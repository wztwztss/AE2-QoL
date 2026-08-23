package com.wztwzt.ae2_qof.mixin.ae;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;
import com.wztwzt.ae2_qof.network.CraftingCompletePacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.features.INetworkEncodable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingMedium.BlockingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.CraftUpdateListener;
import appeng.container.ContainerNull;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.helpers.DualityInterface;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import appeng.tile.misc.TileSecurity;
import appeng.util.Platform;
import appeng.util.ScheduledReason;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.FMLCommonHandler;
import reobf.proghatches.gt.metatileentity.util.IMultiplePatternPushable;

/**
 * 合成完成通知 + 智能倍增（Smart Doubling）。
 * <p>
 * executeCrafting 通过 {@code @Inject(HEAD, cancellable)} 仅在存在智能倍增任务时接管整个 tick：
 * 对实现了 {@link ISmartDoublingMedium} 且已启用倍增的介质，一次性提取 N 轮原料并推送，
 * 随后按 N 轮记账（产出 / 能耗 / 诊断）。其余分支保持与原版逐行一致的语义。
 * 采用 HEAD 注入 + cancel 而非 @Overwrite，保留原方法字节码结构，避免与其它模组
 * （如 ProgrammableHatches 的 MixinInstantComplete）对同一方法的注入冲突崩溃。
 */
@Mixin(CraftingCPUCluster.class)
public abstract class MixinCraftingCPUCluster {

    @Shadow
    private boolean suspended;

    @Shadow
    private HashMap<ICraftingPatternDetails, List<ICraftingMedium>> parallelismProvider;

    @Shadow
    private HashMap<ICraftingPatternDetails, ScheduledReason> reasonProvider;

    @Shadow
    private HashSet<ICraftingMedium> knownBusyMediums;

    @Shadow
    private MECraftingInventory inventory;

    @Shadow
    private MachineSource machineSrc;

    @Shadow
    private IItemList<IAEStack<?>> waitingFor;

    @Shadow
    private boolean somethingChanged;

    @Shadow
    private int remainingOperations;

    @Shadow
    private List<CraftUpdateListener> craftUpdateListeners;

    @Shadow
    protected abstract boolean canCraft(ICraftingPatternDetails details, List<IAEStack<?>> condensedInputs);

    @Shadow
    protected abstract List<IAEStack<?>> getExpandedCondensedInputs(ICraftingPatternDetails details,
        CraftingGridCache cache);

    @Shadow
    protected abstract List<IAEStack<?>> getExpandedInputs(ICraftingPatternDetails details, CraftingGridCache cache);

    @Shadow
    protected abstract ArrayList<IAEStack<?>> getExtractItems(IAEStack<?> ingredient,
        ICraftingPatternDetails patternDetails);

    @Shadow
    protected abstract void postChange(IAEStack<?> diff, BaseActionSource src);

    @Shadow
    protected abstract void postCraftingStatusChange(IAEStack<?> aeDiff);

    @Shadow
    protected abstract void returnItems(MEInventoryCrafting ic);

    @Shadow
    public abstract void markDirty();

    @Shadow
    protected abstract World getWorld();

    @Shadow
    protected abstract boolean isCraftingDiagnosticsEnabled();

    @Unique
    private EntityPlayer player;

    @Unique
    private ItemStack output;

    @Unique
    private long networkKey = 0;

    @Inject(method = "submitJob", at = @At("RETURN"), remap = false)
    private void ae2qol$captureSubmitJob(IGrid g, ICraftingJob job, BaseActionSource src,
        ICraftingRequester requestingMachine, CallbackInfoReturnable<ICraftingLink> cir) {
        if (src instanceof PlayerSource ps && cir.getReturnValue() != null && job != null) {
            java.util.Iterator<IGridNode> iterator = g.getMachines(TileSecurity.class)
                .iterator();
            if (iterator.hasNext()) {
                this.networkKey = ((TileSecurity) iterator.next()
                    .getMachine()).getLocatableSerial();
                this.player = ps.player;
                IAEStack<?> out = job.getOutput();
                this.output = out instanceof IAEItemStack item ? item.getItemStack() : null;
            } else {
                setAsNull();
            }
        } else {
            setAsNull();
        }
    }

    @Inject(method = "handleCraftBranchFailure", at = @At("TAIL"), remap = false)
    private void ae2qol$onBranchFailure(CraftBranchFailure e, BaseActionSource src, CallbackInfo ci) {
        setAsNull();
    }

