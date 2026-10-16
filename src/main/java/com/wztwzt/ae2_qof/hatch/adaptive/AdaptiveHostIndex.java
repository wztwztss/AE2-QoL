package com.wztwzt.ae2_qof.hatch.adaptive;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 3.32.0：「仓 → 主机」的**权威反查**，以及 TecTech 兼容激光仓的安培写入。
 *
 * <h2>为什么必须改掉旧的"一次性解析"（问题①根因）</h2>
 * 旧实现只在仓自己的 {@code onFirstTick} 里调 {@code AdaptiveHatchHelper.findAttachedMachine}，
 * 而它依赖 {@link MultiblockRegistry} 里"控制器已经注册过"。只要仓先 tick（区块加载顺序、
 * 结构尚未成型、服务器重启后先加载到仓的区块），解析必返 {@code null}，于是把**仓自己的名字与图标**
 * 写进 NBT 缓存，而且此后**没有任何重算路径** —— 这正是用户实测"481 行全叫『自适应电网仓』"的原因。
 *
 * <h2>本类怎么做</h2>
 * 反过来用 GT 自己维护的仓清单（已用实例 GT jar 的 javap 确认都是 public）：
 * {@link MTEMultiBlockBase#mEnergyHatches} / {@link MTEMultiBlockBase#mDynamoHatches} /
 * {@link MTEMultiBlockBase#getExoticEnergyHatches()}。**一次遍历所有已注册主机**就能把全部仓认领完
 * （O(主机数)，不是 O(仓×主机)），且**完全不依赖任何 tick 时序**；
 * TecTech 的激光仓（不是我们的类，没有 helper）也顺带认领，用于"纳入统计与配置"。
 *
 * <p>
 * 副产物：主机是"谁在吃电"的真实主体，GT 把它记在 {@link MTEMultiBlockBase#mEUt}
 * （消耗路径经字节码里的 {@code ineg} 存为负值），可直接带符号显示。
 */
public final class AdaptiveHostIndex {

    /** 一个"主机 + 它名下的仓"分组。{@code host == null} 表示未识别主机（该仓不属于任何已注册多方块）。 */
    public static final class HostGroup {

        public final MTEMultiBlockBase host;
        public final int dim;
        public final int x;
        public final int y;
        public final int z;
        /** 主机控制器物品的 MTE id（客户端据此本地化名称 + 画图标）；-1 = 未识别主机。 */
        public final int hostMetaId;
        /** 本网络已绑定、且被这台主机认领的仓。 */
        public final List<AdaptiveHatchHelper> adaptive = new ArrayList<>();
        /** 本主机上的 TecTech 兼容激光仓（不是我们的类）。 */
        public final List<MetaTileEntity> compat = new ArrayList<>();
        /**
         * 这台主机此刻是否真的在运行（{@link MTEMultiBlockBase#mMaxProgresstime} > 0）。
         * GT 的 {@code mEUt} 停机后**不清零**，所以显示耗电前必须先问这个。
         */
        public final boolean running;

        HostGroup(MTEMultiBlockBase host, int dim, int x, int y, int z, int hostMetaId) {
            this.host = host;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hostMetaId = hostMetaId;
            this.running = isRunning(host);
        }

        public boolean hostKnown() {
            return host != null && hostMetaId >= 0;
        }

        /** 主机实时耗电（GT 自己的字段；消耗为负）。未识别主机返回 0。 */
        public long hostEut() {
            try {
                return host == null ? 0L : host.mEUt;
            } catch (Throwable t) {
                return 0L;
            }
        }
    }

    private AdaptiveHostIndex() {}

    private static final Set<String> WARNED = java.util.Collections
        .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private static void warnOnce(String key, String message, Throwable t) {
        if (!WARNED.add(key)) return;
        if (t == null) {
            MyMod.LOG.warn("[AE2QoL] " + message);
        } else {
            MyMod.LOG.warn("[AE2QoL] " + message, t);
        }
    }

    private static String posKey(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    /**
     * 反查本网络全部仓的主机归属。
     *
     * <p>
     * 返回顺序即显示顺序（未排序）：调用方负责排序。
     * 未识别主机的仓**不会丢**，它们各自成组（{@code host == null}），由调用方标注出来——
     * 本项目原则：不做"信息静默消失"的兜底。
     */
    public static List<HostGroup> scan(UUID owner, int frequency) {
        AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(owner, frequency);
        List<AdaptiveHatchHelper> helpers = network == null ? java.util.Collections.<AdaptiveHatchHelper>emptyList()
            : network.getAllHelpers();

        Map<String, AdaptiveHatchHelper> byPos = new HashMap<>();
        for (AdaptiveHatchHelper h : helpers) {
            byPos.put(posKey(h.getDim(), h.getX(), h.getY(), h.getZ()), h);
        }

        Map<String, HostGroup> groups = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();

        for (MTEMultiBlockBase host : MultiblockRegistry.snapshot()) {
            if (host == null) continue;
            IGregTechTileEntity base = host.getBaseMetaTileEntity();
            if (base == null || base.getWorld() == null || base.getWorld().isRemote) continue;
            int hdim = base.getWorld().provider.dimensionId;
            int hx = base.getXCoord();
            int hy = base.getYCoord();
            int hz = base.getZCoord();

            List<MTEHatch> candidates = new ArrayList<>();
            if (host.mEnergyHatches != null) candidates.addAll(host.mEnergyHatches);
            if (host.mDynamoHatches != null) candidates.addAll(host.mDynamoHatches);
            try {
                List<MTEHatch> exotic = host.getExoticEnergyHatches();
                if (exotic != null) candidates.addAll(exotic);
            } catch (Throwable t) {
                warnOnce("exotic", "读取主机异种能量仓清单失败（激光/多安培仓可能漏统计）", t);
            }

            HostGroup group = null;
            for (MTEHatch hatch : candidates) {
                if (hatch == null) continue;
                IGregTechTileEntity hb = hatch.getBaseMetaTileEntity();
                if (hb == null || hb.getWorld() == null) continue;
                String k = posKey(hb.getWorld().provider.dimensionId, hb.getXCoord(), hb.getYCoord(), hb.getZCoord());
                if (!claimed.add(k)) continue;

                AdaptiveHatchHelper helper = byPos.get(k);
                boolean compat = helper == null
                    && (HatchType.isTecTechLaserSource(hatch) || HatchType.isTecTechLaserTarget(hatch));
                if (helper == null && !compat) continue;

                if (group == null) {
                    String gk = posKey(hdim, hx, hy, hz);
                    group = groups.get(gk);
                    if (group == null) {
                        group = new HostGroup(host, hdim, hx, hy, hz, hostMetaIdOf(host));
                        groups.put(gk, group);
                    }
                }
                if (helper != null) {
                    group.adaptive.add(helper);
                } else {
                    group.compat.add(hatch);
                }
            }
        }

        // 用户口径：TecTech 激光仓只统计"已接入我们电网的主机"⇒ 只含兼容仓、没有我们任何仓的分组丢弃
        groups.values()
            .removeIf(g -> g.adaptive.isEmpty());

        // 没有任何主机认领的仓：单独成组并标注（信息不消失）
        for (AdaptiveHatchHelper h : helpers) {
            if (claimed.contains(posKey(h.getDim(), h.getX(), h.getY(), h.getZ()))) continue;
            HostGroup orphan = new HostGroup(null, h.getDim(), h.getX(), h.getY(), h.getZ(), -1);
            orphan.adaptive.add(h);
            groups.put("orphan:" + posKey(h.getDim(), h.getX(), h.getY(), h.getZ()), orphan);
        }

        return new ArrayList<>(groups.values());
    }

    /** 某个仓（我们的）所在方块上的 MTE id；取不到返回 -1。 */
    public static int hatchMetaIdOf(AdaptiveHatchHelper helper) {
        try {
            net.minecraft.world.WorldServer ws = net.minecraft.server.MinecraftServer.getServer()
                .worldServerForDimension(helper.getDim());
            if (ws == null) return -1;
            net.minecraft.tileentity.TileEntity te = ws.getTileEntity(helper.getX(), helper.getY(), helper.getZ());
            if (!(te instanceof IGregTechTileEntity)) return -1;
            gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            if (mte == null) return -1;
            net.minecraft.item.ItemStack stack = mte.getStackForm(1L);
            return stack == null ? -1 : stack.getItemDamage();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 主机控制器物品的 MTE id（= 物品 meta）；取不到返回 -1。 */
    public static int hostMetaIdOf(MTEMultiBlockBase host) {
        try {
            net.minecraft.item.ItemStack stack = host.getStackForm(1L);
            return stack == null ? -1 : stack.getItemDamage();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 这台主机此刻是否真的在运行。
     *
     * <p>
     * 判据是 GT 的 {@code MTEMultiBlockBase.mMaxProgresstime}（当前配方的剩余 tick，0 = 空闲）。
     * **为什么必须先问它**：GT 的 {@code mEUt} 在机器停机后**不清零**，会残留上一次配方的值——
     * 用户实测到"最上面那台根本没工作却写着 -458,752,000 EU/t"，而同一行的实测流量是 0。
     */
    public static boolean isRunning(MTEMultiBlockBase host) {
        try {
            return host != null && host.mMaxProgresstime > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final Map<Class<?>, Integer> TYPE_META = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 按仓类型查它的 MTE id（只查一次并缓存）。
     *
     * <p>
     * 用途：仓所在区块**没加载**（或主机认不出来）时，方块查不到、拿不到 MTE id，
     * 这一行就会既没名字也没图标。类型值是常量（我们的四个类），用它反查 METATILEENTITIES
     * 就能在这种"离线"情形下照样显示正确的仓名与图标 —— 信息不消失。
     */
    public static int hatchMetaIdForType(HatchType type) {
        if (type == null) return -1;
        Class<?> wanted;
        switch (type) {
            case ENERGY:
                wanted = AdaptiveNetHatch.class;
                break;
            case LASER_SOURCE:
                wanted = AdaptiveNetLaserHatch.class;
                break;
            case DYNAMO:
                wanted = AdaptiveNetDynamoHatch.class;
                break;
            case LASER_TARGET:
                wanted = AdaptiveNetLaserTargetHatch.class;
                break;
            default:
                return -1;
        }
        Integer cached = TYPE_META.get(wanted);
        if (cached != null) return cached;
        int found = -1;
        try {
            Object[] all = gregtech.api.GregTechAPI.METATILEENTITIES;
            for (int i = 0; i < all.length; i++) {
                if (all[i] != null && wanted.isInstance(all[i])) {
                    found = i;
                    break;
                }
            }
        } catch (Throwable t) {
            return -1;
        }
        if (found >= 0) TYPE_META.put(wanted, found);
        return found;
    }

    // ===== TecTech 兼容激光仓：安培写入（用户口径：纳入配置，接受覆盖）=====

    private static final Map<Class<?>, Method> SET_AMPERES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Field> MAX_AMPERES_FIELD = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 3.35.0-fix17（审查 P2-11）：反射**负缓存** + getter 缓存。
     * <p>
     * 旧实现的三个反射点都在周期性热路径上（{@code AdaptiveNetTerminal.onPreTick} 每 20 tick 一次）
     * 反复重付代价：
     * <ul>
     * <li>{@code ratedAmps}：类**没有** {@code maxAmperes} 字段时（普通 GT {@code MTEHatchEnergy} /
     * {@code MTEHatchDynamo} —— 白名单允许放进四个槽位），{@code getField} 每次都抛
     * {@code NoSuchFieldException}（构造异常要填栈，代价远高于一次 map 查询），且失败结果**从不缓存**
     * ⇒ 每个仓每 20 tick 重抛一次；</li>
     * <li>{@code getAmperes}：{@code getMethod("getAmperes")} **完全没有缓存**，每次调用都做一次方法查找；</li>
     * <li>{@code writeAmperes}：{@code setAmperes} 只缓存成功，失败同样每次重试。</li>
     * </ul>
     * 负缓存把"这个类没有该成员"也记下来——探测一次即永久跳过。
     * <p>
     * ⚠️ 只对**查找失败**（{@code NoSuchFieldException}/{@code NoSuchMethodException}）写负缓存；
     * 调用期失败（{@code f.get()}/{@code invoke()} 抛）**不写**——那可能是瞬时问题，下个周期应当重试，
     * 否则一次偶发异常会固化成永久禁用。
     */
    private static final java.util.Set<Class<?>> MAX_AMPERES_MISSING = java.util.concurrent.ConcurrentHashMap
        .newKeySet();
    private static final Map<Class<?>, Method> GET_AMPERES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Class<?>> GET_AMPERES_MISSING = java.util.concurrent.ConcurrentHashMap
        .newKeySet();
    private static final java.util.Set<Class<?>> SET_AMPERES_MISSING = java.util.concurrent.ConcurrentHashMap
        .newKeySet();

    /**
     * 外来兼容仓的**额定安培**（3.32.0-fix1）。
     *
     * <p>
     * 为什么不能直接用 {@code maxAmperesIn()/maxAmperesOut()}：TecTech 的能源类把它实现成
     * {@code Amperes + (Amperes >> 2)}，也就是 **×1.25**（字节码已核对）——「64安能源仓」会被读成 **80A**
     * （用户实测），「256安激光靶仓」会被读成 320A。而它们自己的评级字段 {@code public final int maxAmperes}
     * 才是"这个仓是多少安"。
     *
     * <p>
     * 读不到字段时退回方法值，再退回调用方给的兜底值；失败限频 WARN，不静默。
     */
    public static int ratedAmps(MetaTileEntity mte, boolean isOutput, int fallback) {
        if (mte == null) return fallback;
        Class<?> cls = mte.getClass();
        java.lang.reflect.Field f = MAX_AMPERES_FIELD.get(cls);
        if (f == null && !MAX_AMPERES_MISSING.contains(cls)) {
            try {
                f = cls.getField("maxAmperes");
                MAX_AMPERES_FIELD.put(cls, f);
            } catch (NoSuchFieldException e) {
                // 3.35.0-fix17：负缓存 —— 该类确认没有评级字段，此后直接走回退，不再付异常代价
                MAX_AMPERES_MISSING.add(cls);
                warnOnce(
                    "maxAmperes:" + cls.getName(),
                    "读不到评级字段 maxAmperes，退回 maxAmperesIn/Out（可能含 GT 的 ×1.25 派生）: " + cls.getName(),
                    null);
            } catch (Throwable t) {
                warnOnce("maxAmperes:" + cls.getName(), "探测评级字段 maxAmperes 失败: " + cls.getName(), null);
            }
        }
        if (f != null) {
            try {
                Object v = f.get(mte);
                if (v instanceof Integer && (Integer) v > 0) {
                    return (Integer) v;
                }
            } catch (Throwable ignored) {
                // 调用期失败不写负缓存：下个周期重试
            }
        }
        try {
            long v = isOutput ? mte.maxAmperesOut() : mte.maxAmperesIn();
            if (v > 0) return (int) Math.min(v, Integer.MAX_VALUE);
        } catch (Throwable ignored) {}
        return fallback;
    }

    /**
     * 把某一类激光仓的安培写给**本网络主机上的** TecTech 兼容仓。
     *
     * <p>
     * 为什么必须周期写：{@code MTEHatchEnergyMulti}/{@code MTEHatchDynamoMulti} 的
     * {@code Amperes} 字段**不进 NBT**（jar 字节码里没有 "Amperes" 键），区块重载后会回到构造默认值。
     * <p>
     * 为什么自己夹取：它们的 {@code setAmperes(int)} 是直接 {@code putfield}、**不做任何校验**，
     * 所以上限由我们按 {@code maxAmperesIn()/maxAmperesOut()} 夹。
     * <p>
     * 已知代价（用户已确认接受）：TecTech 自己的 GUI 也能改这个值，我们周期写入会覆盖手动设置。
     *
     * @return 实际写入的仓数量
     */
    public static void applyCompatAmps(UUID owner, int frequency, int sourceAmps, int targetAmps) {
        int wantedSource = Math.max(1, sourceAmps);
        int wantedTarget = Math.max(1, targetAmps);
        // 3.35.0-fix18（审查 P2-12，第 1 级·可证明等价的短路）：网络里没有任何**本模组**仓时，
        // scan() 内部本来就会把"只有兼容仓、没有本模组仓"的分组丢弃（`g.adaptive.isEmpty()` removeIf）
        // ⇒ 此分支下必然写不到任何东西。直接返回，省掉每秒一次的全量多方块扫描。
        AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(owner, frequency);
        if (network == null || network.getAllHelpers()
            .isEmpty()) {
            return;
        }
        // 3.35.0-fix18（第 2 级）：**发现与写入解耦** —— 兼容仓清单按 (owner,frequency) 缓存 5 秒
        // TTL；安培写入仍每秒执行（写到缓存清单上）。全量扫描 20 tick 一次 → 100 tick 一次；
        // 新激光仓插上最多 5 秒被发现（安培本来就要周期重写——不进 NBT——5 秒发现延迟无感知）。
        for (CompatEntry e : compatSnapshot(owner, frequency)) {
            if (e.isSource) {
                writeAmperes(e.mte, HatchType.LASER_SOURCE, wantedSource);
            } else {
                writeAmperes(e.mte, HatchType.LASER_TARGET, wantedTarget);
            }
        }
    }

    /** 3.35.0-fix18（P2-12）：一条兼容仓快照（mte + 槽位向）。 */
    private static final class CompatEntry {

        final MetaTileEntity mte;
        final boolean isSource;

        CompatEntry(MetaTileEntity mte, boolean isSource) {
            this.mte = mte;
            this.isSource = isSource;
        }
    }

    private static final class CompatCache {

        final long atMs;
        final List<CompatEntry> entries;

        CompatCache(long atMs, List<CompatEntry> entries) {
            this.atMs = atMs;
            this.entries = entries;
        }
    }

    /** 3.35.0-fix18（P2-12）：发现节流窗口。5 秒 = 安培重写周期的 5 倍，语义无感、扫描降 5 倍。 */
    private static final long COMPAT_SNAPSHOT_TTL_MS = 5_000L;
    /** 3.35.0-fix18（P2-12）：缓存有界（条目 = 活跃终端数 × 频率数，极端情况整体清空）。 */
    private static final int COMPAT_CACHE_MAX = 256;
    private static final Map<String, CompatCache> COMPAT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<CompatEntry> compatSnapshot(UUID owner, int frequency) {
        String key = (owner == null ? "null" : owner.toString()) + ':' + frequency;
        CompatCache cached = COMPAT_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < COMPAT_SNAPSHOT_TTL_MS) {
            return cached.entries;
        }
        List<CompatEntry> entries = new ArrayList<>();
        for (HostGroup group : scan(owner, frequency)) {
            for (MetaTileEntity mte : group.compat) {
                if (mte == null) continue;
                if (HatchType.isTecTechLaserSource(mte)) {
                    entries.add(new CompatEntry(mte, true));
                } else if (HatchType.isTecTechLaserTarget(mte)) {
                    entries.add(new CompatEntry(mte, false));
                }
            }
        }
        if (COMPAT_CACHE.size() >= COMPAT_CACHE_MAX && !COMPAT_CACHE.containsKey(key)) {
            COMPAT_CACHE.clear();
        }
        COMPAT_CACHE.put(key, new CompatCache(now, entries));
        return entries;
    }

    private static boolean writeAmperes(MetaTileEntity mte, HatchType type, int wanted) {
        Class<?> cls = mte.getClass();
        // 3.35.0-fix17（P2-11）：方法查找只做一次（成功入 GET 缓存，失败入负缓存）
        Method setter = SET_AMPERES.get(cls);
        if (setter == null) {
            if (SET_AMPERES_MISSING.contains(cls)) return false;
            try {
                setter = cls.getMethod("setAmperes", int.class);
                SET_AMPERES.put(cls, setter);
            } catch (NoSuchMethodException e) {
                SET_AMPERES_MISSING.add(cls);
                warnOnce(
                    "compatAmps:" + cls.getName(),
                    "给 TecTech 兼容激光仓写安培失败（该类可能没有 setAmperes）: " + cls.getName(),
                    null);
                return false;
            }
        }
        try {
            long cap = type == HatchType.LASER_SOURCE ? mte.maxAmperesIn() : mte.maxAmperesOut();
            if (cap <= 0) cap = wanted;
            int value = (int) Math.min(wanted, cap);
            if (getAmperes(mte) == value) return false;
            setter.invoke(mte, value);
            return true;
        } catch (Throwable t) {
            // 调用期失败不写负缓存（方法确实存在，可能是瞬时问题 ⇒ 下个周期应重试）
            warnOnce("compatAmpsWrite:" + cls.getName(), "给 TecTech 兼容激光仓写安培时调用失败: " + cls.getName(), t);
            return false;
        }
    }

    private static int getAmperes(MetaTileEntity mte) {
        Class<?> cls = mte.getClass();
        // 3.35.0-fix17（P2-11）：旧实现每次调用都 getMethod("getAmperes")（无缓存），
        // 而本方法在每个兼容仓每个写入周期都会被调用 ⇒ 方法查找被反复支付。
        Method getter = GET_AMPERES.get(cls);
        if (getter == null) {
            if (GET_AMPERES_MISSING.contains(cls)) return -1;
            try {
                getter = cls.getMethod("getAmperes");
                GET_AMPERES.put(cls, getter);
            } catch (NoSuchMethodException e) {
                GET_AMPERES_MISSING.add(cls);
                return -1;
            }
        }
        try {
            Object v = getter.invoke(mte);
            return v instanceof Integer ? (Integer) v : -1;
        } catch (Throwable t) {
            return -1;
        }
    }
}
