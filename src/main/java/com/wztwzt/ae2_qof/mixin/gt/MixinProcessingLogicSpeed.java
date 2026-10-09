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

        // 3.25.0：原先这里的"跨配方合并接管"（threads > 1 时 cir.setReturnValue(crossRecipeProcess(uh))）
        // 已被**线程引擎**取代（MixinMTEMultiBlockBase + hatch/thread/Ae2qolThreadEngine）。
        // 旧实现把多个配方**合并成一个结果**、并绕开了 GT 的 applyRecipe（扣料/产出/保护都在里面），
        // 既不符合"每线程各自独立计时/扣料/产物"的语义，也不受 GT 校验保护 ⇒ 已停用。
        // 本混入现在只负责把维护仓的"并行数 / 速度"喂给 GT 自己的计算（这两项语义不变）。
    }

    /** P1 诊断的"只报一次"标记。 */
    private boolean ae2qol$parallelLogged = false;

    @Shadow
    public abstract int getCurrentParallels();

    /**
     * P1 诊断（3.25.0-fix4）：**只报一次**，把"维护仓设定值"与"本次实际采用值"并排打出来。
     * <p>为什么需要：用户实测"并行 1000 改 10，产出/耗时完全不变"，看起来像设定被忽略；
     * 但启动日志证明我们的 {@code process() HEAD} 注入**确实被应用**（`@Inject::ae2qol$hatchControl`），
     * 所以需要这一行来区分两种情况：
     * <ul>
     * <li>设定值 = 实际值 ⇒ 设定生效，问题在别处（如批处理 ×64 的显示口径、或输入快照被多条线程共享）；</li>
     * <li>设定值 ≠ 实际值 ⇒ 设定被机器自身的并行/批处理覆盖，需要按机器适配。</li>
     * </ul>
     */
    /**
     * 3.28.0（2026-10-08 用户决定）：**删除"并行设定诊断"噪音日志**。
     *
     * <p>它当初只是一次性问诊探针（用来让用户把日志发回确认并行设定是否生效），结论已确认
     * "设定一直生效、设定=1 时刻意不覆盖机器自身并行"并记入文档；此后它只剩噪音：
     * 每台机器首次真正运行时都要在**服务端**打一行（用户实测一次十几行）。
     *
     * <p>**并行逻辑本身一字未动**（{@code getEffectiveParallel} 与 {@code MixinParallel} 系列照旧）。
     * 需要复活时把下面这段注入恢复即可（保留原注释与实现作为参考）。
     */
    // @Inject(method = "process", at = @At("RETURN"))
    // private void ae2qol$logParallelOnce(CallbackInfoReturnable<CheckRecipeResult> cir) {
    //     if (ae2qol$parallelLogged) return;
    //     if (!(machine instanceof MTEMultiBlockBase multi)) return;
    //     AE2MaintenanceHatchUniversal uh = findUniversalHatch(multi);
    //     if (uh == null) return;
    //     int actual = getCurrentParallels();
    //     // 3.25.0-fix17（减噪）：只在真的跑起来时才记这一行（actual == 0 视为"这次没跑"）。
    //     if (actual <= 0) return;
    //     ae2qol$parallelLogged = true;
    //     com.wztwzt.ae2_qof.MyMod.LOG.info(
    //         "[AE2QoL] 并行设定诊断：{} @ {} 维护仓设定={} 实测本次并行={}（两者不符即为被机器自身并行/批处理覆盖，请把这行发我）",
    //         multi.getMetaName(), ae2qol$posText(multi), uh.getEffectiveParallel(), actual);
    // }

    private static String ae2qol$posText(MTEMultiBlockBase multi) {
        if (multi.getBaseMetaTileEntity() == null) return "?";
        return multi.getBaseMetaTileEntity()
            .getXCoord() + ","
            + multi.getBaseMetaTileEntity()
                .getYCoord()
            + ","
            + multi.getBaseMetaTileEntity()
                .getZCoord();
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
     *
     * <p><b>3.25.0 起已停用（dead code，待下一轮清理）</b>：调用点已改为线程引擎
     * （{@code MixinMTEMultiBlockBase} + {@code hatch/thread/Ae2qolThreadEngine}）。
     * 保留本方法只为对照排查，**不会被调用**。
     */
    @Deprecated
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
                // P1-020：duration 先做溢出判断再转 int；乘法前用除法判界避免 long 溢出。
                long perRecipeDurationLong = calc.getDuration();
                if (perRecipeDurationLong <= 0) continue;
                if (perRecipeDurationLong > Integer.MAX_VALUE) return CheckRecipeResultRegistry.DURATION_OVERFLOW;
                int perRecipeDuration = (int) perRecipeDurationLong;
                if (perRecipeEUt > 0 && perRecipeEUt > Long.MAX_VALUE / perRecipeDurationLong) {
                    return CheckRecipeResultRegistry.POWER_OVERFLOW;
                }

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

                long recipeEu = perRecipeEUt * perRecipeDurationLong;
                if (totalEu > Long.MAX_VALUE - recipeEu) {
                    return CheckRecipeResultRegistry.POWER_OVERFLOW;
                }
                totalEu += recipeEu;
                totalParallels += helper.getCurrentParallel();
                remain -= helper.getCurrentParallel();
                matchedRecipes++;
                if (matchedRecipes >= maxRecipes) break;
            }

            // 5. 结果
            if (matchedRecipes == 0) return CheckRecipeResultRegistry.NO_RECIPE;
            // P1-020：电压×电流同样先判界，避免 int/long 溢出后算出错误功率。
            if (availableVoltage > 0 && availableAmperage > 0
                && availableVoltage > Long.MAX_VALUE / availableAmperage) {
                return CheckRecipeResultRegistry.POWER_OVERFLOW;
            }
            long maxPower = availableVoltage * availableAmperage;
            if (maxPower <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
            double durationExact = Math.ceil((double) totalEu / maxPower);
            if (durationExact >= Integer.MAX_VALUE) return CheckRecipeResultRegistry.DURATION_OVERFLOW;
            int duration = (int) Math.max(durationExact, 20);
            long eut = (long) Math.ceil((double) totalEu / duration);
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
                // P1-020：int 上限饱和，避免输出合并溢出成负数。
                long sum = (long) existing.stackSize + stack.stackSize;
                existing.stackSize = (int) Math.min(Integer.MAX_VALUE, sum);
                return;
            }
        }
        list.add(stack.copy());
    }

    private static void addFluidMerged(List<FluidStack> list, FluidStack stack) {
        for (FluidStack existing : list) {
            if (existing.getFluid() == stack.getFluid()
                && FluidStack.areFluidStackTagsEqual(existing, stack)) {
                long sum = (long) existing.amount + stack.amount;
                existing.amount = (int) Math.min(Integer.MAX_VALUE, sum);
                return;
            }
        }
        list.add(stack.copy());
    }
}
