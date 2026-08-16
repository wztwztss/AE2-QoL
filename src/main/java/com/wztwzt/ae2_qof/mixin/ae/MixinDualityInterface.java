package com.wztwzt.ae2_qof.mixin.ae;

import java.util.EnumSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.api.config.InsertionMode;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingMedium.BlockingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.util.InventoryAdaptor;

/**
 * 为 ME 接口（方块 / 线缆面板）的 DualityInterface 增加智能倍增能力：
 * <ul>
 * <li>新增持久化布尔开关 {@code smartDoubling}；</li>
 * <li>实现 {@link ISmartDoublingMedium}，基于相邻机器当前剩余容量（simulateAddStack）
 * 保守估算最大可推送轮数。</li>
 * </ul>
 */
@Mixin(DualityInterface.class)
public abstract class MixinDualityInterface implements ISmartDoublingMedium {

    @Shadow
    @Final
    private IInterfaceHost iHost;

    @Shadow
    @Final
    private boolean isFluidInterface;

    @Shadow
    protected abstract boolean hasItemsToSend();

    @Shadow
    public abstract boolean isFakeCraftingMode();

    @Shadow
    public abstract BlockingMode getBlockingMode();

    @Unique
    private boolean smartDoubling;

    @Override
    public boolean isSmartDoublingEnabled() {
        return this.smartDoubling;
    }

    public void setSmartDoubling(boolean enabled) {
        this.smartDoubling = enabled;
        this.iHost.saveChanges();
    }

    @Override
    public int getMaxMultiplier(ICraftingPatternDetails details) {
        if (!isSmartDoublingEnabled()) {
            return 1;
        }
        // 合成配方（craftable）逐槽一格原料，不能倍增；流体接口与假合成亦不倍增。
        if (details.isCraftable() || this.isFluidInterface || isFakeCraftingMode()) {
            return 1;
        }
        // 阻塞 / 智能阻塞 / 接口有滞留物：维持原逐轮语义，避免绕过阻塞检查。
        if (getBlockingMode() != BlockingMode.NONE || hasItemsToSend()) {
            return 1;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorldObj();
        final EnumSet<ForgeDirection> directions = this.iHost.getTargets();
        final IAEStack<?>[] inputs = details.getAEInputs();

        int best = Config.smartDoublingMaxRounds;
        boolean foundAdaptor = false;
        for (final ForgeDirection s : directions) {
            final TileEntity te = w.getTileEntity(tile.xCoord + s.offsetX, tile.yCoord + s.offsetY, tile.zCoord + s.offsetZ);
            if (te == null) {
                continue;
            }
            // 直接吃样板的机器（ICraftingMachine.acceptsPlans）不支持倍增。
            if (te instanceof ICraftingMachine cm && cm.acceptsPlans()) {
                return 1;
            }
            final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
            if (ad == null) {
                continue;
            }
            foundAdaptor = true;
            for (final IAEStack<?> input : inputs) {
                if (input == null || !(input instanceof IAEItemStack)) {
                    // 流体 / 未知类型无法用 simulateAddStack 估算，安全回退为逐轮。
                    return 1;
                }
                final long roundSize = input.getStackSize();
                if (roundSize <= 0) {
                    continue;
                }
                // 二分查找：该面上当前能整份吞下多少轮。
                int lo = 1;
                int hi = Config.smartDoublingMaxRounds;
                while (lo < hi) {
                    final int mid = (lo + hi + 1) >>> 1;
                    final IAEStack<?> probe = input.copy().setStackSize(roundSize * mid);
                    final IAEStack<?> leftover = ad.simulateAddStack(probe, InsertionMode.DEFAULT);
                    if (leftover == null || leftover.getStackSize() == 0) {
                        lo = mid;
                    } else {
                        hi = mid - 1;
                    }
                }
                if (lo < best) {
                    best = lo;
                }
            }
        }
        return foundAdaptor ? best : 1;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void ae2qol$writeSmartDoubling(NBTTagCompound data, CallbackInfo ci) {
        data.setBoolean("ae2qolSmartDoubling", this.smartDoubling);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void ae2qol$readSmartDoubling(NBTTagCompound data, CallbackInfo ci) {
        this.smartDoubling = data.getBoolean("ae2qolSmartDoubling");
    }
}