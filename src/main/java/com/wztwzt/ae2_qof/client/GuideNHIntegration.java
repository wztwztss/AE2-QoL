package com.wztwzt.ae2_qof.client;

import net.minecraft.util.ResourceLocation;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.Loader;

/**
 * GuideNH 指南注册（3.14.0）：此前仅有 md 资源文件而未注册 Guide 实例，
 * 导致物品索引（frontmatter item_ids）从未生效、悬停本 mod 物品无「长按 G」提示。
 * 仅客户端调用；GuideNH 未安装时静默跳过。
 */
public final class GuideNHIntegration {

    private GuideNHIntegration() {}

    public static void register() {
        try {
            if (!Loader.isModLoaded("guidenh")) return;
            com.hfstudio.guidenh.guide.Guide.builder(new ResourceLocation("ae2_qof", "guidenh"))
                .register(true)
                .build();
            MyMod.LOG.info("[AE2QoL] GuideNH guide registered");
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] GuideNH registration failed: " + t);
        }
    }
}
