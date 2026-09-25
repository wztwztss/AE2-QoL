package com.wztwzt.ae2_qof.mixin.ae;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ServerTerminalHelper;
import com.wztwzt.ae2_qof.tile.TileExIOPort;
import cn.dancingsnow.aeinfinitycell.item.ItemInfinityStorageCell;

import appeng.api.AEApi;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.tile.storage.TileIOPort;
import appeng.util.IterationCounter;

/**
 * 强化版 IO 端口传输倍率：对 ExIOPort 放大每次传输的物品数量。
 * <p>
 * fix51 追加：多通道元件的「逐通道补搬」，见 {@link #ae2qol$fanOutExtraChannels}。
 */
@Mixin(TileIOPort.class)
public abstract class MixinTileIOPort {

    /**
     * {@code TileIOPort.transferContents} 的反射句柄。
     * <p>
     * 该方法的返回类型是**私有内部类** {@code TileIOPort$TransferResult}，{@code @Shadow}/{@code @Invoker}
     * 都必须声明完整签名，而我们无法在源码里引用那个私有内部类，因此只能用反射调用（返回值不需要）。
     */
    private static Method ae2qol$transferContentsHandle;
    private static boolean ae2qol$transferContentsResolved;
    /** 热路径警告只记一次，避免端口未供电等常态情形每 tick 刷屏（参照 P2-027 的日志要求）。 */
    private static boolean ae2qol$fanOutWarned;

    @ModifyVariable(method = "transferContents", at = @At(value = "HEAD"), remap = false, ordinal = 0, argsOnly = true)
    private long ae2qol$transferContents(long itemsToMove) {
        if ((Object) this instanceof TileExIOPort) {
            Config.ensureFresh();
            int rate = Config.exIOPortTransferContentsRate;
            if (rate > 1 && itemsToMove > 0) {
                // 溢出保护：极端配置（Integer.MAX_VALUE）下乘积不超过 long 上界
                if (itemsToMove > Long.MAX_VALUE / rate) {
                    itemsToMove = Long.MAX_VALUE;
                } else {
                    itemsToMove *= rate;
                }
            }
        }
        return itemsToMove;
    }

