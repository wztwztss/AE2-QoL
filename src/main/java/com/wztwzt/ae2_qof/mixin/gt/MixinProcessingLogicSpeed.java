package com.wztwzt.ae2_qof.mixin.gt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

import com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal;

@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class MixinProcessingLogicSpeed {

    @Shadow
    protected IVoidable machine;

    @Shadow
    protected double speedBoost;

    @Shadow
    protected int maxParallel;

    @Shadow
    protected Supplier<Integer> maxParallelSupplier;

    @Shadow
    protected Supplier<Double> speedBoostSupplier;

    @Shadow
    protected long availableVoltage;

    @Shadow
    protected long availableAmperage;

    @Shadow
    protected ItemStack[] inputItems;

    @Shadow
    protected FluidStack[] inputFluids;

    @Shadow
    protected int calculatedParallels;

    @Shadow
    protected abstract ItemStack[] prepareCatalyst(ItemStack[] input);

    @Shadow
    protected abstract RecipeMap<?> getCurrentRecipeMap();

    @Shadow
    protected abstract java.util.stream.Stream<GTRecipe> findRecipeMatches(RecipeMap<?> recipeMap);

    @Shadow
    protected abstract ParallelHelper createParallelHelper(GTRecipe recipe);

    @Shadow
    protected abstract OverclockCalculator createOverclockCalculator(GTRecipe recipe);

    @Shadow
    public abstract ProcessingLogic overwriteOutputItems(ItemStack... items);

    @Shadow
    public abstract ProcessingLogic overwriteOutputFluids(FluidStack... fluids);

    @Shadow
    public abstract ProcessingLogic overwriteCalculatedEut(long eut);

    @Shadow
    public abstract ProcessingLogic overwriteCalculatedDuration(int duration);

    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void ae2qol$hatchControl(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!(machine instanceof MTEMultiBlockBase)) return;
        AE2MaintenanceHatchUniversal uh = findUniversalHatch((MTEMultiBlockBase) machine);
        if (uh == null) return;

        // supplier 接管（HEAD 在 supplier 求值之前，本次 process 立即生效）
        int parallel = uh.getEffectiveParallel();
        if (parallel > 1) {
            maxParallelSupplier = () -> parallel;
        }
        double speed = uh.getEffectiveSpeedBoost();
        if (speed != 1.0) {
            speedBoostSupplier = () -> speed;
        }

        // 跨配方分支（仅线程 > 1 且处于普通路径时接管）
        if (uh.getEffectiveThreads() > 1) {
            cir.setReturnValue(ae2qol$crossRecipeProcess(uh));
        }
    }

    private AE2MaintenanceHatchUniversal findUniversalHatch(MTEMultiBlockBase multi) {
        for (MTEHatchMaintenance hatch : multi.mMaintenanceHatches) {
            if (hatch instanceof AE2MaintenanceHatchUniversal) {
                return (AE2MaintenanceHatchUniversal) hatch;
            }
        }
        return null;
    }

    /**
     * 跨配方并行：同一 tick 内处理多个不同配方。
     * 输入共享 + 额度递减（ParallelHelper.build 原地扣输入）。
     * 机器端只消费 ProcessingLogic 输出字段，无需合成 GTRecipe。
     */
    private CheckRecipeResult ae2qol$crossRecipeProcess(AE2MaintenanceHatchUniversal uh) {
        try {
            // 1. 准备输入——与原版 process() 一致：prepareCatalyst 结果写回 this.inputItems
            this.inputItems = prepareCatalyst(this.inputItems);
            if (this.inputItems == null) this.inputItems = new ItemStack[0];
            if (this.inputFluids == null) this.inputFluids = new FluidStack[0];

            // 2. 配方池
            RecipeMap<?> map = getCurrentRecipeMap();
            if (map == null) return CheckRecipeResultRegistry.NO_RECIPE;

            // 3. 额度：总并行 = 并行 × 线程
            long remain = (long) uh.getEffectiveParallel() * uh.getEffectiveThreads();
            int maxRecipes = uh.getEffectiveThreads();

            // 4. 遍历多配方
            List<ItemStack> mergedItems = new ArrayList<>();
            List<FluidStack> mergedFluids = new ArrayList<>();
            long totalEu = 0;
            int totalParallels = 0;
            int matchedRecipes = 0;

            GTRecipe[] recipes = findRecipeMatches(map).limit(maxRecipes).toArray(GTRecipe[]::new);
            for (GTRecipe recipe : recipes) {
                if (remain <= 0) break;

                int perParallel = (int) Math.min(uh.getEffectiveParallel(), remain);

                ParallelHelper helper = createParallelHelper(recipe);
                // 每个 recipe 用输入副本：helper.build() 会原地消耗 itemInputs/fluidInputs，
                // 不复制会导致第一个 recipe 消耗后，后续 recipe 用已耗尽的输入 → 全部失败
                helper.setItemInputs(java.util.Arrays.copyOf(this.inputItems, this.inputItems.length));
                helper.setFluidInputs(java.util.Arrays.copyOf(this.inputFluids, this.inputFluids.length));
                helper.setMaxParallel(perParallel);
                OverclockCalculator calc = createOverclockCalculator(recipe);
                helper.setCalculator(calc);
                helper.build();

                if (!helper.getResult().wasSuccessful() || helper.getCurrentParallel() <= 0) continue;

                long perRecipeEUt = calc.getConsumption();
                int perRecipeDuration = calc.getDuration();

                // 合并输出（按机器输出槽位上限截断）
                // 注意：getItemOutputs()/getFluidOutputs() 可能返回 null（纯流体/纯物品配方），必须判空
                int itemLimit = machine.getItemOutputLimit();
                int fluidLimit = machine.getFluidOutputLimit();
                ItemStack[] itemOutputs = helper.getItemOutputs();
                if (itemOutputs != null) {
                    for (ItemStack out : itemOutputs) {
                        if (out == null) continue;
                        if (mergedItems.size() >= itemLimit) break;
                        addItemMerged(mergedItems, out);
                    }
                }
                FluidStack[] fluidOutputs = helper.getFluidOutputs();
                if (fluidOutputs != null) {
                    for (FluidStack out : fluidOutputs) {
                        if (out == null) continue;
                        if (mergedFluids.size() >= fluidLimit) break;
                        addFluidMerged(mergedFluids, out);
                    }
                }

                totalEu += perRecipeEUt * perRecipeDuration;
                totalParallels += helper.getCurrentParallel();
                remain -= helper.getCurrentParallel();
                matchedRecipes++;
                if (matchedRecipes >= maxRecipes) break;
            }

            // 5. 结果
            if (matchedRecipes == 0) return CheckRecipeResultRegistry.NO_RECIPE;
            long maxPower = availableVoltage * availableAmperage;
            if (maxPower <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
            int duration = (int) Math.max(Math.ceil((double) totalEu / maxPower), 20);
            long eut = (long) Math.ceil((double) totalEu / duration);
            if (duration >= Integer.MAX_VALUE) return CheckRecipeResultRegistry.DURATION_OVERFLOW;
            if (eut > Integer.MAX_VALUE) return CheckRecipeResultRegistry.POWER_OVERFLOW;

            // 6. 写字段
            overwriteOutputItems(mergedItems.toArray(new ItemStack[0]));
            overwriteOutputFluids(mergedFluids.toArray(new FluidStack[0]));
            overwriteCalculatedEut(eut);
            overwriteCalculatedDuration(duration);
            calculatedParallels = totalParallels;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        } catch (Throwable t) {
            // 跨配方分支异常时回退原流程，并打印异常便于定位
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2-QoL] crossRecipeProcess failed, fallback to NO_RECIPE", t);
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
    }

    private static void addItemMerged(List<ItemStack> list, ItemStack stack) {
        for (ItemStack existing : list) {
            if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                existing.stackSize += stack.stackSize;
                return;
            }
        }
        list.add(stack.copy());
    }

    private static void addFluidMerged(List<FluidStack> list, FluidStack stack) {
        for (FluidStack existing : list) {
            if (existing.getFluid() == stack.getFluid()
                && FluidStack.areFluidStackTagsEqual(existing, stack)) {
                existing.amount += stack.amount;
                return;
            }
        }
        list.add(stack.copy());
    }
}
