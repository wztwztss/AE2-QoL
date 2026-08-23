package com.wztwzt.ae2_qof.mixin.ae;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.api.ISmartDoublingContainer;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerInterface;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;

/**
 * 为 ME 接口容器增加智能倍增同步字段：{@code @GuiSync(30)} 与服务端/客户端双向同步，
 * 服务端写入 DualityInterface 持久化。
 * <p>
 * 同步 id 用 30：AE2 {@code ContainerInterface} 继承链已用 0/1/3~18，
 * GTNotLeisure 的 {@code ContainerSuperInterface}（extends ContainerInterface）用 19，
 * 避免同 id 被声明两次导致 {@code DataSynchronization} 抛异常。
 */
@Mixin(ContainerInterface.class)
public abstract class MixinContainerInterface implements ISmartDoublingContainer {

    @Shadow
    @Final
    private DualityInterface myDuality;

    @GuiSync(30)
    public boolean smartDoubling = false;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ae2qol$initSmartDoubling(InventoryPlayer ip, IInterfaceHost te, CallbackInfo ci) {
        if (this.myDuality instanceof ISmartDoublingMedium sdm) {
            this.smartDoubling = sdm.isSmartDoublingEnabled();
        }
    }

    @Override
    public boolean getSmartDoubling() {
        return this.smartDoubling;
    }

    @Override
    public void setSmartDoubling(boolean enabled) {
        this.smartDoubling = enabled;
        if (this.myDuality instanceof ISmartDoublingMedium sdm) {
            sdm.setSmartDoubling(enabled);
        }
    }
}