    @Inject(method = "completeJob", at = @At("TAIL"), remap = false)
    private void ae2qol$onJobComplete(CallbackInfo ci) {
        if (this.player == null || this.output == null || this.networkKey == 0) {
            return;
        }
        if (this.player instanceof EntityPlayerMP playerMP && playerMP.playerNetServerHandler == null) {
            // 玩家已断线：放弃通知并清理捕获，避免 NPE 与内存滞留
            setAsNull();
            return;
        }
        for (int i = 0; i < this.player.inventory.mainInventory.length; i++) {
            ItemStack stack = this.player.inventory.mainInventory[i];
            if (isSameNetworkKey(stack)) {
                return;
            }
        }
    }

    @Unique
    private boolean isSameNetworkKey(ItemStack item) {
        if (item != null && item.getItem() instanceof INetworkEncodable encodable) {
            String key = encodable.getEncryptionKey(item);
            if (key != null && key.equals(Long.toString(this.networkKey)) && this.player instanceof EntityPlayerMP) {
                ModNetwork.CHANNEL.sendTo(
                    new CraftingCompletePacket(this.output, this.output.stackSize),
                    (EntityPlayerMP) this.player);
                setAsNull();
                return true;
            }
        }
        return false;
    }

    @Unique
    private void setAsNull() {
        this.player = null;
        this.output = null;
        this.networkKey = 0;
    }

    // ---------------------------------------------------------------- 智能倍增

    @Unique
    private static Field TASK_PROGRESS_VALUE;

    @Unique
    private static Method TASK_PROGRESS_CONSUME_SESSION;

    @Unique
    private static Field FINAL_OUTPUT_FIELD;

    @Unique
    private static Method FINAL_OUTPUT_IS_FAKE_CRAFTING;

    @Unique
    private static Method FINAL_OUTPUT_IS_FINAL_PATTERN;

    @Unique
    private static Method FINAL_OUTPUT_SET_FAKE_CRAFTING;

    @Unique
    private static Method FINAL_OUTPUT_PERFORM_FAKE_CRAFTING;

    @Unique
    private static Field DIAGNOSTICS_FIELD;

    @Unique
    private static Method DIAGNOSTICS_RECORD_EXPECTED_OUTPUT;

    @Unique
    private static Field TASKS_FIELD;

    @Unique
    private static Field WORKABLE_TASKS_FIELD;

    @Unique
    private static Method GET_SERVER_TICK;

    @Unique
    private static void ae2qol$initReflection() {
        try {
            if (TASK_PROGRESS_VALUE == null) {
                final Class<?> tp = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress");
                TASK_PROGRESS_VALUE = tp.getDeclaredField("value");
                TASK_PROGRESS_VALUE.setAccessible(true);
                TASK_PROGRESS_CONSUME_SESSION = tp.getDeclaredMethod("consumeCraftSession");
                TASK_PROGRESS_CONSUME_SESSION.setAccessible(true);
            }
            if (FINAL_OUTPUT_FIELD == null) {
                final Class<?> owner = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster");
                FINAL_OUTPUT_FIELD = owner.getDeclaredField("finalOutput");
                FINAL_OUTPUT_FIELD.setAccessible(true);
                DIAGNOSTICS_FIELD = owner.getDeclaredField("diagnostics");
                DIAGNOSTICS_FIELD.setAccessible(true);
                TASKS_FIELD = owner.getDeclaredField("tasks");
                TASKS_FIELD.setAccessible(true);
                WORKABLE_TASKS_FIELD = owner.getDeclaredField("workableTasks");
                WORKABLE_TASKS_FIELD.setAccessible(true);
                GET_SERVER_TICK = owner.getDeclaredMethod("getServerTick");
                GET_SERVER_TICK.setAccessible(true);

                final Class<?> fo = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$finalOutput");
                FINAL_OUTPUT_IS_FAKE_CRAFTING = fo.getDeclaredMethod("isFakeCrafting");
                FINAL_OUTPUT_IS_FAKE_CRAFTING.setAccessible(true);
                FINAL_OUTPUT_IS_FINAL_PATTERN = fo.getDeclaredMethod("isFinalPattern", ICraftingPatternDetails.class);
                FINAL_OUTPUT_IS_FINAL_PATTERN.setAccessible(true);
                FINAL_OUTPUT_SET_FAKE_CRAFTING = fo.getDeclaredMethod("setFakeCrafting");
                FINAL_OUTPUT_SET_FAKE_CRAFTING.setAccessible(true);
                FINAL_OUTPUT_PERFORM_FAKE_CRAFTING = fo
                    .getDeclaredMethod("performFakeCrafting", ICraftingPatternDetails.class);
                FINAL_OUTPUT_PERFORM_FAKE_CRAFTING.setAccessible(true);

                final Class<?> dc = Class.forName("appeng.me.cluster.implementations.CraftingCpuDiagnostics");
                DIAGNOSTICS_RECORD_EXPECTED_OUTPUT = dc.getDeclaredMethod(
                    "recordExpectedOutput",
                    IAEStack.class,
                    long.class,
                    CraftingDiagnosticSessionId.class);
                DIAGNOSTICS_RECORD_EXPECTED_OUTPUT.setAccessible(true);
            }
        } catch (Throwable t) {
            // 反射失败时安全降级：倍增相关判定回退为单轮，不影响其他逻辑。
        }
    }