    /**
     * 多通道元件的逐通道补搬（fix51）。
     * <p>
     * 根因（AE2 rv3-beta-1050 上游代码）：{@code TileIOPort.getInv(ItemStack)} 在
     * {@code AEStackTypeRegistry.getAllTypes()} 里只取**第一个**能返回非空 inventory 的通道就
     * {@code break}；而调用它的 {@code tickingRequest} 对每个元件每 tick 只做一次搬运。
     * 于是多通道元件（本模组的无限磁盘：物品 + 流体 + 源质）永远只搬运被选中的那一个通道，
     * 其余通道从不进入搬运循环。单通道元件（普通物品元件、ae2fc 流体元件）的「唯一匹配」
     * 恰好就是自己，所以不受影响——这也解释了「普通流体元件能搬、无限磁盘的流体搬不动」。
     * <p>
     * 处理：在原版整个 tick 逻辑跑完之后（{@code RETURN}），对本模组的无限磁盘按**同样的枚举顺序**
     * 取出它支持的通道列表，跳过索引 0（即 {@code getInv} 已经处理过的那个通道），
     * 对每个剩余通道用 AE2 自己的 {@code transferContents} 做一次搬运。
     * <p>
     * 边界（按用户确认的范围）：只处理 {@link ItemInfinityStorageCell}，单通道元件立即跳过，
     * 因此普通元件与其它模组元件的字节码路径与改动前完全一致。
     * <p>
     * 已知差异（如实登记）：补搬不参与原版循环里的「搬空后把元件弹到输出口」（{@code shouldMove}）判定，
     * 也不递减原循环的 {@code amountToMove} 配额——每个剩余通道按与主循环相同的**初始**预算处理。
     * 任何异常都只记一条警告，绝不让异常冒泡到 AE2 的 tick 逻辑。
     */
    @Inject(method = "tickingRequest", at = @At("RETURN"), remap = false)
    private void ae2qol$fanOutExtraChannels(IGridNode node, int ticksSinceLastCall,
        CallbackInfoReturnable<TickRateModulation> cir) {
        try {
            Method transfer = ae2qol$resolveTransferContents();
            if (transfer == null) return;

            final TileIOPort self = (TileIOPort) (Object) this;

            // @At("RETURN") 会在每个 return 前触发，其中包含「端口未供电/未上线 → IDLE」那条；
            // 此时取能源与存储会抛 GridAccessException，必须在这里先放行，既不做事也不刷日志。
            if (!self.getProxy().isActive()) return;

            final Object modeObject = self.getConfigManager().getSetting(Settings.OPERATION_MODE);
            if (!(modeObject instanceof OperationMode)) return;
            final OperationMode operationMode = (OperationMode) modeObject;
            if (operationMode != OperationMode.EMPTY && operationMode != OperationMode.FILL) return;

            final IInventory cells = self.getInventoryByName("cells");
            if (cells == null) return;

            // 以下三项只在真的发现本模组无限磁盘时才求值：普通 IO 端口（未插无限磁盘）
            // 每 tick 只多做几次廉价判断，不承担取网格能源/存储与升级档位计算的开销。
            long tickBudget = -1L;
            IEnergySource energy = null;
            IStorageGrid storage = null;

            for (int x = 0; x < cells.getSizeInventory(); x++) {
                final ItemStack cellStack = cells.getStackInSlot(x);
                // 只处理本模组的无限磁盘：本整合包内只有它是多通道元件
                if (cellStack == null || !(cellStack.getItem() instanceof ItemInfinityStorageCell)) continue;

                // 与 TileIOPort.getInv 完全相同的枚举顺序，保证索引 0 就是它已经处理过的通道
                final List<IAEStackType<?>> types = new ArrayList<>();
                final List<IMEInventory<?>> inventories = new ArrayList<>();
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    IMEInventory<?> inventory = AEApi.instance()
                        .registries()
                        .cell()
                        .getCellInventory(cellStack, null, type);
                    if (inventory != null) {
                        types.add(type);
                        inventories.add(inventory);
                    }
                }
                // 单通道元件走原路径，本注入完全不动
                if (types.size() <= 1) continue;

                if (tickBudget < 0) {
                    tickBudget = ae2qol$tickBudget(self);
                    energy = self.getProxy().getEnergy();
                    storage = self.getProxy().getStorage();
                }
                if (tickBudget <= 0) continue;

                for (int i = 1; i < types.size(); i++) {
                    final IAEStackType<?> type = types.get(i);
                    final Object monitor = storage.getMEMonitor(type);
                    if (monitor == null) continue;

                    final long budget = tickBudget * type.getAmountPerUnit();
                    if (budget <= 0) continue;

                    // EMPTY = 把元件里的东西搬进网络；FILL = 从网络搬进元件（口径与原循环一致）
                    final Object source = operationMode == OperationMode.EMPTY ? inventories.get(i) : monitor;
                    final Object destination = operationMode == OperationMode.EMPTY ? monitor : inventories.get(i);
                    transfer.invoke(self, energy, source, destination, budget);
                }
            }
        } catch (Throwable t) {
            if (!ae2qol$fanOutWarned) {
                ae2qol$fanOutWarned = true;
                MyMod.LOG.warn("[AE2QoL] IO port multi-channel fan-out skipped: {}", t.toString());
            }
        }
    }

    /**
     * 复现 {@code TileIOPort.tickingRequest} 里每 tick 的传输预算：基准 256，
     * 再按 SPEED / SUPERSPEED / SUPERLUMINALSPEED 三个升级档位依次相乘。
     * 数值必须与上游保持一致，改动时需对照 AE2 源码。
     */
    private static long ae2qol$tickBudget(TileIOPort self) {
        long amount = 256L;

        switch (self.getInstalledUpgrades(Upgrades.SPEED)) {
            case 1:
                amount *= 2;
                break;
            case 2:
                amount *= 4;
                break;
            case 3:
                amount *= 8;
                break;
            default:
                break;
        }

        switch (self.getInstalledUpgrades(Upgrades.SUPERSPEED)) {
            case 1:
                amount *= 16;
                break;
            case 2:
                amount *= 128;
                break;
            case 3:
                amount *= 1024;
                break;
            default:
                break;
        }

        switch (self.getInstalledUpgrades(Upgrades.SUPERLUMINALSPEED)) {
            case 1:
                amount *= 131_072L;
                break;
            case 2:
                amount *= 8_388_608L;
                break;
            case 3:
                amount *= 536_870_912L;
                break;
            default:
                break;
        }

        return amount;
    }

    /** 解析并缓存 {@code TileIOPort.transferContents} 的反射句柄；失败只记一次警告。 */
    private static Method ae2qol$resolveTransferContents() {
        if (!ae2qol$transferContentsResolved) {
            ae2qol$transferContentsResolved = true;
            try {
                Method method = TileIOPort.class.getDeclaredMethod(
                    "transferContents",
                    IEnergySource.class,
                    IMEInventory.class,
                    IMEInventory.class,
                    long.class);
                method.setAccessible(true);
                ae2qol$transferContentsHandle = method;
            } catch (Throwable t) {
                MyMod.LOG.warn(
                    "[AE2QoL] IO port multi-channel fan-out disabled (transferContents not resolvable): {}",
                    t.toString());
            }
        }
        return ae2qol$transferContentsHandle;
    }

    // ===== fix53-diag：IO 端口「通道选择 / 搬走判定」诊断（**仅记录，不改变任何判定**） =====

    /**
     * 最近一次 {@code getInv} 返回的通道库存，以及它对应的元件。
     * 用途：把 {@code shouldMove} 的诊断绑定到紧邻的那一次 {@code getInv}（循环内两者同槽位、无交错）。
     */
    private ItemStack ae2qol$lastCell;
    private IMEInventory<?> ae2qol$lastInv;

    @Inject(method = "getInv", at = @At("RETURN"), remap = false)
    private void ae2qol$diagRememberChannel(ItemStack is, CallbackInfoReturnable<IMEInventory<?>> cir) {
        final IMEInventory<?> inv = cir.getReturnValue();
        this.ae2qol$lastCell = inv == null ? null : is;
        this.ae2qol$lastInv = inv;
        try {
            if (is != null && is.getItem() instanceof ItemInfinityStorageCell && inv != null) {
                ServerTerminalHelper.diagOnce(
                    "IO-PICK",
                    "端口为该无限磁盘选中的通道 = " + inv.getStackType()
                        .getId() + "（本模组补搬会跳过该通道、只处理其余通道）");
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "shouldMove", at = @At("HEAD"), remap = false)
    private void ae2qol$diagShouldMove(IMEInventory<?> inventory, boolean sourceEmptyAfterTransfer,
        boolean destinationFull, boolean didWork, boolean moveOnEmptyWhileFilling, OperationMode om, FullnessMode fm,
        CallbackInfoReturnable<Boolean> cir) {
        try {
            if (this.ae2qol$lastInv != inventory) return;
            final ItemStack cell = this.ae2qol$lastCell;
            if (cell == null || !(cell.getItem() instanceof ItemInfinityStorageCell)) return;
            // 先去重门控再算详情：下面两次全通道枚举在热路径上很贵，不能每 tick 白算
            if (!ServerTerminalHelper.diagNeeded("IO-MOVE")) return;
            ServerTerminalHelper.diagOnce(
                "IO-MOVE",
                "是否把该元件搬到输出半区：OperationMode=" + om
                    + ", FullnessMode=" + fm
                    + ", didWork=" + didWork
                    + ", 被选中通道还有内容=" + !ae2qol$availableStacks(inventory).isEmpty()
                    + ", 其余通道还有内容=" + ae2qol$otherChannelsHaveContent(cell, inventory));
        } catch (Throwable ignored) {}
    }

    /** 与 AE2 的 {@code TileIOPort.getAvailableStacks} 同口径（上游用原始类型，此处同样如此）。 */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static IItemList<? extends IAEStack> ae2qol$availableStacks(IMEInventory<?> inventory) {
        if (inventory instanceof IMEMonitor<?> monitor) return monitor.getStorageList();
        final IMEInventory raw = inventory;
        return raw.getAvailableItems(raw.getStackType().createList(), IterationCounter.fetchNewId());
    }

    /** 该元件除「端口已选中的那个通道」之外，是否还有其它通道仍存有内容。 */
    private static boolean ae2qol$otherChannelsHaveContent(ItemStack cell, IMEInventory<?> chosen) {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IMEInventory<?> inv = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(cell, null, type);
            if (inv == null) continue;
            if (inv.getStackType() == chosen.getStackType()) continue;
            if (!ae2qol$availableStacks(inv).isEmpty()) return true;
        }
        return false;
    }
}
