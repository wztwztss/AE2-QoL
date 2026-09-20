package com.wztwzt.ae2_qof.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 自定义方块贴图工具：按资源路径加载 ae2_qof 自己的 16x16 方块贴图。
 *
 * <p><b>贴图来源</b>：PNG 由资源包（AE2QoL-v7-resourcepack.zip）提供，mod jar 内不含机器贴图，
 * 贴图资源与 mod 解耦。</p>
 *
 * <p><b>双通道注册（关键）</b>：实测证明 <b>GT 图标队列单通道不可用</b>——
 * {@code GTCustomBlockIconContainer.run()} 走的是 {@code GregTechAPI.sBlockIcons}，而那是 GT 在
 * {@code registerBlockIcons} 阶段绑定的 atlas 实例，**早于资源包参与 atlas 重建**；
 * 我们来自资源包的路径在那个旧 map 中不存在，{@code registerIcon} 一律返回 missingno 占位 sprite
 * （表现为：icon 非 null、名字正确、16x16，但 25 张贴图 UV 全部相同 = 同一个缺失纹理格子 → 紫黑）。
 * 因此本类自行监听 {@code TextureStitchEvent.Pre}（方块图集），用<b>当前活动 atlas</b>
 * （{@code event.map}）注册，结果存入 {@link #STITCHED}，渲染时优先取用。</p>
 *
 * <p>客户端 {@link StitchedContainer} 取图：stitched（当前 atlas，UV 正确）→ GT 机箱图标兜底，
 * 保证渲染永不返回 null（1.7.10 的 GT 渲染器碰到 null icon 会提前 return 而不关闭 Tessellator，
 * 进而导致下一帧 {@code Already tesselating!} 崩溃）。</p>
 *
 * <p>服务端使用 {@link ServerSafeContainer}（纯接口实现，不依赖 gregtech.client）。</p>
 */
public final class ModTextures {

    /**
     * 贴图根路径：放在 <b>gregtech 命名空间</b>下的 ae2qol 专属子目录。
     *
     * <p>为什么不用 ae2_qof 自己的命名空间：GT 的图标注册走 sBlockIcons（GT registerBlockIcons
     * 阶段绑定的 atlas，早于资源包），自定义命名空间的新路径在那里必然缺失并回退到 missingno，
     * 表现为「icon 非 null、名字对、16x16，但 UV 全相同 → 紫黑」。改挂 gregtech 命名空间 +
     * 独立子目录，既复用 GT 的资源加载通路，又不污染 GT 其它机器（只有本 mod 这 8 台引用它）。</p>
     */
    public static final String BASE = "gregtech:blocks/ae2qol/";

    /** 每台机器的面级文件名（与资源包目录结构一致）。 */
    public static final String F_FRONT = "/FRONT";
    public static final String F_TOP = "/TOP";
    public static final String F_SIDE = "/SIDE";

    /** 8 台机器共用的底面（GT 机箱底面）。 */
    public static final String SHARED_BOTTOM = "gregtech:blocks/ae2qol/_shared/BOTTOM";

    /** 全部机器基名（8 个），每个有 主体/_side/_top 三张贴图 + 共用 casing_side，共 25 张。 */
    public static final String[] MACHINES = {
        "universal_maintenance_hatch",
        "adaptive_net_terminal",
        "adaptive_net_hatch",
        "adaptive_net_laser_hatch",
        "adaptive_net_dynamo_hatch",
        "adaptive_net_laser_target",
        "wireless_energy_input",
        "wireless_energy_output"
    };

    private static final Map<String, ITexture> CACHE = new HashMap<>();

    /**
     * 当前活动 atlas（TextureStitchEvent.Pre 的 event.map）注册得到的图标。
     * 这是唯一能拿到正确 UV 的通道——GT 的 sBlockIcons 早于资源包，注册结果是 missingno。
     */
    private static final Map<String, IIcon> STITCHED = new HashMap<>();

    /**
     * 方案 B 通道：由 MixinTextureMap 在原版方块图集 registerIcons 尾部注册后回填。
     * 这些图标来自同一张方块图集，UV 正确，是 v7 贴图的首选来源。
     */
    private static final Map<String, IIcon> BAKED = new HashMap<>();

    /** 由图集 Mixin 回填注册成功的图标。 */
    public static void registerBaked(String path, IIcon icon) {
        if (path != null && icon != null) {
            BAKED.put(path, icon);
        }
    }

    /** 在 TextureStitchEvent.Pre 中注册并记录图标（由 ClientProxy 的事件处理器调用）。 */
    public static void registerStitched(String path, IIcon icon) {
        STITCHED.put(path, icon);
    }

    /** 当前活动 atlas 上是否已注册到该路径的图标。 */
    public static boolean hasStitched(String path) {
        return STITCHED.get(path) != null;
    }


    /** 已打印过「首次渲染命中」诊断的路径（防刷屏）。 */
    private static final Set<String> HIT_LOGED = new HashSet<>();

    /** 已打印过诊断的路径（防刷屏）。 */
    private static final Set<String> MISS_LOGED = new HashSet<>();

    private ModTextures() {}

    /**
     * 按资源路径取贴图（首次创建并缓存）。
     *
     * <p>客户端只走 {@link StitchedContainer}——方案 B 图标由
     * {@code MixinTextureMap} 在原版方块图集注册后回填到 {@link #BAKED}；
     * 旧事件通道 {@link #STITCHED} 仅作兼容保留。不再使用 GT 的
     * {@code GTCustomBlockIconContainer}：它注册进 sBlockIcons（早于资源包），实测恒为 missingno。</p>
     */
    public static ITexture tex(String path) {
        ITexture t = CACHE.get(path);
        if (t == null) {
            if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
                t = TextureFactory.of(new StitchedContainer(path));
            } else {
                t = TextureFactory.of(new ServerSafeContainer(path));
            }
            CACHE.put(path, t);
        }
        return t;
    }


    /**
     * v7 贴图总开关：0=自动（默认，按 25 张 PNG 是否齐全判定）、1=强制开、-1=强制关。
     *
     * <p>强制关时 8 台机器必定走 {@code super.getTexture(...)}（GT 默认外观），
     * 不依赖任何资源探测——这是「彻底回到改材质之前」的可靠回退手段。</p>
     */
    public static volatile int forceMode = 0;

    /** 自动判定的缓存结果（仅在判定成功/失败均缓存，postInit 后不再变化）。 */
    private static Boolean ready = null;

    /**
     * 客户端：v7 贴图是否可用。任一情况返回 false → 机器走基类原始渲染（GT 默认外观，不紫黑）。
     *
     * <p><b>为何不再只探第一张</b>：旧实现只检查 {@code MACHINES[0]} 一张 PNG，
     * 且只在 init 阶段探一次；资源管理器处于 reload 中间态时可能拿到兜底资源而误判为 true，
     * 导致 8 台机器全部走 v7 分支、实际 PNG 不可读 → 不论加不加资源包都紫黑。
     * 现改为：强制关 → false；强制开 → true；自动 → 25 张全部可读且 PNG 头合法才 true，
     * 且仅在 postInit（资源加载完成后）才做判定。</p>
     *
     * <p><b>只缓存 true</b>：资源包在进入世界时会再次 reload，若把首次判定得到的 false 也缓存，
     * 就会永久锁死为默认外观（表现为「装了材质包也没变化」）。false 每次重查，代价是三次
     * getResource 调用，可忽略。</p>
     */
    public static boolean isReady() {
        if (forceMode == -1) {
            return false;
        }
        if (forceMode == 1) {
            return true;
        }
        // 只缓存 true：资源包在进入世界时会再 reload 一次，
        // 若把首次（资源尚未就绪时的）false 也缓存，就会永久锁死为默认外观。
        if (ready != null && ready.booleanValue()) {
            return true;
        }
        if (!FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return false; // 服务端不渲染方块贴图
        }
        if (!resourceCheckAllowed) {
            return false; // 未到 postInit，判定不可信
        }
        String miss = firstMissing();
        if (miss == null) {
            ready = Boolean.TRUE;
            System.out.println("[AE2QoL-TEX] isReady()=true (" + (MACHINES.length * 3 + 1) + " png all reachable)");
            return true;
        }
        if (MISS_LOGED.add(miss)) {
            System.out.println("[AE2QoL-TEX] isReady()=false, first missing: " + miss);
        }
        return false;
    }

    /** postInit 前为 false：资源加载未完成，判定不可信，此时一律走 GT 默认外观。 */
    private static volatile boolean resourceCheckAllowed = false;

    /** 由 CommonProxy.postInit 调用：资源已全部加载完成，此后允许做自动判定。 */
    public static void allowResourceCheck() {
        resourceCheckAllowed = true;
        // 清掉可能存在的早期误判缓存，让下一次询问重新计算
        ready = null;
        System.out.println("[AE2QoL-TEX] resource check enabled (postInit reached)");
    }

    /** 返回第一个不可读的贴图路径（诊断用），全部可读返回 null。 */
    private static String firstMissing() {
        for (String name : MACHINES) {
            for (String face : new String[] { F_FRONT, F_TOP, F_SIDE }) {
                String p = BASE + name + face;
                if (!checkResourceReachable(p)) {
                    return p;
                }
            }
        }
        if (!checkResourceReachable(SHARED_BOTTOM)) {
            return SHARED_BOTTOM;
        }
        return null;
    }

    /** 全部 25 张 PNG 是否都可读，且文件头为合法 PNG（89 50 4E 47）。 */
    private static boolean checkAllResourcesReachable() {
        return firstMissing() == null;
    }

    /**
     * 资源层可达性检查：从注册名反推 PNG 路径，读取并校验 PNG 文件头。
     *
     * <p>只判断「能打开」是不够的——资源缺失时部分加载链会返回兜底资源而不抛异常，
     * 因此这里额外校验前 4 字节必须是 PNG 魔数 {@code 89 50 4E 47}，否则视为不可达。</p>
     */
    private static boolean checkResourceReachable(String path) {
        try {
            int colon = path.indexOf(':');
            if (colon < 0) {
                return false;
            }
            String domain = path.substring(0, colon);
            String pngPath = "textures/" + path.substring(colon + 1) + ".png";
            net.minecraft.client.resources.IResource res =
                net.minecraft.client.Minecraft.getMinecraft().getResourceManager()
                    .getResource(new net.minecraft.util.ResourceLocation(domain, pngPath));
            java.io.InputStream in = res.getInputStream();
            try {
                byte[] head = new byte[4];
                int n = 0;
                while (n < 4) {
                    int r = in.read(head, n, 4 - n);
                    if (r < 0) {
                        break;
                    }
                    n += r;
                }
                if (n < 4) {
                    return false;
                }
                return (head[0] & 0xFF) == 0x89 && (head[1] & 0xFF) == 0x50
                    && (head[2] & 0xFF) == 0x4E && (head[3] & 0xFF) == 0x47;
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 预创建全部 25 个贴图容器。
     *
     * <p>方案 B 下实际图标由 {@code MixinTextureMap} 在原版方块图集注册后回填；
     * 这里只做容器预创建，使渲染期无额外分配。</p>
     */
    public static void init() {
        if (forceMode == -1) {
            System.out.println("[AE2QoL-TEX] init() skipped: v7 textures force-disabled (machines keep default GT look)");
            return;
        }
        for (String name : MACHINES) {
            front(name);
            top(name);
            side(name);
        }
        bottom();
        System.out.println("[AE2QoL-TEX] init(): 25 containers created (icons come from TextureMap mixin)");
    }

    /** 机器正面贴图（<机器名>/FRONT）。 */
    public static ITexture front(String name) {
        return tex(BASE + name + F_FRONT);
    }

    /** 机器顶面贴图（<机器名>/TOP）。 */
    public static ITexture top(String name) {
        return tex(BASE + name + F_TOP);
    }

    /** 机器侧面贴图（<机器名>/SIDE）。 */
    public static ITexture side(String name) {
        return tex(BASE + name + F_SIDE);
    }

    /** 机器底面贴图（8 台共用的 _shared/BOTTOM）。 */
    public static ITexture bottom() {
        return tex(SHARED_BOTTOM);
    }

    /**
     * 客户端容器：返回在<b>当前活动 atlas</b> 上注册的图标（{@link #STITCHED}）。
     *
     * <p>取不到时（Mixin 未回填/资源包未启用）兜底 GT 机箱图标，保证渲染永不返回 null——
     * 1.7.10 的 GT 渲染器碰到 null icon 会提前 return 而不关闭 Tessellator，
     * 下一帧抛 {@code Already tesselating!} 崩溃。</p>
     */
    private static class StitchedContainer implements IIconContainer {

        private final String path;

        StitchedContainer(String path) {
            this.path = path;
        }

        @Override
        public IIcon getIcon() {
            IIcon icon = STITCHED.get(path);
            if (icon == null) {
                icon = BAKED.get(path);
            }
            if (icon != null) {
                if (HIT_LOGED.add(path)) {
                    float minU = -1F, minV = -1F, maxU = -1F, maxV = -1F;
                    try {
                        minU = icon.getMinU();
                        minV = icon.getMinV();
                        maxU = icon.getMaxU();
                        maxV = icon.getMaxV();
                    } catch (Throwable ignored) {}
                    System.out.println(
                        "[AE2QoL-TEX] getIcon HIT " + path + " UV=[" + minU + "," + minV + "]-[" + maxU + ","
                            + maxV + "]");
                }
                // 兜底：未真正装订进图集的 sprite 尺寸为 0，UV 恒为 [0,0]-[0,0]。
                // 直接返回它会让方块变成透明（挖空），因此退化 UV 一律回退 GT 机箱，
                // 宁可外观不对也不能让机器不可见。
                if (icon.getMaxU() > 0F || icon.getMaxV() > 0F) {
                    return icon;
                }
                if (MISS_LOGED.add(path)) {
                    System.out.println(
                        "[AE2QoL-TEX] getIcon DEGENERATE " + path + " (UV all zero) -> fallback GT casing");
                }
                return fallbackIcon();
            }
            if (MISS_LOGED.add(path)) {
                System.out.println("[AE2QoL-TEX] getIcon MISS " + path + " -> fallback GT casing");
            }
            return fallbackIcon();
        }

        /**
         * 兜底图标：依次尝试若干 GT 机箱图标，全不可用时退回原版石头的图标。
         *
         * <p>渲染器拿到 null icon 会提前 return 且不关闭 Tessellator，下一帧直接
         * {@code Already tesselating!} 崩溃，所以这里必须保证永不返回 null。</p>
         */
        private static IIcon fallbackIcon() {
            try {
                IIcon icon = Textures.BlockIcons.MACHINE_LV_SIDE.getIcon();
                if (icon != null) {
                    return icon;
                }
            } catch (Throwable ignored) {}
            try {
                IIcon icon = Textures.BlockIcons.MACHINE_CASING_SOLID_STEEL.getIcon();
                if (icon != null) {
                    return icon;
                }
            } catch (Throwable ignored) {}
            return net.minecraft.init.Blocks.stone.getIcon(0, 0);
        }

        @Override
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        public ResourceLocation getTextureFile() {
            return new ResourceLocation("textures/atlas/blocks.png");
        }
    }


    /** 服务端安全容器：服务端不渲染方块贴图，getIcon 返回 null 即可（不会抛错）。 */
    private static class ServerSafeContainer implements IIconContainer {

        private final String path;

        ServerSafeContainer(String path) {
            this.path = path;
        }

        @Override
        public IIcon getIcon() {
            return null;
        }

        @Override
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        public ResourceLocation getTextureFile() {
            return new ResourceLocation("textures/atlas/blocks.png");
        }
    }
}
