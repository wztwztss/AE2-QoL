package com.wztwzt.ae2_qof.hatch.adaptive;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public class AdaptiveHatchHelper {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");

    private UUID networkOwner;
    private int networkFrequency = 0;
    private int currentVoltageTier = 0;
    private int currentAmps = 1;
    private HatchType hatchType;
    private int posX, posY, posZ, posDim;
    private short cachedMetaId = -1;
    private String cachedName = "";
    private int machineMetaId = -1;
    private String machineName = "";
    private long realFlowEUt = 0;

    // WAILA缓存字段（服务端更新，客户端读取）
    private int lastVoltageTier = 0;
    private int lastAmps = 1;
    private long lastGridEU = 0L;
    /**
     * 3.33.0-fix3：电网余额的 BigInteger 快照（仅供 WAILA/GUI **显示**用）。
     * <p>
     * {@code lastGridEU} 是钳到 Long.MAX_VALUE 的 long 快照（统计/补电等算术路径仍用它，
     * 避免热路径付 BigInteger 代价）；但用户电网已实测 >9.2E —— 显示层若只看 long 快照
     * 就永远停在 9.22E。这里在 {@code setLastGridEU} 时顺带存一份精确值（客户端渲染读），
     * NBT 只持久化 long 版（显示缓存不跨存档，丢了下一 tick 就会刷新，无需精确持久化）。
     */
    private BigInteger lastGridEUBigInteger = BigInteger.ZERO;

    public HatchType getHatchType() {
        return hatchType;
    }

    public void setHatchType(HatchType hatchType) {
        this.hatchType = hatchType;
    }

    public UUID getNetworkOwner() {
        return networkOwner;
    }

    public int getNetworkFrequency() {
        return networkFrequency;
    }

    public int getCurrentVoltageTier() {
        return currentVoltageTier;
    }

    public int getCurrentAmps() {
        return currentAmps;
    }

    public void setVoltageTier(int tier) {
        this.currentVoltageTier = Math.max(0, Math.min(tier, 15));
        this.lastVoltageTier = this.currentVoltageTier;
    }

    public void setAmps(int amps) {
        this.currentAmps = Math.max(1, amps);
        this.lastAmps = this.currentAmps;
    }

    public int getX() {
        return posX;
    }

    public int getY() {
        return posY;
    }

    public int getZ() {
        return posZ;
    }

    public int getDim() {
        return posDim;
    }

    public void setPosition(int x, int y, int z, int dim) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.posDim = dim;
    }

    public short getCachedMetaId() {
        return cachedMetaId;
    }

    public String getCachedName() {
        return cachedName;
    }

    public void setCachedInfo(short metaId, String name) {
        this.cachedMetaId = metaId;
        this.cachedName = name != null ? name : "";
    }

    public int getMachineMetaId() {
        return machineMetaId;
    }

    public String getMachineName() {
        return machineName;
    }

    public void setMachineInfo(int metaId, String name) {
        this.machineMetaId = metaId;
        this.machineName = name != null ? name : "";
    }

    public static boolean isAdaptiveHatch(MetaTileEntity mte) {
        return mte instanceof AdaptiveNetHatch || mte instanceof AdaptiveNetDynamoHatch
            || mte instanceof AdaptiveNetLaserHatch
            || mte instanceof AdaptiveNetLaserTargetHatch;
    }

    /**
     * 识别仓室连接的多方块主机。
     * <p>
     * 只通过全局 {@link MultiblockRegistry} 查找多方块主机，按 center/radius 范围匹配。
     * 找不到主机时返回 {@code null}，由调用方显示仓室本身的名字和图标。
     * <p>
     * 不检查 {@code mMachine}：仓室 onFirstTick 可能早于主机的结构检查，此时 mMachine 仍为 false，
     * 会导致已注册的主机被跳过。注册表中的主机均为 MTEMultiBlockBase，未成型主机的 center/radius
     * 为默认值，通常不会误匹配。
     */
    public static MetaTileEntity findAttachedMachine(IGregTechTileEntity aBase) {
        net.minecraft.world.World world = aBase.getWorld();
        int x = aBase.getXCoord();
        int y = aBase.getYCoord();
        int z = aBase.getZCoord();
        int dim = world.provider.dimensionId;

        MultiblockRegistry.cleanupInvalid();
        MTEMultiBlockBase bestMultiblock = null;
        double bestDistSq = Double.MAX_VALUE;

        for (MTEMultiBlockBase multi : MultiblockRegistry.snapshot()) {
            IGregTechTileEntity mbBase = multi.getBaseMetaTileEntity();
            if (mbBase == null || mbBase.getWorld() == null || mbBase.getWorld().provider.dimensionId != dim) {
                continue;
            }

            double cx, cy, cz;
            int radius;
            if (multi instanceof MTEEnhancedMultiBlockBase) {
                MTEEnhancedMultiBlockBase<?> enhanced = (MTEEnhancedMultiBlockBase<?>) multi;
                Vector3f center = enhanced.getCenter();
                if (center != null) {
                    cx = center.x;
                    cy = center.y;
                    cz = center.z;
                    radius = enhanced.getApproximateRadius();
                } else {
                    cx = mbBase.getXCoord();
                    cy = mbBase.getYCoord();
                    cz = mbBase.getZCoord();
                    radius = 4;
                }
            } else {
                cx = mbBase.getXCoord();
                cy = mbBase.getYCoord();
                cz = mbBase.getZCoord();
                radius = 4;
            }

            double dx = cx - (x + 0.5);
            double dy = cy - (y + 0.5);
            double dz = cz - (z + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= (radius + 2) * (radius + 2) && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestMultiblock = multi;
            }
        }

        return bestMultiblock;
    }

    public long getRealFlowEUt() {
        return realFlowEUt;
    }

    public void setRealFlowEUt(long realFlowEUt) {
        this.realFlowEUt = realFlowEUt;
    }

    /**
     * 实测流量的采样窗口（tick）。
     *
     * <p>
     * 四个仓都在 {@code aTick % 4 == 0} 的节拍上采样一次，所以"一个窗口内的增量 ÷ 窗口"才是 **EU/t**。
     *
     * <p>
     * 3.32.0-fix4：旧实现把**窗口增量**直接当 EU/t 存进 {@link #realFlowEUt}，
     * 于是列表里的"实测"恰好放大 **4 倍**（用户实测：额定 4,194,304 却显示实测 16,777,216 = ×4）。
     */
    public static final int FLOW_WINDOW_TICKS = 4;

    /** 用"一个采样窗口内的增量"更新实测流量，内部换算成 **EU/t**（负数按 0 处理）。 */
    public void updateRealFlowEUt(long deltaInWindow) {
        this.realFlowEUt = Math.max(0L, deltaInWindow) / FLOW_WINDOW_TICKS;
    }

    public int getLastVoltageTier() {
        return lastVoltageTier;
    }

    public void setLastVoltageTier(int v) {
        this.lastVoltageTier = v;
    }

    public int getLastAmps() {
        return lastAmps;
    }

    public void setLastAmps(int a) {
        this.lastAmps = a;
    }

    public long getLastGridEU() {
        return lastGridEU;
    }

    /** 3.33.0-fix3：精确电网余额（显示专用；>9.22E 时与 long 快照不同）。 */
    public BigInteger getLastGridEUBigInteger() {
        return lastGridEUBigInteger;
    }

    public void setLastGridEU(long eu) {
        this.lastGridEU = eu;
    }

    /**
     * 3.33.0-fix3：与服务端读侧配套的精确写入。调用方（四个仓的 onPreTick）在
     * {@code setLastGridEU(long)} 之后调用它，把 BigInteger 快照一并带给客户端渲染。
     * <p>
     * 注意：helper 随 NBT 双端同步走的是自身字段序列化；BigInteger 不进 NBT
     * （显示缓存下一 tick 就会刷新，无需持久化），因此客户端首帧可能短暂看到
     * long 快照口径（9.22E 封顶），随后被本字段覆盖为精确值。
     */
    public void setLastGridEUBigInteger(BigInteger eu) {
        this.lastGridEUBigInteger = eu == null ? BigInteger.ZERO : eu;
    }

    public static long getGridEULong(UUID owner) {
        if (owner == null) return 0;
        BigInteger eu = gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        return eu.min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    public static BigInteger getGridEUBigInteger(UUID owner) {
        if (owner == null) return BigInteger.ZERO;
        return gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
    }

    public boolean isBound() {
        return networkOwner != null && networkFrequency >= 0;
    }

    public void bind(UUID owner, int frequency) {
        if (isBound()) {
            LOG.info(
                "[AE2QoL] Hatch rebind: oldOwner={}, oldFreq={} -> newOwner={}, newFreq={}",
                networkOwner,
                networkFrequency,
                owner,
                frequency);
            AdaptiveNetworkManager.unregisterHatch(this);
        } else {
            LOG.info("[AE2QoL] Hatch first bind: owner={}, freq={}, type={}", owner, frequency, hatchType);
        }
        this.networkOwner = owner;
        this.networkFrequency = frequency;
        AdaptiveNetworkManager.registerHatch(this);
    }

    public void unbind() {
        if (isBound()) {
            AdaptiveNetworkManager.unregisterHatch(this);
        }
        this.networkOwner = null;
        this.networkFrequency = 0;
        this.currentVoltageTier = 0;
    }

    public static EntityPlayerMP findPlayerByUUID(World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        for (Object obj : world.playerEntities) {
            if (obj instanceof EntityPlayerMP p) {
                if (uuid.equals(
                    p.getGameProfile()
                        .getId()))
                    return p;
            }
        }
        return null;
    }

    public boolean tryAutoBindFromPlacer(IGregTechTileEntity aBase) {
        if (isBound()) return false;
        UUID placerUuid = aBase.getOwnerUuid();
        if (placerUuid == null) return false;
        EntityPlayerMP player = findPlayerByUUID(aBase.getWorld(), placerUuid);
        if (player == null) return false;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemNetworkDataStick
                && ItemNetworkDataStick.hasData(stack)) {
                UUID owner = ItemNetworkDataStick.getOwner(stack);
                int freq = ItemNetworkDataStick.getFrequency(stack);
                if (owner != null) {
                    bind(owner, freq);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean handleDataStickRightClick(EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemNetworkDataStick)) {
            return false;
        }

        boolean hasData = ItemNetworkDataStick.hasData(heldItem);
        LOG.info("[AE2QoL] Hatch rightclick: player={}, hasData={}", aPlayer.getCommandSenderName(), hasData);

        if (hasData) {
            UUID owner = ItemNetworkDataStick.getOwner(heldItem);
            int freq = ItemNetworkDataStick.getFrequency(heldItem);
            LOG.info("[AE2QoL] Flash drive data: owner={}, freq={}", owner, freq);
            if (owner != null) {
                bind(owner, freq);
                if (!aPlayer.worldObj.isRemote) {
                    LOG.info("[AE2QoL] Hatch bound successfully: owner={}, freq={}, type={}", owner, freq, hatchType);
                    aPlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("ae2_qof.adaptive.bind.success")));
                }
            } else {
                if (!aPlayer.worldObj.isRemote) {
                    LOG.warn("[AE2QoL] Hatch bind failed: owner is null in flash drive");
                    aPlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.adaptive.bind.fail")));
                }
            }
            return true;
        } else {
            if (!aPlayer.worldObj.isRemote) {
                LOG.info("[AE2QoL] Empty flash drive used on hatch");
                aPlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.YELLOW
                            + StatCollector.translateToLocal("ae2_qof.adaptive.bind.empty_stick")));
            }
            return true;
        }
    }

    public void saveNBT(NBTTagCompound aNBT) {
        if (networkOwner != null) {
            aNBT.setString("ae2qolNO", networkOwner.toString());
        }
        aNBT.setInteger("ae2qolNF", networkFrequency);
        aNBT.setInteger("ae2qolVT", currentVoltageTier);
        aNBT.setInteger("ae2qolPX", posX);
        aNBT.setInteger("ae2qolPY", posY);
        aNBT.setInteger("ae2qolPZ", posZ);
        aNBT.setInteger("ae2qolPD", posDim);
        aNBT.setShort("ae2qolMI", cachedMetaId);
        aNBT.setString("ae2qolMN", cachedName);
        aNBT.setInteger("ae2qolMMI", machineMetaId);
        aNBT.setString("ae2qolMMN", machineName);
        aNBT.setInteger("ae2qolLVT", lastVoltageTier);
        aNBT.setInteger("ae2qolLA", lastAmps);
        aNBT.setLong("ae2qolLGE", lastGridEU);
    }

    public void loadNBT(NBTTagCompound aNBT) {
        String s = aNBT.getString("ae2qolNO");
        if (s != null && !s.isEmpty()) {
            try {
                networkOwner = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        }
        networkFrequency = aNBT.getInteger("ae2qolNF");
        currentVoltageTier = aNBT.getInteger("ae2qolVT");
        posX = aNBT.getInteger("ae2qolPX");
        posY = aNBT.getInteger("ae2qolPY");
        posZ = aNBT.getInteger("ae2qolPZ");
        posDim = aNBT.getInteger("ae2qolPD");
        cachedMetaId = aNBT.getShort("ae2qolMI");
        cachedName = aNBT.getString("ae2qolMN");
        if (cachedName == null) cachedName = "";
        machineMetaId = aNBT.getInteger("ae2qolMMI");
        machineName = aNBT.getString("ae2qolMMN");
        if (machineName == null) machineName = "";
        lastVoltageTier = aNBT.getInteger("ae2qolLVT");
        lastAmps = aNBT.getInteger("ae2qolLA");
        if (lastAmps <= 0) lastAmps = 1;
        lastGridEU = aNBT.getLong("ae2qolLGE");
    }

    public void migrateTo(UUID newOwner, int newFrequency) {
        if (isBound()) {
            AdaptiveNetworkManager.unregisterHatch(this);
        }
        this.networkOwner = newOwner;
        this.networkFrequency = newFrequency;
        AdaptiveNetworkManager.registerHatch(this);
    }

    // ==================== WAILA 数据通道（4.3.0-fix5） ====================

    /**
     * WAILA 同步 tag 的键。WAILA 的正确架构是：**服务端** {@code getWailaNBTData} 把动态值写进 tag
     * （每次请求约 250ms 一次），**客户端** {@code getWailaBody} 读 {@code accessor.getNBTData()}。
     * 旧实现四个仓只在客户端读自己 TE 的 helper 字段 —— 而 GT 的 MTE 没有 TE 描述包，客户端字段
     * 只在区块(重)加载时从 NBT 恢复一次 ⇒ WAILA 永远显示旧档/旧余额（用户实测"显示不正常"）。
     */
    public static final String WAILA_TIER = "ae2qolWT";
    public static final String WAILA_AMPS = "ae2qolWA";
    public static final String WAILA_GRID = "ae2qolWG";
    /** 电网余额超过 long 上限时的精确十进制串（BigInteger 不进 NBT 主存档，这里按需随查随写）。 */
    public static final String WAILA_GRID_EXACT = "ae2qolWGX";
    public static final String WAILA_BOUND = "ae2qolWB";

    /** 服务端：WAILA 请求时写 tag。档位/安培取**实时**配置值，电网余额按当前余额现算。 */
    public static void writeWailaNBT(net.minecraft.nbt.NBTTagCompound tag, java.util.UUID owner, int tier, int amps) {
        tag.setInteger(WAILA_TIER, Math.max(0, Math.min(tier, 15)));
        tag.setInteger(WAILA_AMPS, Math.max(1, amps));
        tag.setBoolean(WAILA_BOUND, owner != null);
        if (owner != null) {
            BigInteger exact = getGridEUBigInteger(owner);
            long clamped = exact.min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
            tag.setLong(WAILA_GRID, clamped);
            if (exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                tag.setString(WAILA_GRID_EXACT, exact.toString());
            }
        } else {
            tag.setLong(WAILA_GRID, 0L);
        }
    }

    /** 客户端：四个仓共用的 WAILA 行（V/A、Grid、绑定状态），全部读服务端同步 tag。 */
    public static void appendWailaBody(java.util.List<String> currenttip,
        mcp.mobius.waila.api.IWailaDataAccessor accessor) {
        net.minecraft.nbt.NBTTagCompound tag = accessor.getNBTData();
        int v = Math.max(0, Math.min(tag.getInteger(WAILA_TIER), 15));
        int a = Math.max(1, tag.getInteger(WAILA_AMPS));
        String tierName = gregtech.api.util.GTUtility.getColoredTierNameFromTier((byte) v);
        currenttip.add(
            net.minecraft.util.EnumChatFormatting.AQUA + "V:"
                + tierName
                + " ("
                + gregtech.api.enums.GTValues.V[v]
                + ") | A:"
                + a);
        currenttip.add(net.minecraft.util.EnumChatFormatting.AQUA + "Grid: " + formatGridEUFromTag(tag));
        if (tag.getBoolean(WAILA_BOUND)) {
            currenttip.add(
                net.minecraft.util.EnumChatFormatting.GREEN
                    + net.minecraft.util.StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
        }
    }

    /** Grid 行：超 long 上限用精确串格式化，否则按 long。 */
    private static String formatGridEUFromTag(net.minecraft.nbt.NBTTagCompound tag) {
        String exact = tag.hasKey(WAILA_GRID_EXACT) ? tag.getString(WAILA_GRID_EXACT) : null;
        if (exact != null && !exact.isEmpty()) {
            try {
                return formatEUExact(new BigInteger(exact));
            } catch (NumberFormatException ignored) {}
        }
        return formatEU(tag.getLong(WAILA_GRID));
    }

    /** 4.5.0-fix30：StringSyncValue 承载的十进制电网余额安全解析（空/坏串 → 0）。 */
    public static BigInteger parseEUString(String text) {
        if (text == null || text.isEmpty()) return BigInteger.ZERO;
        try {
            return new BigInteger(text.trim());
        } catch (NumberFormatException e) {
            return BigInteger.ZERO;
        }
    }

    /** long 电网余额的 K/M/G/T/P/E 缩写（与各仓 GUI 同口径）。 */
    public static String formatEU(long eu) {
        if (eu >= 1_000_000_000_000_000_000L) return String.format("%.2fE", eu / 1_000_000_000_000_000_000.0);
        if (eu >= 1_000_000_000_000_000L) return String.format("%.2fP", eu / 1_000_000_000_000_000.0);
        if (eu >= 1_000_000_000_000L) return String.format("%.2fT", eu / 1_000_000_000_000.0);
        if (eu >= 1_000_000_000L) return String.format("%.2fG", eu / 1_000_000_000.0);
        if (eu >= 1_000_000L) return String.format("%.2fM", eu / 1_000_000.0);
        if (eu >= 1_000L) return String.format("%.1fK", eu / 1_000.0);
        return String.valueOf(eu);
    }

    /** BigInteger 电网余额的精确缩写（>9.22E 时显示真实值）。 */
    public static String formatEUExact(BigInteger eu) {
        BigInteger E = BigInteger.valueOf(1_000_000_000_000_000_000L);
        BigInteger P = BigInteger.valueOf(1_000_000_000_000_000L);
        BigInteger T = BigInteger.valueOf(1_000_000_000_000L);
        BigInteger G = BigInteger.valueOf(1_000_000_000L);
        BigInteger M = BigInteger.valueOf(1_000_000L);
        BigInteger K = BigInteger.valueOf(1_000L);
        BigInteger abs = eu.abs();
        BigInteger whole;
        String suffix;
        double scale;
        if (abs.compareTo(E) >= 0) {
            whole = abs.divide(E);
            suffix = "E";
            scale = 1e18;
        } else if (abs.compareTo(P) >= 0) {
            whole = abs.divide(P);
            suffix = "P";
            scale = 1e15;
        } else if (abs.compareTo(T) >= 0) {
            whole = abs.divide(T);
            suffix = "T";
            scale = 1e12;
        } else if (abs.compareTo(G) >= 0) {
            whole = abs.divide(G);
            suffix = "G";
            scale = 1e9;
        } else if (abs.compareTo(M) >= 0) {
            whole = abs.divide(M);
            suffix = "M";
            scale = 1e6;
        } else if (abs.compareTo(K) >= 0) {
            whole = abs.divide(K);
            suffix = "K";
            scale = 1e3;
        } else {
            return (eu.signum() < 0 ? "-" : "") + abs;
        }
        double frac = abs.mod(BigInteger.valueOf((long) (scale / 100)))
            .doubleValue() / (scale / 100);
        return String.format("%s%.2f%s", eu.signum() < 0 ? "-" : "", whole.doubleValue() + frac / 100.0, suffix);
    }
}
