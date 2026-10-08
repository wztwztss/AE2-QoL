package com.wztwzt.ae2_qof.mixin.ae;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.AEApi;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.me.GridAccessException;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.storage.TileIOPort;
import appeng.util.ConfigManager;
import cn.dancingsnow.aeinfinitycell.ae.AbstractInfinityInventoryHandler;
import cn.dancingsnow.aeinfinitycell.item.ItemInfinityStorageCell;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.tile.TileExIOPort;

/**
 * 无限存储元件的 IO 端口适配。
 * <p>
 * <b>3.27.0 换实现</b>：改用上游 AE2InfinityCell 1.0.5 的「逐 tick 轮询通道」方案
 * （{@code getInv} 每次轮到一个通道 + {@code matches}/{@code shouldMove} 把"搬空/抽空"判定扩展到全部通道），
 * 替换本项目 fix51 的「原版跑完后补搬其余通道」方案。换的理由是两条实测出来的语义缺陷：
 * <ol>
 *   <li>补搬给每个剩余通道各发一份**完整**预算（256 × 速度升级档位），多通道元件会把 IO 端口吞吐放大到 n 倍；</li>
 *   <li>补搬不参与原版的 {@code shouldMove}「搬空后弹出元件」判定，可能出现"校验通道空了、别的通道还有货就弹出"。</li>
 * </ol>
 * 轮询方案每 tick 只服务一个通道（总吞吐与原版一致）且三处判定都以"全部通道"为准，因此取代原实现。
 * <p>
 * 本项目原有的 <b>ExIOPort 传输倍率</b>注入原样保留（只改 {@code transferContents} 的入参初值，与轮询不冲突）。
 * <p>
 * 生效范围：只对 {@link ItemInfinityStorageCell}（本模组的无限磁盘）动手；普通元件与其它模组元件走原版路径。
 * 注意本 mixin 的配置是 {@code required: false}：若下面的 {@code @Shadow} 与 AE2 字段名不匹配，
 * Mixin 只会记一条警告并**静默跳过**本 mixin（IO 端口退回原版单通道行为），启动日志里核对是否真的 Mixing 过。
 */
@Mixin(value = TileIOPort.class, remap = false)
public abstract class MixinTileIOPort {

    @Shadow
    private ItemStack currentCell;

    @Shadow
    private IMEInventory<?> cachedInventory;

    @Shadow
    @Final
    private ConfigManager manager;

    @Unique
    private int ae2qol$channelRotation;

    // ==================== 本项目保留：ExIOPort 传输倍率 ====================

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

    // ==================== 上游 1.0.5：逐 tick 轮询通道 ====================

    /**
     * AE2 的 {@code TileIOPort#getInv} 每 tick 只取存储元件第一个可用通道的库存，多通道的无限存储单元
     * 因此只会传输物品，流体等通道永远轮不到。这里让 IO 端口在单元的各通道间逐 tick 轮询，
     * 并把「搬空/抽空」的完成判定扩展到全部通道。
     */
    @Inject(method = "getInv", at = @At("HEAD"), cancellable = true)
    private void ae2qol$rotateChannels(ItemStack is, CallbackInfoReturnable<IMEInventory<?>> cir) {
        if (is == null || !(is.getItem() instanceof ItemInfinityStorageCell)) {
            return;
        }
        if (this.currentCell != is) {
            this.currentCell = is;
            this.ae2qol$channelRotation = 0;
        }

        List<IAEStackType<?>> types = new ArrayList<>(AEStackTypeRegistry.getAllTypes());
        int typeCount = types.size();
        if (typeCount == 0) {
            return;
        }

        boolean emptying = (OperationMode) this.manager.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY;
        for (int i = 0; i < typeCount; i++) {
            int index = (this.ae2qol$channelRotation + i) % typeCount;
            IMEInventory<?> inv = ae2qol$channelInventory(is, types.get(index));
            if (!(inv instanceof AbstractInfinityInventoryHandler<?>handler)) {
                continue;
            }
            // 搬空模式下跳过已无内容的通道，避免 tick 浪费在空通道上
            if (emptying && handler.getUsedTypes() == 0L) {
                continue;
            }
            this.ae2qol$channelRotation = (index + 1) % typeCount;
            this.cachedInventory = inv;
            cir.setReturnValue(inv);
            return;
        }

        // 搬空模式下全部通道都已为空：交给第一个通道，让 matches 判定后把元件弹到输出槽
        IMEInventory<?> fallback = ae2qol$channelInventory(is, types.get(0));
        this.cachedInventory = fallback;
        cir.setReturnValue(fallback);
    }

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void ae2qol$matchesAllChannels(FullnessMode fm, OperationMode om, IMEInventory<?> src, boolean didWork,
        CallbackInfoReturnable<Boolean> cir) {
        if (!(src instanceof AbstractInfinityInventoryHandler<?>handler)) {
            return;
        }
        if (fm == FullnessMode.EMPTY && om == OperationMode.EMPTY) {
            // 所有通道都搬空后才算"已空"，而不是只看当前轮到的通道
            cir.setReturnValue(ae2qol$cellDrained(handler.getCellStack()));
        }
    }

    @Inject(method = "shouldMove", at = @At("HEAD"), cancellable = true)
    private void ae2qol$shouldMoveAllChannels(IMEInventory<?> inventory, boolean sourceEmptyAfterTransfer,
        boolean destinationFull, boolean didWork, boolean moveOnEmptyWhileFilling, OperationMode om, FullnessMode fm,
        CallbackInfoReturnable<Boolean> cir) throws GridAccessException {
        if (!(inventory instanceof AbstractInfinityInventoryHandler<?>)) {
            return;
        }
        if (moveOnEmptyWhileFilling && didWork) {
            // 填充模式下所有通道的网络侧都抽空后才弹出元件
            cir.setReturnValue(destinationFull || ae2qol$networkDrained());
        }
    }

    @Unique
    private boolean ae2qol$cellDrained(ItemStack cellStack) {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IMEInventory<?> inv = ae2qol$channelInventory(cellStack, type);
            if (inv instanceof AbstractInfinityInventoryHandler<?>handler && handler.getUsedTypes() > 0L) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private boolean ae2qol$networkDrained() throws GridAccessException {
        AENetworkInvTile self = (AENetworkInvTile) (Object) this;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IMEMonitor<?> monitor = self.getProxy()
                .getStorage()
                .getMEMonitor(type);
            if (monitor != null && !monitor.getStorageList()
                .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private IMEInventory<?> ae2qol$channelInventory(ItemStack cell, IAEStackType<?> type) {
        return AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(cell, null, type);
    }
}
