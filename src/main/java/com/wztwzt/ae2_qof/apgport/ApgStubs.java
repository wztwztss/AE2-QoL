/*
 * AE2PatternGen 的 ClientProxy 里有"打开存储/详情屏"与"关闭当前屏"三个方法，我们搬运时跳过了它的 proxy。
 * 这里用显式桩类收口：能实现的（关闭屏幕）照常实现；暂未接线的（存储/详情面板）记 WARN 并说明原因，
 * 绝不静默（本项目铁则）。后续要接这两个面板时只改这里，不必改搬运来的 GUI 代码。
 */
package com.wztwzt.ae2_qof.apgport;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.wztwzt.ae2_qof.MyMod;

public final class ApgStubs {

    private ApgStubs() {}

    /** 暂未接线：AE2PatternGen 的"样板存储面板"（缓存浏览器）。 */
    public static void openPatternStorageScreen(EntityPlayer player) {
        MyMod.LOG.warn("[AE2QoL] apgport：样板存储面板尚未接线（该按钮暂不可用）player={}", player);
    }

    /** 暂未接线：AE2PatternGen 的"样板详情面板"。 */
    public static void openPatternDetailScreen(EntityPlayer player, int index, List<String> inputs,
        List<String> outputs) {
        MyMod.LOG.warn(
            "[AE2QoL] apgport：样板详情面板尚未接线（index={} inputs={} outputs={}）",
            index,
            inputs == null ? 0 : inputs.size(),
            outputs == null ? 0 : outputs.size());
    }

    /** 关闭当前界面（这个能真正实现）。 */
    public static void closeCurrentScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.displayGuiScreen(null);
        }
    }
}