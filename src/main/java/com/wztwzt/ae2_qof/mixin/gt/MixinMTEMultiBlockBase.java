package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.hatch.thread.Ae2qolThreadEngine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/**
 * 两件事：
 * <ol>
 * <li>原有功能：{@link MTEMultiBlockBase#shouldCheckMaintenance()} 恒 false（万能维护仓）。</li>
 * <li>3.25.0 线程引擎：注入 {@code doCheckRecipe()} 与 {@code incrementProgressTime()}，
 * 让装了我们的万能维护仓且线程 &gt; 1 的机器跑 N 条**各自独立进度**的线程。</li>
 * </ol>
 *
 * <h2>为什么拦截这两个点（证据）</h2>
 * <ul>
 * <li>{@code MTEMultiBlockBase.checkProcessing()}（源码 L1094-1115）与 TST 的机器
 * （如 {@code TST_HephaestusAtelier:512}、{@code GT_TileEntity_IndustrialMagicMatrix:559}）
 * **都调用 {@code doCheckRecipe()}** 来"把配方算进 {@code processingLogic}" ⇒ 这是各家族共享的计算入口；
 * GT 原生实现只跑**一个**配方（遍历双输入仓，第一个成功即 return）。</li>
 * <li>进度与产物落地在 {@code runMachine()} → {@code incrementProgressTime()}（每 tick 一次）
 * 与 {@code addItemOutputs/addFluidOutputs}（完成时） ⇒ 这是共享的推进入口。</li>
 * </ul>
 *
 * <h2>外壳（envelope）约定</h2>
 * 机器自身只有一套进度/耗电/产物字段，所以本注入把机器那套当成"外壳"：
 * 时长 = 最长线程的剩余、耗电 = 活跃线程求和、**产物留空**；每条线程的产物在它自己完成时
 * 用 GT 自己的 {@code addItemOutputs/addFluidOutputs} 落地（绝不自行发明产物，避免刷物品）。
 */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MixinMTEMultiBlockBase {

    // ===================== 原有：万能维护仓 =====================

    /**
     * @author wztwzt
     * @return 始终返回 false，禁用所有维护检查
     * @reason 万能维护仓功能：全局禁用维护系统
     */
    @Overwrite(remap = false)
    public boolean shouldCheckMaintenance() {
        return false;
    }

    // ===================== 线程引擎所需的 GT 成员 =====================

    @Shadow
    @Final
    protected ProcessingLogic processingLogic;

    @Shadow
    public int mMaxProgresstime;

    @Shadow
    public int mProgresstime;

    @Shadow
    public int mEUt;

    /** GT 原始的"算一个配方"实现（不经过本注入的接管分支，见 {@code computing} 标志）。 */
    @Invoker("doCheckRecipe")
    protected abstract CheckRecipeResult ae2qol$gtDoCheckRecipe();

    /** GT 自己的产物落地（物品）。 */
    @Invoker("addItemOutputs")
    protected abstract boolean ae2qol$addItemOutputs(ItemStack[] items);

    /** GT 自己的产物落地（流体）。 */
    @Invoker("addFluidOutputs")
    protected abstract boolean ae2qol$addFluidOutputs(FluidStack[] fluids);

    /** 上一次因缺电降级的条数：只在变化时记日志（避免每 tick 刷屏，但绝不静默）。 */
    private int ae2qol$lastDropped = 0;

    // ===================== 计算入口 =====================

    /**
     * 接管 {@code doCheckRecipe()}：一次性把 N 条线程都算出来（每条各自消耗自己的输入份额），
     * 然后给机器一个"外壳成功"结果。没启用线程、或正在引擎内部回调时，放行给 GT 原生实现。
     */
    @Inject(method = "doCheckRecipe", at = @At("HEAD"), cancellable = true)
    private void ae2qol$computeThreads(CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        Ae2qolThreadEngine engine = Ae2qolThreadEngine.get(self);
        if (!engine.isEnabled() || engine.isComputing()) return; // 未启用 / 引擎内部调用 ⇒ 走 GT 原逻辑

        cir.setReturnValue(ae2qol$runThreadCompute(engine, false));
    }

    /**
     * @param reuseRunning true 表示"已经在跑的线程保持不动"（tick 中给新空出的线程找配方时用）
     */
    private CheckRecipeResult ae2qol$runThreadCompute(Ae2qolThreadEngine engine, boolean tickPhase) {
        engine.beginComputing();
        try {
            CheckRecipeResult lastFail = CheckRecipeResultRegistry.NO_RECIPE;
            int started = 0;
            int count = engine.threadCount();
            for (int i = 0; i < count; i++) {
                Ae2qolThreadEngine.Slot slot = engine.slot(i);
                if (slot.isRunning() || slot.state == Ae2qolThreadEngine.ST_POWER) {
                    // 已在跑（或已被电力策略降级，等它自己恢复）⇒ 本轮不再动它
                    if (slot.isRunning()) started++;
                    continue;
                }
                CheckRecipeResult result = ae2qol$gtDoCheckRecipe();
                if (result != null && result.wasSuccessful()) {
                    ae2qol$fillSlotFromLogic(slot);
                    started++;
                } else {
                    boolean noRecipe = (result == null || result == CheckRecipeResultRegistry.NO_RECIPE);
                    slot.clearRun();
                    slot.state = noRecipe ? Ae2qolThreadEngine.ST_STARVED : Ae2qolThreadEngine.ST_OUTPUT_FULL;
                    if (!noRecipe) lastFail = result;
                }
            }
            if (started == 0) {
                // 一条都没起来：交回机器的正常失败处理（NO_RECIPE / 输出空间不足 …）
                return lastFail;
            }
            ae2qol$writeEnvelope(engine);
            return CheckRecipeResultRegistry.SUCCESSFUL;
        } finally {
            engine.endComputing();
        }
    }

    /** 从 {@code processingLogic} 读出"这条线程本次"的结果（产物/时长/并行/耗电）。 */
    private void ae2qol$fillSlotFromLogic(Ae2qolThreadEngine.Slot slot) {
        int duration = Math.max(1, processingLogic.getDuration());
        slot.state = Ae2qolThreadEngine.ST_RUNNING;
        slot.total = duration;
        slot.remain = duration;
        slot.parallel = Math.max(1, processingLogic.getCurrentParallels());
        // setEnergyUsage 用负数写 mEUt（见 GT 源码 L1318），这里统一存正数便于求和
        slot.eutPerTick = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, -processingLogic.getCalculatedEut()));
        slot.items = processingLogic.getOutputItems();
        slot.fluids = processingLogic.getOutputFluids();
        slot.icon = ae2qol$firstItem(slot.items);
    }

    /** 外壳：产物留空（产物由每条线程完成时自行落地）、时长=最长线程剩余、耗电=活跃线程求和。 */
    private void ae2qol$writeEnvelope(Ae2qolThreadEngine engine) {
        processingLogic.overwriteOutputItems();
        processingLogic.overwriteOutputFluids();
        processingLogic.overwriteCalculatedDuration(Math.max(1, engine.maxRemain()));
        processingLogic.overwriteCalculatedEut(engine.totalEutPerTick());
    }

    // ===================== 推进入口 =====================

    /**
     * 每 tick 推进各线程；某条线程完成时用 **GT 自己的** {@code addItemOutputs/addFluidOutputs} 落地它的产物，
     * 然后立刻给它找下一个配方（这就是"错峰"）。电力不够时按用户口径**自动降线程**而不是整台停机。
     */
    @Inject(method = "incrementProgressTime", at = @At("HEAD"))
    private void ae2qol$tickThreads(CallbackInfo ci) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        Ae2qolThreadEngine engine = Ae2qolThreadEngine.peek(self);
        if (engine == null) return;
        if (!engine.isEnabled()) {
            // 开关关闭（维护仓被拆 / 线程改回 1）：清空状态，回到 GT 原生行为
            engine.clearAll();
            return;
        }
        engine.trimToThreadCount();

        IGregTechTileEntity base = self.getBaseMetaTileEntity();
        long stored = 0;
        if (base != null) {
            stored = Math.max(0L, base.getStoredEU());
        }
        int dropped = engine.applyPowerLimit(stored);
        if (dropped != ae2qol$lastDropped) {
            if (dropped > 0) {
                MyMod.LOG.info(
                    "[AE2QoL] 线程引擎：{} @ {} 电力不足，自动降线程 {} 条（活跃 {} 条，需求 {} EU/t，可用 {} EU）",
                    self.getMetaName(),
                    ae2qol$posText(base),
                    dropped,
                    engine.activeCount(),
                    engine.totalEutPerTick(),
                    stored);
            } else {
                MyMod.LOG.info(
                    "[AE2QoL] 线程引擎：{} @ {} 电力恢复，降级线程已恢复（活跃 {} 条）",
                    self.getMetaName(),
                    ae2qol$posText(base),
                    engine.activeCount());
            }
            ae2qol$lastDropped = dropped;
        }

        engine.beginComputing();
        try {
            int count = engine.threadCount();
            for (int i = 0; i < count; i++) {
                Ae2qolThreadEngine.Slot slot = engine.slot(i);
                if (!slot.isRunning()) continue;
                slot.remain--;
                if (slot.remain > 0) continue;

                // 线程完成：先落地产物（用 GT 自己的 API，输出空间在算配方时已由 GT 校验过）
                if (slot.items != null && slot.items.length > 0) {
                    if (!ae2qol$addItemOutputs(slot.items)) {
                        MyMod.LOG.warn(
                            "[AE2QoL] 线程引擎：{} @ {} 第 {} 条线程的物品产物未能全部落地（输出空间不足）",
                            self.getMetaName(),
                            ae2qol$posText(base),
                            i + 1);
                    }
                }
                if (slot.fluids != null && slot.fluids.length > 0) {
                    ae2qol$addFluidOutputs(slot.fluids);
                }

                // 立刻补下一个配方（错峰的关键）
                slot.clearRun();
                CheckRecipeResult result = ae2qol$gtDoCheckRecipe();
                if (result != null && result.wasSuccessful()) {
                    ae2qol$fillSlotFromLogic(slot);
                } else {
                    boolean noRecipe = (result == null || result == CheckRecipeResultRegistry.NO_RECIPE);
                    slot.clearRun();
                    slot.state = noRecipe ? Ae2qolThreadEngine.ST_STARVED : Ae2qolThreadEngine.ST_OUTPUT_FULL;
                }
            }
        } finally {
            engine.endComputing();
        }

        // 外壳维护：只要还有线程在跑，就把机器的"总时长"顶到最长线程的剩余，保证机器不会提前结算
        int maxRemain = engine.maxRemain();
        if (maxRemain > 0) {
            mMaxProgresstime = mProgresstime + maxRemain;
        }
        long need = engine.totalEutPerTick();
        mEUt = (int) -Math.min(Integer.MAX_VALUE, need);
    }

    // ===================== 小工具 =====================

    private static ItemStack ae2qol$firstItem(ItemStack[] items) {
        if (items == null) return null;
        for (ItemStack item : items) {
            if (item != null && item.stackSize > 0) return item;
        }
        return null;
    }

    private static String ae2qol$posText(IGregTechTileEntity base) {
        if (base == null) return "?";
        return base.getXCoord() + "," + base.getYCoord() + "," + base.getZCoord();
    }
}
