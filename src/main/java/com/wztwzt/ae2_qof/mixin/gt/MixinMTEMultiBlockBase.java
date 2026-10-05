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
import com.wztwzt.ae2_qof.hatch.thread.ThreadStatusBroadcaster;

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

    /** G1：机器"当前操作产物"字段 —— 我们只用来**显示**（结算前会主动置空，避免重复落地）。 */
    @Shadow
    public ItemStack[] mOutputItems;

    @Shadow
    public FluidStack[] mOutputFluids;

    /** GT 原始的"算一个配方"实现（不经过本注入的接管分支，见 {@code computing} 标志）。 */
    @Invoker("doCheckRecipe")
    protected abstract CheckRecipeResult ae2qol$gtDoCheckRecipe();

    /** GT 自己的产物落地（物品）。 */
    @Invoker("addItemOutputs")
    protected abstract boolean ae2qol$addItemOutputs(ItemStack[] items);

    /** GT 自己的产物落地（流体）。 */
    @Invoker("addFluidOutputs")
    protected abstract boolean ae2qol$addFluidOutputs(FluidStack[] fluids);

    /**
     * GT 的收尾处理（G2）：GT 在 {@code checkProcessing()} 里的顺序是
     * {@code doCheckRecipe() → postCheckRecipe(result, processingLogic) → 才取 getCalculatedEut() 写 mEUt}。
     * 我原来在读 EU 之前漏了这一步，于是每条线程的 `getCalculatedEut()` 都读到 0
     * ⇒ 外壳 EU=0 ⇒ 家族把 mEUt 写成 0 ⇒ WAILA/主界面的耗电整段消失（用户实测）。
     */
    @Invoker("postCheckRecipe")
    protected abstract CheckRecipeResult ae2qol$gtPostCheckRecipe(CheckRecipeResult result, ProcessingLogic logic);

    /** 跑一次 GT 的「算配方 + 收尾」，返回已定稿的结果（EU/时长/outputs 才是最终值）。 */
    private CheckRecipeResult ae2qol$checkOne() {
        CheckRecipeResult result = ae2qol$gtDoCheckRecipe();
        if (result != null && result.wasSuccessful()) {
            result = ae2qol$gtPostCheckRecipe(result, processingLogic);
        }
        return result;
    }

    /** 上一次因缺电降级的条数：只在变化时记日志（避免每 tick 刷屏，但绝不静默）。 */
    private int ae2qol$lastDropped = 0;

    /**
     * 上一次观察到的机器进度（{@code mProgresstime}）。
     * <p>3.25.1：线程推进改为**跟随这个字段的增量**，而不是假定 1/tick —— 因为 NH-Utilities 等加速机制
     * 是「直接改写 {@code multiBlockBase.mProgresstime} 并跳过 updateEntity」的
     * （证据：NH-Utilities `BaseMetaTileEntityAcceleration_Mixin.tickAcceleration`：
     * {@code multiBlockBase.mProgresstime = Math.min(maxProgress, currentProgress + rate); return true;}）。
     */
    private int ae2qol$lastProgress = 0;

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
            // P0（3.25.0-fix2）：**每次只允许启动一条新线程**。
            // 根因：同一 tick 内连续调用 N 次 GT 的 doCheckRecipe() 时，每次都读到**同一份尚未被扣减的输入快照**
            // （Programmable-Hatches 那类"限制输入仓/虚拟供给"的扣料不会在同 tick 内对下一次计算可见），
            // 于是 N 次计算都按同一份输入算出 N 份产出 ⇒ 用户实测"16 线程输出 ≈16×100 倍而输入只扣 100 倍"。
            // 现在改为"排队起步"：本次只起一条，其余空闲线程由后续 tick 逐条起（16 条 ≈ 16 tick ≈ 0.8 秒），
            // 每次计算都发生在上一线程扣料生效之后。这同时也更符合"错峰"语义。
            int startsLeft = 1;
            int count = engine.threadCount();
            for (int i = 0; i < count; i++) {
                Ae2qolThreadEngine.Slot slot = engine.slot(i);
                if (slot.isRunning() || slot.state == Ae2qolThreadEngine.ST_POWER) {
                    // 已在跑（或已被电力策略降级，等它自己恢复）⇒ 本轮不再动它
                    if (slot.isRunning()) started++;
                    continue;
                }
                if (startsLeft <= 0) continue; // 预算用完：保持空闲，等后续 tick
                CheckRecipeResult result = ae2qol$checkOne();
                if (result != null && result.wasSuccessful()) {
                    ae2qol$fillSlotFromLogic(slot);
                    started++;
                    startsLeft--;
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
            // 3.25.0 提交 2/2：顺带把线程状态降频推给客户端（WAILA 用；内部每 10 tick 一次）
            ThreadStatusBroadcaster.maybeBroadcast((MTEMultiBlockBase) (Object) this, engine);
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
        // 注意：产物**必须留空**（G1 另行把"待落地产物并集"写进 mOutputItems 仅供显示，
        // 并在结算前置空）—— 否则 GT 结算时会把这些产物再落地一次 = 刷物品。
        processingLogic.overwriteOutputItems();
        processingLogic.overwriteOutputFluids();
        processingLogic.overwriteCalculatedDuration(Math.max(1, engine.maxRemain()));
        // G2：只有拿到正值才写 EU；拿不到就保留 processingLogic 里**已经定稿的那个值**
        // （家族随后会用它写 mEUt）⇒ 绝不再出现"耗电 0"的假象。
        long totalEut = engine.totalEutPerTick();
        if (totalEut > 0) {
            processingLogic.overwriteCalculatedEut(totalEut);
        }
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

        // F1：算出本 tick 的**有效进度增量**（正常 1；被加速器直接跳字段时就是它跳的量）。
        // 这样所有"跳进度"的加速机制都自动对线程生效，不需要逐个模组适配。
        int delta = mProgresstime - ae2qol$lastProgress;
        if (delta < 1) delta = 1;
        ae2qol$lastProgress = mProgresstime;

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
            boolean startedThisTick = false;
            for (int i = 0; i < count; i++) {
                Ae2qolThreadEngine.Slot slot = engine.slot(i);
                if (!slot.isRunning()) continue;
                slot.remain -= delta;
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

                // 立刻补下一个配方（错峰的关键）。
                // P0：**每 tick 只允许起一条新线程** —— 同 tick 内连续两次 doCheckRecipe() 会读到
                // 同一份"尚未扣减"的输入快照（虚拟/限制输入仓的扣料同 tick 内不可见）⇒ N 份产出只扣 1 份料。
                slot.clearRun();
                if (startedThisTick) {
                    continue; // 预算用完：本槽保持空闲，等后续 tick 再起
                }
                CheckRecipeResult result = ae2qol$checkOne();
                if (result != null && result.wasSuccessful()) {
                    ae2qol$fillSlotFromLogic(slot);
                    startedThisTick = true;
                } else {
                    boolean noRecipe = (result == null || result == CheckRecipeResultRegistry.NO_RECIPE);
                    slot.clearRun();
                    slot.state = noRecipe ? Ae2qolThreadEngine.ST_STARVED : Ae2qolThreadEngine.ST_OUTPUT_FULL;
                }
            }

            // P0（续）：尚未起步的空闲槽（首次开机、或上一条完成后来不及接续）每 tick 补**一条**，
            // 于是 16 条线程在约 16 tick（0.8 秒）内逐步到位 —— 每条计算都在上一条扣料生效之后。
            if (!startedThisTick) {
                for (int i = 0; i < count && !startedThisTick; i++) {
                    Ae2qolThreadEngine.Slot slot = engine.slot(i);
                    if (slot.isRunning() || slot.state == Ae2qolThreadEngine.ST_POWER) continue;
                    CheckRecipeResult result = ae2qol$checkOne();
                    if (result != null && result.wasSuccessful()) {
                        ae2qol$fillSlotFromLogic(slot);
                        startedThisTick = true;
                    } else {
                        boolean noRecipe = (result == null || result == CheckRecipeResultRegistry.NO_RECIPE);
                        slot.clearRun();
                        slot.state = noRecipe ? Ae2qolThreadEngine.ST_STARVED
                            : Ae2qolThreadEngine.ST_OUTPUT_FULL;
                        break; // 这一条起不来，本 tick 不再试（避免把同一份输入反复试探）
                    }
                }
            }
        } finally {
            engine.endComputing();
        }

        // G1（3.25.0-fix3）：把"活跃线程待落地的产物并集"写回机器字段，**仅供 GT 主界面/WAILA 显示**。
        // 关键：如果**本 tick 将要结算**（进度即将到达上限），必须先把它们置空 ——
        // 否则 GT 在结算时会再 addItemOutputs 一次，就变成重复产出（刷物品）。
        if (mProgresstime + 1 >= mMaxProgresstime) {
            mOutputItems = null;
            mOutputFluids = null;
        } else {
            ae2qol$fillDisplayOutputs(engine);
        }

        // 外壳维护：只要还有线程在跑，就把机器的"总时长"顶到最长线程的剩余，保证机器不会提前结算
        // F2：只许**向外扩展**总时长，绝不缩短 —— 否则会把加速器刚跳上去的进度又压回去（这是加速失效的直接原因）
        int maxRemain = engine.maxRemain();
        if (maxRemain > 0) {
            int want = mProgresstime + maxRemain;
            if (want > mMaxProgresstime) {
                mMaxProgresstime = want;
            }
        }
        // F4：只有拿到可靠的正数才改写 mEUt；拿不到就**保留家族/GT 自己算好的值**，
        // 宁可少算自己的份额，也不能出现"耗电 0"的免电路径。
        long need = engine.totalEutPerTick();
        if (need > 0) {
            mEUt = (int) -Math.min(Integer.MAX_VALUE, need);
        }
        // 3.25.0 提交 2/2：每 tick 推进后也让广播器有机会发一次（内部自行节流）
        ThreadStatusBroadcaster.maybeBroadcast(self, engine);
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

    /**
     * G1：把活跃线程的待落地产物合并成"当前操作产物"，只用于显示（GT 主界面 / WAILA）。
     * 不参与结算 —— 结算路径上（见 tick 里的"将要结算"分支）会先被置空。
     */
    private void ae2qol$fillDisplayOutputs(Ae2qolThreadEngine engine) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        java.util.List<FluidStack> fluids = new java.util.ArrayList<>();
        int count = engine.threadCount();
        for (int i = 0; i < count; i++) {
            Ae2qolThreadEngine.Slot slot = engine.slot(i);
            if (!slot.isRunning()) continue;
            if (slot.items != null) {
                for (ItemStack stack : slot.items) {
                    if (stack != null) ae2qol$mergeItem(items, stack);
                }
            }
            if (slot.fluids != null) {
                for (FluidStack stack : slot.fluids) {
                    if (stack != null) ae2qol$mergeFluid(fluids, stack);
                }
            }
        }
        mOutputItems = items.isEmpty() ? null : items.toArray(new ItemStack[0]);
        mOutputFluids = fluids.isEmpty() ? null : fluids.toArray(new FluidStack[0]);
    }

    private static void ae2qol$mergeItem(java.util.List<ItemStack> list, ItemStack stack) {
        for (ItemStack existing : list) {
            if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                existing.stackSize = (int) Math.min(Integer.MAX_VALUE, (long) existing.stackSize + stack.stackSize);
                return;
            }
        }
        list.add(stack.copy());
    }

    private static void ae2qol$mergeFluid(java.util.List<FluidStack> list, FluidStack stack) {
        for (FluidStack existing : list) {
            if (existing.getFluid() == stack.getFluid() && FluidStack.areFluidStackTagsEqual(existing, stack)) {
                existing.amount = (int) Math.min(Integer.MAX_VALUE, (long) existing.amount + stack.amount);
                return;
            }
        }
        list.add(stack.copy());
    }
}
