package com.gali.ae2_auto_pattern_upload.mixin.ae;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gali.ae2_auto_pattern_upload.network.CraftingCompletePacket;
import com.gali.ae2_auto_pattern_upload.network.ModNetwork;

import appeng.api.features.INetworkEncodable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.CraftBranchFailure;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.misc.TileSecurity;

/**
 * 合成完成通知：捕获 submitJob 的发起玩家与产出，completeJob 完成后向玩家发送通知。
 */
@Mixin(CraftingCPUCluster.class)
public abstract class MixinCraftingCPUCluster {

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
        if (this.player != null && this.output != null && this.networkKey != 0) {
            for (int i = 0; i < this.player.inventory.mainInventory.length; i++) {
                ItemStack stack = this.player.inventory.mainInventory[i];
                if (isSameNetworkKey(stack)) {
                    return;
                }
            }
        }
    }

    @Unique
    private boolean isSameNetworkKey(ItemStack item) {
        if (item != null && item.getItem() instanceof INetworkEncodable encodable) {
            String key = encodable.getEncryptionKey(item);
            if (key != null && key.equals(Long.toString(this.networkKey))) {
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
}
