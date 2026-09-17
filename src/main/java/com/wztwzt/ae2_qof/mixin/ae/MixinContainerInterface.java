package com.wztwzt.ae2_qof.mixin.ae;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

import com.wztwzt.ae2_qof.MyMod;
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

    @GuiSync(30)
    public boolean smartDoubling = false;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ae2qol$initSmartDoubling(InventoryPlayer ip, IInterfaceHost te, CallbackInfo ci) {
        Object duality = ae2qol$duality(this);
        if (duality instanceof ISmartDoublingMedium sdm) {
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
        Object duality = ae2qol$duality(this);
        if (duality instanceof ISmartDoublingMedium sdm) {
            sdm.setSmartDoubling(enabled);
        } else {
            MyMod.LOG.warn(
                "[AE2QoL] smart doubling toggle could not reach DualityInterface (container={})",
                this.getClass().getName());
        }
    }

    /**
     * 解析容器的 DualityInterface。
     * <p>
     * 原实现用 {@code @Shadow private DualityInterface myDuality}，但该字段在当前 AE2 混淆映射中缺失
     * （构建期持续报 {@code Unable to locate obfuscation mapping for @Shadow field}），
     * shadow 取值不可靠会让服务端开关写不到真正生效的介质上——表现为「界面开关能点、倍增不生效」。
     * 改为沿类层级反射查找同名字段，与项目内既有容器解析方式一致，不再依赖 Shadow 映射。
     */
    private static Object ae2qol$duality(Object container) {
        for (Class<?> c = container.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("myDuality");
                f.setAccessible(true);
                return f.get(container);
            } catch (NoSuchFieldException ignored) {
                continue;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