    @Unique
    private Map<ICraftingPatternDetails, Object> ae2qol$getWorkableTasks() {
        ae2qol$initReflection();
        try {
            if (WORKABLE_TASKS_FIELD != null) {
                return (Map<ICraftingPatternDetails, Object>) WORKABLE_TASKS_FIELD.get(this);
            }
        } catch (Throwable t) {
            // 忽略
        }
        return null;
    }

    @Unique
    private Map<ICraftingPatternDetails, Object> ae2qol$getTasks() {
        ae2qol$initReflection();
        try {
            if (TASKS_FIELD != null) {
                return (Map<ICraftingPatternDetails, Object>) TASKS_FIELD.get(this);
            }
        } catch (Throwable t) {
            // 忽略
        }
        return null;
    }

    @Unique
    private long ae2qol$taskValue(Object task) {
        ae2qol$initReflection();
        try {
            return TASK_PROGRESS_VALUE != null ? TASK_PROGRESS_VALUE.getLong(task) : 1L;
        } catch (Throwable t) {
            return 1L;
        }
    }

    @Unique
    private void ae2qol$taskValueAdd(Object task, long delta) {
        ae2qol$initReflection();
        try {
            if (TASK_PROGRESS_VALUE != null) {
                TASK_PROGRESS_VALUE.setLong(task, TASK_PROGRESS_VALUE.getLong(task) + delta);
            }
        } catch (Throwable t) {
            // 忽略
        }
    }

