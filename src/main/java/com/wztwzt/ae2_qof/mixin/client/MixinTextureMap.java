package com.wztwzt.ae2_qof.mixin.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.renderer.texture.TextureMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.IIcon;

import com.wztwzt.ae2_qof.util.ModTextures;

/**
 * v7 材质方案 B：把本模组的 25 张机器贴图混入原版方块图集的注册列表。
 *
 * <p><b>为什么需要</b>：1.7.10 的 {@code TextureMap.registerIcons()} 只会把「所有已注册方块 /
 * 物品」引用的路径放进 {@code mapRegisteredSprites}。运行时新造的路径不在其中，任何注册尝试
 * 都会拿到 missingno 占位（表现为 25 张不同贴图 UV 全部相同、渲染紫黑）。GT 的图标队列
 * {@code GregTechAPI.sBlockIcons} 走的也是同一个 map，同样解决不了这个问题（8 轮实测已证伪）。</p>
 *
 * <p><b>做法</b>：注入 {@code registerIcons()}（运行时名 {@code func_110573_f}，MCP 名
 * {@code registerIcons}，两个都列上并 {@code remap=false}——与 {@code MixinGuiSuperDualInterface}
 * 处理 {@code func_146284_a} 的方式一致）。TAIL 时原版 / Forge / GT 已全部注册完，
 * 我们再把 25 张 v7 路径补进 {@code mapRegisteredSprites}，本帧 stitcher 会把它们当作
 * 「本来就存在的贴图」一起装订进图集，资源包正常参与解析。</p>
 *
 * <p><b>Angelica 兼容</b>：Angelica / MCPatcherForge 对 TextureMap 的注入均发生在
 * {@code func_110571_b}（loadTextureAtlas）的早段与 stitcher 阶段，本注入在
 * {@code func_110573_f}（registerIcons）TAIL，与它们的注入点无重叠，不会互相干扰。</p>
 */
@Mixin(TextureMap.class)
public abstract class MixinTextureMap {

    @Unique
    private Method ae2qol$registerIconMethod;

    @Unique
    private Integer ae2qol$atlasType;

    @Inject(method = { "registerIcons", "func_110573_f" }, at = @At("TAIL"), remap = false)
    private void ae2qol$registerV7Icons(CallbackInfo ci) {
        if (!ae2qol$resolveAtlasMembers()) {
            return;
        }
        Integer atlasType = ae2qol$atlasType;
        // 只在方块图集（textureType=0）上注入；物品图集走另一套 basePath，混入会错位。
        if (atlasType == null || atlasType.intValue() != 0) {
            return;
        }
        // 必须每次 registerIcons 都补注册：该方法开头会 clear() 整张清单，而图集在启动期
        // 会重建多次（构造器一次、首次 loadTextureAtlas 一次且 skipFirst 跳过装载、
        // 之后每次 refreshResources 再一次）。若只注册一次，真正装载的那次清单里没有我们，
        // sprite 永远停在 0x0，UV 全 0，渲染出来就是透空。
        for (String machine : ModTextures.MACHINES) {
            ae2qol$ensure(ModTextures.BASE + machine + ModTextures.F_FRONT);
            ae2qol$ensure(ModTextures.BASE + machine + ModTextures.F_TOP);
            ae2qol$ensure(ModTextures.BASE + machine + ModTextures.F_SIDE);
        }
        ae2qol$ensure(ModTextures.SHARED_BOTTOM);
    }

    @Unique
    private void ae2qol$ensure(String path) {
        try {
            // registerIcon 对已存在的路径会直接返回既有图标；这样无论资源包是否先注册，都能拿到图集图标。
            IIcon icon = (IIcon) ae2qol$registerIconMethod.invoke(this, path);
            if (icon != null) {
                ModTextures.registerBaked(path, icon);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 同时兼容开发环境 MCP 名与生产环境 SRG 名。
     * 图集 Mixin 显式关闭 remap 后，{@code @Shadow} 不能依赖注解处理器改写，
     * 因此这里用反射按两套名字解析，宁可多一次查找也不让补丁静默挂空。
     */
    @Unique
    private boolean ae2qol$resolveAtlasMembers() {
        if (ae2qol$registerIconMethod != null && ae2qol$atlasType != null) {
            return true;
        }
        try {
            Class<?> type = TextureMap.class;
            if (ae2qol$registerIconMethod == null) {
                ae2qol$registerIconMethod = ae2qol$findMethod(type, "registerIcon", "func_94245_a");
            }
            if (ae2qol$atlasType == null) {
                Field field = ae2qol$findField(type, "textureType", "field_94255_a");
                field.setAccessible(true);
                ae2qol$atlasType = field.getInt(this);
            }
            return ae2qol$registerIconMethod != null && ae2qol$atlasType != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static Field ae2qol$findField(Class<?> type, String mcpName, String srgName) throws ReflectiveOperationException {
        try {
            return type.getDeclaredField(mcpName);
        } catch (NoSuchFieldException ignored) {
            return type.getDeclaredField(srgName);
        }
    }

    @Unique
    private static Method ae2qol$findMethod(Class<?> type, String mcpName, String srgName) throws ReflectiveOperationException {
        try {
            return type.getDeclaredMethod(mcpName, String.class);
        } catch (NoSuchMethodException ignored) {
            return type.getDeclaredMethod(srgName, String.class);
        }
    }

}
