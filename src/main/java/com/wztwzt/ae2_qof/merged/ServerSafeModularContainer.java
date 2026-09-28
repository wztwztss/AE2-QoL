package com.wztwzt.ae2_qof.merged;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 服务端专用的「无槽位 MUI1 容器」工厂（3.23.2-fix1）。
 *
 * <h2>为什么需要它（专用服务器上右键打不开界面的根因）</h2>
 * 两个搬运界面（Wild 通配窗口、批量样板生成器）的 {@code getServerGuiElement} 原本**也**调用各自的
 * {@code createWindow(...)} 去构建完整窗口。而那些窗口类里含**客户端专用**代码
 * （{@code Minecraft.getMinecraft().displayGuiScreen(new GuiTextInputDialog(...))} —— fix43 起给自绘界面加的
 * 「改」按钮），专用服务器上 Forge 的 {@code SideTransformer} 会拒绝加载客户端类，实测日志：
 *
 * <pre>
 * [Server thread/WARN] [ae2_qof/]: [AE2QoL] 打开通配样板界面失败（shift=false）
 * java.lang.NoClassDefFoundError: net/minecraft/client/gui/GuiScreen
 *   at ...wildport.gui.WildcardGuiHandler.getServerGuiElement(WildcardGuiHandler.java:28)
 *   at ...merged.MergedGuiHandler.delegateUi(MergedGuiHandler.java:106)
 *   at cpw.mods.fml.common.network.NetworkRegistry.getRemoteGuiContainer(NetworkRegistry.java:243)
 *   at cpw.mods.fml.common.network.internal.FMLNetworkHandler.openGui(FMLNetworkHandler.java:75)
 *   at net.minecraft.entity.player.EntityPlayer.openGui(EntityPlayer.java:2209)
 *   at ...wildcard.ItemSmartWildcardPattern.func_77659_a(ItemSmartWildcardPattern.java:189)
 * Caused by: java.lang.RuntimeException: Attempted to load class bdw for invalid side SERVER
 *   at cpw.mods.fml.common.asm.transformers.SideTransformer.transform(SideTransformer.java:50)
 * </pre>
 *
 * 异常发生在 FML 取「服务端容器」那一步 ⇒ 容器取不到 ⇒ 开窗包（S2DOpenWindow）**根本不发**
 * ⇒ 客户端表现为「右键完全没反应」。单机是 CLIENT 侧，不受 {@code SideTransformer} 约束，所以单机一直正常。
 *
 * <h2>为什么「空窗口」是安全的</h2>
 * <ul>
 * <li>这两个界面**没有任何槽位**：{@code ItemSlot / SlotWidget / SlotGroup / PlayerInventory / addSlotToContainer}
 * 在全仓它们的 gui 包内计数为 <b>0</b>；MUI1 的 {@code ModularUIContainer} 构造器自身也不加背包槽（javap 实证）
 * ⇒ 服务端空窗口与客户端完整窗口的槽位集合一致（都为空），不会出现槽位错位。</li>
 * <li>这两个界面**不使用 MUI1 的同步处理器**（零 {@code SyncHandler/SyncValue/syncManager}），
 * 跨端全走它们自己的通道（{@code _wild} / {@code _apg}）⇒ 服务端不需要 MUI1 的同步上下文。</li>
 * <li>{@code ModularWindow} / {@code ModularWindow$Builder} / {@code UIBuildContext} / {@code ModularUIContainer}
 * 的类常量池里 {@code net/minecraft/client/} 引用数均为 <b>0</b>（已扫）⇒ 本类在服务端可安全加载。</li>
 * </ul>
 * 窗口本体仍旧**只在 {@code getClientGuiElement}（客户端）里构建**，与整合包自带先例一致
 * （GT 自带的 {@code gtPlusPlus/core/handler/GuiHandler.getServerGuiElement} 也只 new 纯容器）。
 */
public final class ServerSafeModularContainer {

    private ServerSafeModularContainer() {}

    /**
     * 服务端 {@code getServerGuiElement} 用：空窗口 + 无槽位容器。
     *
     * <p>失败不静默：任何异常都记 WARN（含堆栈）并返回 null —— 那种情况下客户端依旧收不到开窗包，
     * 但日志能立刻区分「服务端没能建容器」与「包没发出去」，避免重演本次这种"客户端毫无线索"的排查。
     */
    public static Object slotless(EntityPlayer player) {
        try {
            ModularUIContext context = new ModularUIContext(new UIBuildContext(player), () -> {});
            // 尺寸只影响服务端这个占位窗口本身（不会下发到客户端，客户端会自建真正的窗口）
            ModularWindow emptyWindow = ModularWindow.builder(176, 166)
                .build();
            return new ModularUIContainer(context, emptyWindow);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 服务端无槽位容器创建失败：专用服务器上该界面将打不开（请把这条日志发给开发者）", t);
            return null;
        }
    }
}