    @Unique
    private CraftingDiagnosticSessionId ae2qol$consumeCraftSession(Object task) {
        ae2qol$initReflection();
        try {
            return TASK_PROGRESS_CONSUME_SESSION != null
                ? (CraftingDiagnosticSessionId) TASK_PROGRESS_CONSUME_SESSION.invoke(task)
                : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Unique
    private long ae2qol$getServerTick() {
        ae2qol$initReflection();
        try {
            if (GET_SERVER_TICK != null) {
                return (Long) GET_SERVER_TICK.invoke(null);
            }
        } catch (Throwable t) {
            // 忽略
        }
        return 0L;
    }

    @Unique
    private boolean ae2qol$finalOutputIsFakeCrafting() {
        ae2qol$initReflection();
        try {
            if (FINAL_OUTPUT_FIELD == null) {
                return false;
            }
            final Object fo = FINAL_OUTPUT_FIELD.get(this);
            return FINAL_OUTPUT_IS_FAKE_CRAFTING != null && (Boolean) FINAL_OUTPUT_IS_FAKE_CRAFTING.invoke(fo);
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private boolean ae2qol$finalOutputIsFinalPattern(ICraftingPatternDetails details) {
        ae2qol$initReflection();
        try {
            if (FINAL_OUTPUT_FIELD == null) {
                return false;
            }
            final Object fo = FINAL_OUTPUT_FIELD.get(this);
            return FINAL_OUTPUT_IS_FINAL_PATTERN != null && (Boolean) FINAL_OUTPUT_IS_FINAL_PATTERN.invoke(fo, details);
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private void ae2qol$finalOutputSetFakeCrafting() {
        ae2qol$initReflection();
        try {
            if (FINAL_OUTPUT_FIELD == null) {
                return;
            }
            final Object fo = FINAL_OUTPUT_FIELD.get(this);
            if (FINAL_OUTPUT_SET_FAKE_CRAFTING != null) {
                FINAL_OUTPUT_SET_FAKE_CRAFTING.invoke(fo);
            }
        } catch (Throwable t) {
            // 忽略
        }
    }

    @Unique
    private void ae2qol$finalOutputPerformFakeCrafting(ICraftingPatternDetails details) {
        ae2qol$initReflection();
        try {
            if (FINAL_OUTPUT_FIELD == null) {
                return;
            }
            final Object fo = FINAL_OUTPUT_FIELD.get(this);
            if (FINAL_OUTPUT_PERFORM_FAKE_CRAFTING != null) {
                FINAL_OUTPUT_PERFORM_FAKE_CRAFTING.invoke(fo, details);
            }
        } catch (Throwable t) {
            // 忽略
        }
    }

    @Unique
    private void ae2qol$recordExpectedOutput(IAEStack<?> output, long tick, CraftingDiagnosticSessionId sessionId) {
        ae2qol$initReflection();
        try {
            if (DIAGNOSTICS_FIELD == null) {
                return;
            }
            final Object dc = DIAGNOSTICS_FIELD.get(this);
            if (DIAGNOSTICS_RECORD_EXPECTED_OUTPUT != null) {
                DIAGNOSTICS_RECORD_EXPECTED_OUTPUT.invoke(dc, output, tick, sessionId);
            }
        } catch (Throwable t) {
            // 忽略
        }
    }

    @Unique
    private int ae2qol$smartMultiplier(ICraftingMedium medium, ICraftingPatternDetails details, long remaining) {
        if (medium instanceof ISmartDoublingMedium sdm && sdm.isSmartDoublingEnabled()
            && !details.isCraftable()
            && remaining > 1L) {
            int cap = sdm.getMaxMultiplier(details);
            // 0 = 不限：跳过配置上限，一次发配剩余全部轮数（仍受介质容量与功率钳制）。
            if (Config.smartDoublingMaxRounds > 0) {
                cap = Math.min(cap, Config.smartDoublingMaxRounds);
            }
            // 防 long 溢出：剩余轮数超过 int 范围时按 int 上限封顶。
            cap = (int) Math.min(cap, Math.min(remaining, Integer.MAX_VALUE));
            if (cap < 1) {
                cap = 1;
            }
            return cap;
        }
        return 1;
    }

    /**
     * executeCrafting 入口：仅当存在智能倍增任务时才接管整个 tick。
     * 采用 HEAD 注入 + cancel，保留原方法字节码结构（INVOKE 指令不变），
     * 其它模组对同一方法的 @Inject/@At("INVOKE") 注入点仍可正常定位，避免冲突崩溃。
     *
     * @param eg 能源网格
     * @param cc 合成网格缓存
     * @param ci 回调信息（用于 cancel）
     */
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2qol$onExecuteCrafting(IEnergyGrid eg, CraftingGridCache cc, CallbackInfo ci) {
        if (this.suspended) {
            return;
        }
        boolean smart = false;
        try {
            smart = ae2qol$hasSmartDoublingTask(cc);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] hasSmartDoublingTask failed, falling back to vanilla path: " + t, t);
            return;
        }
        if (smart) {
            try {
                ae2qol$executeCraftingSmart(eg, cc);
            } catch (Throwable t) {
                // 兜底：倍增路径异常时不 cancel，让原版 executeCrafting 接管本 tick，避免拖死整个 CPU。
                MyMod.LOG.warn("[AE2QoL] Smart doubling crashed, falling back to vanilla path: " + t, t);
                return;
            }
            ci.cancel();
        }
    }

    /**
     * 检测当前待执行任务中是否存在启用智能倍增的介质（剩余轮数 &gt; 1 且非 craftable 样板）。
     *
     * @param cc 合成网格缓存
     * @return 是否接管本 tick
     */
    @Unique
    private boolean ae2qol$hasSmartDoublingTask(CraftingGridCache cc) {
        final Map<ICraftingPatternDetails, Object> workableTasks = ae2qol$getWorkableTasks();
        if (workableTasks == null || workableTasks.isEmpty()) {
            return false;
        }
        for (final Entry<ICraftingPatternDetails, Object> e : workableTasks.entrySet()) {
            if (ae2qol$taskValue(e.getValue()) <= 1L) {
                continue;
            }
            final ICraftingPatternDetails details = e.getKey();
            if (details.isCraftable()) {
                continue;
            }
            final List<ICraftingMedium> mediums = cc.getMediums(details);
            if (mediums == null) {
                continue;
            }
            for (final ICraftingMedium medium : mediums) {
                if (medium instanceof ISmartDoublingMedium sdm && sdm.isSmartDoublingEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 合成执行主循环（智能倍增版）：对启用智能倍增的介质按 N 轮提取并推送、按 N 轮记账。
     * 逐轮路径与原版语义一致；涉及私有内部类（TaskProgress / finalOutput /
     * CraftingCpuDiagnostics）的字段与方法通过缓存反射访问。
     * 仅在检测到智能倍增任务时由 {@link #ae2qol$onExecuteCrafting} 调用。
     *
     * @param eg 能源网格
     * @param cc 合成网格缓存
     */
    @Unique
    private void ae2qol$executeCraftingSmart(IEnergyGrid eg, CraftingGridCache cc) {
        if (this.suspended) return;

        Config.ensureFresh();

        // 每 tick 开始时重置 busy 集合，避免跨 tick 残留导致介质永远被跳过。
        knownBusyMediums.clear();

        final Map<ICraftingPatternDetails, Object> workableTasks = ae2qol$getWorkableTasks();
        final Map<ICraftingPatternDetails, Object> tasks = ae2qol$getTasks();
        if (workableTasks == null || tasks == null) {
            // 反射不可用时安全跳过本 tick（不应发生）。
            return;
        }

        final Iterator<Entry<ICraftingPatternDetails, Object>> craftingTaskIterator = workableTasks.entrySet()
            .iterator();

        int executedTasks = 0;
        while (craftingTaskIterator.hasNext()) {
            final Entry<ICraftingPatternDetails, Object> craftingEntry = craftingTaskIterator.next();

            if (ae2qol$taskValue(craftingEntry.getValue()) <= 0) {
                final ICraftingPatternDetails ceKey = craftingEntry.getKey();
                tasks.remove(ceKey);
                parallelismProvider.remove(ceKey);
                reasonProvider.remove(ceKey);
                craftingTaskIterator.remove();
                continue;
            }

            final ICraftingPatternDetails details = craftingEntry.getKey();
            ScheduledReason sr = null;
            final List<IAEStack<?>> condensedInputs = getExpandedCondensedInputs(details, cc);
            if (condensedInputs == null) {
                throw new IllegalStateException("Input-only pattern expansion failed");
            }
            if (!this.canCraft(details, condensedInputs)) {
                craftingTaskIterator.remove(); // No need to revisit this task on next executeCrafting this tick
                reasonProvider.put(details, ScheduledReason.NOT_ENOUGH_INGREDIENTS);
                continue;
            }

            boolean pushedPattern = false;
            boolean didPatternCraft;

            List<ICraftingMedium> mediumsList = cc.getMediums(details);
            List<ICraftingMedium> mediumListCheck = null;

            if (mediumsList.size() > 1) {
                mediumListCheck = parallelismProvider.getOrDefault(details, new ArrayList<>(mediumsList));
            }

            doWhileCraftingLoop: do {
                MEInventoryCrafting craftingInventory = null;
                didPatternCraft = false;

                if (mediumListCheck != null) {
                    if (mediumListCheck.isEmpty()) {
                        mediumListCheck = new ArrayList<>(mediumsList);
                    } else {
                        mediumsList = new ArrayList<>(mediumListCheck);
                    }
                }

                for (final ICraftingMedium medium : mediumsList) {
                    if (mediumListCheck != null) mediumListCheck.remove(medium);

                    if (ae2qol$taskValue(craftingEntry.getValue()) <= 0 || knownBusyMediums.contains(medium)) {
                        continue;
                    }

                    if (medium.isBusy()) {
                        knownBusyMediums.add(medium);
                        sr = medium.getScheduledReason();
                        continue;
                    }

                    // Find a valid craftingInventory for this craft.
                    double sum = 0;
                    int effectiveN = 1;
                    // PH 仓走 pushPatternMulti：配方缓冲只需 1×，轮数由该仓自行决定。
                    // 在构建缓冲时确定，避免 GT 构建的 N× 缓冲被误用于 PH 多推送。
                    boolean useMulti = false;
                    if (craftingInventory == null) {
                        final boolean craftable = details.isCraftable();
                        final List<IAEStack<?>> expandedInputs = craftable ? Arrays.asList(details.getAEInputs())
                            : getExpandedInputs(details, cc);
                        if (expandedInputs == null) {
                            throw new IllegalStateException("Input-only pattern expansion failed");
                        }

                        // 智能倍增：确定本次推送的有效轮数 N（仅非 craftable 样板）。
                        effectiveN = ae2qol$smartMultiplier(
                            medium,
                            details,
                            ae2qol$taskValue(craftingEntry.getValue()));
                        if (effectiveN > 1 && medium instanceof DualityInterface di
                            && di.isFakeCraftingMode()
                            && ae2qol$finalOutputIsFinalPattern(details)) {
                            // 假合成模式的最终样板走原版语义，不倍增。
                            effectiveN = 1;
                        }
                        // PH 仓走 pushPatternMulti：配方缓冲只需 1×，轮数由该仓自行决定。
                        useMulti = effectiveN > 1 && medium instanceof IMultiplePatternPushable;
                        // 原料钳制：仅对 GT/其它（CPU 缓冲需一次取 N×）生效。
                        // PH 仓走 pushPatternMulti 只需 CPU 1×，轮数由仓内缓冲空间决定，跳过此钳制。
                        if (effectiveN > 1 && !useMulti) {
                            // SIMULATE 探测，允许部分提取，不再要求严格全量匹配，避免 N 被静默降为 1。
                            for (final IAEStack<?> slotInput : expandedInputs) {
                                if (slotInput == null) {
                                    continue;
                                }
                                final long perRound = slotInput.getStackSize();
                                if (perRound <= 0) {
                                    continue;
                                }
                                // 防 int 溢出：perRound × N 必须落在 int 范围内，否则 GT 缓冲
                                // ItemStack 的 stackSize 会变为负数导致合成错乱。钳制后再构造探测栈。
                                if ((long) perRound * effectiveN > Integer.MAX_VALUE) {
                                    effectiveN = (int) (Integer.MAX_VALUE / perRound);
                                }
                                if (effectiveN <= 1) {
                                    break;
                                }
                                @SuppressWarnings("rawtypes")
                                final IAEStack probe = slotInput.copy()
                                    .setStackSize(perRound * effectiveN);
                                @SuppressWarnings("rawtypes")
                                final IAEStack avail = this.inventory.extractItems(probe, Actionable.SIMULATE);
                                final long rounds = avail == null ? 1L : Math.max(1L, avail.getStackSize() / perRound);
                                if (rounds < effectiveN) {
                                    effectiveN = (int) rounds;
                                }
                                if (effectiveN <= 1) {
                                    break;
                                }
                            }
                        }

                        for (final IAEStack<?> anInput : expandedInputs) {
                            if (anInput != null) {
                                sum += (double) anInput.getStackSize() / anInput.getAmountPerUnit();
                            }
                        }
                        // upgraded interface uses more power
                        if (medium instanceof DualityInterface) sum *= Math
                            .pow(4.0, ((DualityInterface) medium).getInstalledUpgrades(Upgrades.PATTERN_CAPACITY));

                        // 功率钳制（O(1)）：一次 SIMULATE 查询可用电总量，直接算出可负担的最大轮数。
                        // 替代原版逐轮递减的 O(N) 次网格查询 —— 大订单（如 1T 量级）下会直接卡死。
                        if (effectiveN > 1) {
                            final double availablePower = eg
                                .extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                            if (availablePower < sum - 0.01) {
                                // 连一轮电都付不起：与原版 while 循环收敛后一致，跳过本介质。
                                continue;
                            }
                            if (sum > 0) {
                                int affordable = (int) (availablePower / sum);
                                if (affordable < 1) {
                                    affordable = 1;
                                }
                                if (affordable < effectiveN) {
                                    effectiveN = affordable;
                                }
                            }
                        }

                        // check if there is enough power
                        double requiredPower = sum * effectiveN;
                        if (eg.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                            < requiredPower - 0.01) continue;

                        craftingInventory = craftable ? new MEInventoryCrafting(new ContainerNull(), 3, 3)
                            : new MEInventoryCrafting(new ContainerNull(), expandedInputs.size(), 1);

                        // Check if all items can be used for crafting.
                        boolean found = false;
                        for (int x = 0; x < expandedInputs.size(); x++) {
                            final IAEStack<?> slotInput = expandedInputs.get(x);
                            if (slotInput != null) {
                                found = false;
                                final IAEStack<?> target = useMulti ? slotInput
                                    : (effectiveN > 1 ? slotInput.copy()
                                        .setStackSize(slotInput.getStackSize() * effectiveN) : slotInput);
                                for (IAEStack ias : getExtractItems(target, details)) {
                                    IAEStack tempStack = ias.copy();
                                    if (craftable && !details.isValidItemForSlot(x, tempStack, this.getWorld()))
                                        continue;

                                    final IAEStack<?> aes = this.inventory.extractItems(tempStack, Actionable.MODULATE);
                                    if (aes != null) {
                                        found = true;
                                        craftingInventory.setInventorySlotContents(x, aes);
                                        if (effectiveN > 1 && aes.getStackSize() != target.getStackSize()) {
                                            // 防御：N× 配方缓冲必须是全量，否则 GT/PH 收到不足量会出错。
                                            // 取消本介质，物品已归还，下一 tick 重试。
                                            found = false;
                                            break;
                                        }
                                        if (!details.canBeSubstitute() && aes.getStackSize() == target.getStackSize()) {
                                            this.postChange(target, this.machineSrc);
                                            break;
                                        } else {
                                            this.postChange(aes, this.machineSrc);
                                        }
                                    }
                                }
                                if (!found) {
                                    break;
                                }
                            }
                        }

                        if (!found) {
                            // put stuff back.
                            returnItems(craftingInventory);
                            craftingInventory = null;
                            break;
                        }
                    }

                    boolean smartPushed = false;
                    if (useMulti && effectiveN > 1) {
                        // ---- 智能倍增（PH）：pushPatternMulti 一次接受 N 轮 ----
                        final int[] result = ((IMultiplePatternPushable) medium)
                            .pushPatternMulti(details, craftingInventory, effectiveN);
                        final int accepted = result == null || result.length == 0 ? 0 : Math.max(0, result[0]);
                        if (accepted > 0) {
                            this.somethingChanged = true;
                            this.remainingOperations--;
                            pushedPattern = true;
                            smartPushed = true;

                            eg.extractAEPower(sum * accepted, Actionable.MODULATE, PowerMultiplier.CONFIG);
                            ae2qol$accountSmartPush(accepted, details, craftingEntry.getValue());

                            craftingInventory = null; // hand off complete!
                            didPatternCraft = true;
                            this.markDirty();

                            // PH 仓的 isBusy() 可能同样不可靠，添加冷却防止每 tick 重复推送。
                            knownBusyMediums.add(medium);

                            executedTasks += accepted;
                            ae2qol$taskValueAdd(craftingEntry.getValue(), -accepted);
                            if (ae2qol$taskValue(craftingEntry.getValue()) <= 0) {
                                // This craftingEntry is done.
                                break doWhileCraftingLoop;
                            }

                            if (this.remainingOperations == 0) {
                                if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);
                                return;
                            }

                            final List<IAEStack<?>> condensedInputsForRetry = getExpandedCondensedInputs(details, cc);
                            if (condensedInputsForRetry == null) {
                                throw new IllegalStateException("Input-only pattern expansion failed");
                            }
                            if (!this.canCraft(details, condensedInputsForRetry)) {
                                sr = ScheduledReason.NOT_ENOUGH_INGREDIENTS;
                                break;
                            }
                        }
                    }

                    if (!smartPushed && medium.pushPattern(details, craftingInventory)) {
                        this.somethingChanged = true;
                        this.remainingOperations--;
                        pushedPattern = true;

                        // 注意：useMulti 回退时（pushPatternMulti 拒绝接收）craftingInventory 只含
                        // 1 轮材料（见上方 target 构造），必须走原版逐轮记账；仅 GT/其它
                        // （!useMulti）单次收满 N× 缓冲才允许按 N 轮记账，
                        // 否则会少产出 N-1 轮且白扣 (N-1)×sum 功率。
                        if (!useMulti && effectiveN > 1) {
                            // ---- 智能倍增（GT/其它）：pushPattern 一次收 N 轮 ----
                            eg.extractAEPower(sum * effectiveN, Actionable.MODULATE, PowerMultiplier.CONFIG);
                            ae2qol$accountSmartPush(effectiveN, details, craftingEntry.getValue());

                            craftingInventory = null; // hand off complete!
                            didPatternCraft = true;
                            this.markDirty();

                            // GT 的 isBusy() 始终返回 false，需手动冷却防止每 tick 重复推送。
                            knownBusyMediums.add(medium);

                            executedTasks += effectiveN;
                            ae2qol$taskValueAdd(craftingEntry.getValue(), -effectiveN);
                            if (ae2qol$taskValue(craftingEntry.getValue()) <= 0) {
                                // This craftingEntry is done.
                                break doWhileCraftingLoop;
                            }

                            if (this.remainingOperations == 0) {
                                if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);
                                return;
                            }

                            final List<IAEStack<?>> condensedInputsForRetry = getExpandedCondensedInputs(details, cc);
                            if (condensedInputsForRetry == null) {
                                throw new IllegalStateException("Input-only pattern expansion failed");
                            }
                            if (!this.canCraft(details, condensedInputsForRetry)) {
                                sr = ScheduledReason.NOT_ENOUGH_INGREDIENTS;
                                break;
                            }
                        } else {
                            // ---- 原版逐轮路径 ----
                            eg.extractAEPower(sum, Actionable.MODULATE, PowerMultiplier.CONFIG);

                            if (!ae2qol$finalOutputIsFakeCrafting() && ae2qol$finalOutputIsFinalPattern(details)) {
                                if (medium instanceof DualityInterface di && di.isFakeCraftingMode()) {
                                    ae2qol$finalOutputSetFakeCrafting();
                                }
                            }

                            if (ae2qol$finalOutputIsFakeCrafting() && ae2qol$finalOutputIsFinalPattern(details)) {
                                ae2qol$taskValueAdd(craftingEntry.getValue(), -1);

                                if (ae2qol$taskValue(craftingEntry.getValue()) <= 0) {
                                    tasks.remove(details);
                                    parallelismProvider.remove(details);
                                    reasonProvider.remove(details);
                                    craftingTaskIterator.remove();

                                    ae2qol$finalOutputPerformFakeCrafting(details);

                                    break;
                                } else {
                                    ae2qol$finalOutputPerformFakeCrafting(details);

                                    continue;
                                }
                            }

                            final CraftingDiagnosticSessionId diagnosticSessionId = ae2qol$consumeCraftSession(
                                craftingEntry.getValue());
                            final long outputObservedAtTick = diagnosticSessionId == null
                                || !this.isCraftingDiagnosticsEnabled() ? 0L : ae2qol$getServerTick();
                            // Process output items.
                            for (final IAEStack<?> outputItemStack : details.getCondensedAEOutputs()) {
                                if (outputObservedAtTick > 0L) {
                                    ae2qol$recordExpectedOutput(
                                        outputItemStack,
                                        outputObservedAtTick,
                                        diagnosticSessionId);
                                }
                                this.postChange(outputItemStack, this.machineSrc);
                                this.waitingFor.add(outputItemStack.copy());
                                this.postCraftingStatusChange(outputItemStack.copy());
                            }

                            if (details.isCraftable()) {
                                FMLCommonHandler.instance()
                                    .firePlayerCraftingEvent(
                                        Platform.getPlayer((WorldServer) this.getWorld()),
                                        details.getOutput(craftingInventory, this.getWorld()),
                                        craftingInventory);
                                for (int x = 0; x < craftingInventory.getSizeInventory(); x++) {
                                    final ItemStack output = Platform
                                        .getContainerItem(craftingInventory.getStackInSlot(x));
                                    if (output != null) {
                                        final IAEItemStack cItem = AEItemStack.create(output);
                                        this.postChange(cItem, this.machineSrc);
                                        this.waitingFor.add(cItem);
                                        this.postCraftingStatusChange(cItem);
                                    }
                                }
                            }

                            craftingInventory = null; // hand off complete!
                            didPatternCraft = true;
                            this.markDirty();

                            executedTasks += 1;
                            ae2qol$taskValueAdd(craftingEntry.getValue(), -1);
                            if (ae2qol$taskValue(craftingEntry.getValue()) <= 0) {
                                // This craftingEntry is done.
                                break doWhileCraftingLoop;
                            }

                            if (this.remainingOperations == 0) {
                                if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);
                                return;
                            }
                            // Smart blocking is fine sending the same recipe again.
                            if (medium.getBlockingMode() == BlockingMode.BLOCKING) break;

                            final List<IAEStack<?>> condensedInputsForRetry = getExpandedCondensedInputs(details, cc);
                            if (condensedInputsForRetry == null) {
                                throw new IllegalStateException("Input-only pattern expansion failed");
                            }
                            if (!this.canCraft(details, condensedInputsForRetry)) {
                                sr = ScheduledReason.NOT_ENOUGH_INGREDIENTS;
                                break;
                            }
                        }
                    }

                    sr = medium.getScheduledReason();
                }
                if (craftingInventory != null) {
                    // No suitable craftingInventory was found,
                    // put stuff back that was injected during the search.
                    returnItems(craftingInventory);
                }
            } while (didPatternCraft);

            if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);

            if (sr != null) reasonProvider.put(details, sr);

            if (!pushedPattern) {
                // If in all mediums no pattern was pushed,
                // no need to revisit this task on next executeCrafting this tick
                craftingTaskIterator.remove();
            }

        }
        for (CraftUpdateListener craftingStatusListener : craftUpdateListeners) {
            // if executed tasks is 0 for too much long time, we may need to send an alert in callback registered by
            // addon mods, like an email.
            craftingStatusListener.accept(executedTasks);
        }
    }

    /**
     * 智能倍增记账：按 rounds 轮一次性批量记账（等待输出按数量缩放，语义与原版逐轮一致）。
     * 原先逐轮循环对 2^31 量级 N 会执行数十亿次 IItemList.add / postChange，直接卡死游戏；
     * waitingFor 是 IItemList（同物品自动合并），按缩放后的总量各记账一次即可。
     * 诊断会话按本次 push 消耗 1 个（与原版 pushPattern 消耗一致，而非逐轮消耗）。
     *
     * @param rounds  本轮实际完成的轮数
     * @param details 合成样板
     * @param task    任务对象（TaskProgress）
     */
    @Unique
    private void ae2qol$accountSmartPush(int rounds, ICraftingPatternDetails details, Object task) {
        if (rounds <= 0) {
            return;
        }
        final CraftingDiagnosticSessionId diagnosticSessionId = ae2qol$consumeCraftSession(task);
        final long outputObservedAtTick = diagnosticSessionId == null || !this.isCraftingDiagnosticsEnabled() ? 0L
            : ae2qol$getServerTick();
        for (final IAEStack<?> outputItemStack : details.getCondensedAEOutputs()) {
            final IAEStack<?> total = outputItemStack.copy()
                .setStackSize(outputItemStack.getStackSize() * (long) rounds);
            if (outputObservedAtTick > 0L) {
                ae2qol$recordExpectedOutput(outputItemStack, outputObservedAtTick, diagnosticSessionId);
            }
            this.postChange(total, this.machineSrc);
            this.waitingFor.add(total);
            this.postCraftingStatusChange(total);
        }
    }
}
