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
import com.wztwzt.ae2_qof.MyMod;
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

    @Shadow
    @Final
    private appeng.tile.inventory.AppEngInternalInventory patterns;

    @Shadow
    public java.util.List<ICraftingPatternDetails> craftingList;

    @Shadow
    public abstract int getPriority();

    /**
     * 3.22.0 智能通配样板：ME 接口**建样板索引**时，把一张通配样板展开成 N 张各自合法的普通样板。
     *
     * <p>为什么在这里截胡：AE2 原生 {@code addToCraftingList} 一张样板只登记**一个** details，
     * 而“一张覆盖一类配方”的唯一省事办法就是把它展开成多张普通样板（参考实现同款路线，
     * 取证见 {@code docs/research/wildcardpattern-forensics.md}）。
     * 展开出的样板由本模组物品的原生解码路径转成 details（它们已剥掉规则子树，就是普通样板），
     * 优先级沿用 AE2 原生算式 {@code slot - 36 * getPriority()}。
     *
     * <p>失败绝不静默：解析不出候选时记 WARN 并取消（不留半截状态）；
     * 展开器自身异常时**放行原生逻辑**（此时它退化为“按模板那一张样板”），同时记 WARN。
     */
    @Inject(method = "addToCraftingList", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2qol$expandSmartWildcard(int slot, CallbackInfo ci) {
        try {
            if (this.patterns == null || slot < 0 || slot >= this.patterns.getSizeInventory()) return;
            net.minecraft.item.ItemStack stack = this.patterns.getStackInSlot(slot);
            if (stack == null || !com.wztwzt.ae2_qof.wildcard.SmartWildcardState.isSmartWildcard(stack)) return;

            net.minecraft.item.ItemStack single = stack.copy();
            single.stackSize = 1;
            net.minecraft.world.World world = this.iHost == null || this.iHost.getTileEntity() == null
                ? null
                : this.iHost.getTileEntity()
                    .getWorldObj();
            com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander.Result result =
                com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander.expand(single, world);

            if (result.isEmpty()) {
                MyMod.LOG.warn("[AE2QoL] 智能通配样板未展开出任何样板（ME 接口 slot={}）：{}", slot, result.describe());
                ci.cancel();
                return;
            }

            final int priority = slot - 36 * this.getPriority();
            int added = 0;
            for (net.minecraft.item.ItemStack concrete : result.patterns) {
                if (concrete == null || concrete.getItem() == null) continue;
                ICraftingPatternDetails details = null;
                if (concrete.getItem() instanceof appeng.api.implementations.ICraftingPatternItem patternItem) {
                    details = patternItem.getPatternForItem(concrete, world);
                }
                if (details == null) continue;
                details.setPriority(priority);
                this.craftingList.add(details);
                added++;
            }
            if (added == 0) {
                MyMod.LOG.warn(
                    "[AE2QoL] 智能通配样板展开后全部解码失败（ME 接口 slot={}）：{}",
                    slot,
                    result.describe());
                ci.cancel();
                return;
            }
            ci.cancel();
        } catch (Throwable t) {
            // 放行原生逻辑（退化为模板样板），但绝不静默
            MyMod.LOG.warn("[AE2QoL] 智能通配样板在 ME 接口展开异常，已回退原生解码：slot=" + slot, t);
        }
    }

    @Unique
    private boolean smartDoubling;

    @Override
    public boolean isSmartDoublingEnabled() {
        return this.smartDoubling;
    }

    public void setSmartDoubling(boolean enabled) {
        this.smartDoubling = enabled;
        this.iHost.saveChanges();
        MyMod.LOG.info("[AE2QoL] smart doubling set to {} on {}", enabled, this.iHost.getTileEntity());
    }

    @Override
    public int getMaxMultiplier(ICraftingPatternDetails details) {
        Config.ensureFresh();
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

        // 0 = 不限：以 Integer.MAX_VALUE 作为二分上界，实际仍受相邻机器容量钳制。
        final int cfgMax = Config.smartDoublingMaxRounds;
        final int max = cfgMax <= 0 ? Integer.MAX_VALUE : cfgMax;

        int best = max;
        boolean foundAdaptor = false;
        for (final ForgeDirection s : directions) {
            final TileEntity te = w
                .getTileEntity(tile.xCoord + s.offsetX, tile.yCoord + s.offsetY, tile.zCoord + s.offsetZ);
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
                // 指数扩张 + 区间二分：先倍增找到第一个失败点，再在其前的小区间内二分，
                // 替代固定 [1,max] 全程二分（max=不限时 31 次 probe/输入/面，#51）。
                // 小容量场景（常见：只能吞几轮）仅需个位数次 simulateAddStack。
                int lo = 0;
                int step = 1;
                while (lo + step <= max) {
                    final IAEStack<?> probe = input.copy()
                        .setStackSize(roundSize * (lo + step));
                    final IAEStack<?> leftover = ad.simulateAddStack(probe, InsertionMode.DEFAULT);
                    if (leftover != null && leftover.getStackSize() > 0) {
                        break;
                    }
                    lo += step;
                    if (lo >= max) {
                        break;
                    }
                    step <<= 1;
                    if ((long) lo + step > max) {
                        step = max - lo;
                    }
                }
                // 在 (lo, min(lo+step, max)] 内二分收敛（lo 已知可行）
                int hi = Math.min(max, lo + step);
                while (lo < hi) {
                    final int mid = (lo + hi + 1) >>> 1;
                    final IAEStack<?> probe = input.copy()
                        .setStackSize(roundSize * mid);
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
