package com.wztwzt.ae2_qof.client.render;

import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import com.wztwzt.ae2_qof.util.ModTextures;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * v7 贴图注册器：在<b>当前活动 atlas</b>（{@code TextureStitchEvent.Pre} 的 {@code event.map}）上注册
 * 机器贴图，结果写入 {@link ModTextures} 供渲染取用。
 *
 * <p><b>为何必须走这里，而不是 GT 图标队列</b>：GT 的 {@code GTCustomBlockIconContainer} 注册进的是
 * {@code GregTechAPI.sBlockIcons}——GT 在 {@code registerBlockIcons} 阶段绑定的那个 atlas 实例，
 * 该阶段早于资源包参与 atlas 重建。本 mod 的 PNG 来自资源包，在那个旧 map 里不存在，
 * {@code registerIcon} 一律返回 missingno 占位 sprite：表现为 icon 非 null、名字正确、16x16，
 * 但 25 张贴图的 UV 完全相同（都是同一个缺失纹理格子）→ 游戏内呈紫黑。</p>
 *
 * <p>而 {@code TextureStitchEvent.Pre} 的 {@code event.map} 是当前真正生效、且已纳入资源包的 atlas，
 * 在其上注册才能拿到带正确 UV 的 sprite。</p>
 */
@SideOnly(Side.CLIENT)
public final class V7TextureStitchHandler {

    private static final V7TextureStitchHandler INSTANCE = new V7TextureStitchHandler();

    private static boolean initialized = false;

    private V7TextureStitchHandler() {}

    /** 注册事件处理器（CommonProxy.init 中、仅客户端调用）。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        System.out.println("[AE2QoL-TEX] V7TextureStitchHandler registered on EVENT_BUS");
    }

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        // 只处理方块图集（textureType == 0）；物品图集是 1。
        if (event.map == null || event.map.getTextureType() != 0) {
            return;
        }
        int ok = 0;
        for (String name : ModTextures.MACHINES) {
            ok += register(event, ModTextures.BASE + name + ModTextures.F_FRONT) ? 1 : 0;
            ok += register(event, ModTextures.BASE + name + ModTextures.F_TOP) ? 1 : 0;
            ok += register(event, ModTextures.BASE + name + ModTextures.F_SIDE) ? 1 : 0;
        }
        ok += register(event, ModTextures.SHARED_BOTTOM) ? 1 : 0;
        int fail = (ModTextures.MACHINES.length * 3 + 1) - ok;
        System.out.println("[AE2QoL-TEX] stitch(Pre) done: registered=" + ok + " failed=" + fail);
    }

    private boolean register(TextureStitchEvent.Pre event, String path) {
        try {
            IIcon icon = event.map.registerIcon(path);
            if (icon == null) {
                return false;
            }
            ModTextures.registerStitched(path, icon);
            return true;
        } catch (Throwable t) {
            System.out.println("[AE2QoL-TEX] stitch register FAILED " + path + ": " + t);
            return false;
        }
    }
}
