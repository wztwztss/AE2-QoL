package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;
import com.wztwzt.ae2_qof.network.HatchActionPacket;
import com.wztwzt.ae2_qof.network.HatchListSyncPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public class AdaptiveNetTerminal extends MTEHatch {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");
    private static final int SLOT_DYNAMO = 0;
    private static final int SLOT_ENERGY = 1;
    private static final int SLOT_LASER_SOURCE = 2;
    private static final int SLOT_LASER_TARGET = 3;
    /** 列表明细里 TecTech 兼容激光仓的类型标记（本模组四类是 0..3）。 */
    private static final int COMPAT_HATCH_TYPE = 4;
    private static final int REQUIRED_STACK_SIZE = 64;
    private static final int TERMINAL_TIER = 4;
    private static final int CONTENT_W = 330;
    /** 面板尺寸（3.32.0-fix3：给玩家背包留位置，并保证在 1080p + GUI 缩放 4 的约 270 高可用区里也放得下）。 */
    private static final int PANEL_W = 370;
    private static final int PANEL_H = 268;
    /**
     * MUI2 的 {@code bindPlayerInventory()} 把玩家背包（36 格 ≈ 76–80px）画在**面板内部的底部**，
     * 它不会自动扩大面板。我们的内容必须止于 {@code PANEL_H - INV_RESERVED}，
     * 否则背包会盖住列表尾部（用户实测截图：背包压在子仓列表下面几行上）。
     */
    private static final int INV_RESERVED = 80;
    /** 页签内容视口高度 = 面板高 − 内容起点 4 − 背包预留 80 − 底部留白 4。 */
    private static final int PAGE_H = PANEL_H - 4 - INV_RESERVED - 4;

    private UUID networkOwner;
    private int networkFrequency = 0;
    private boolean autoReconnect = true;
    private int displayMode = 0; // 0=regular, 1=scientific, 2=KMG
    private net.minecraft.world.World world;
    private int syncTick = 0;

    private int currentVoltageTier = 0;
    private int[] hatchTiers = new int[HatchType.COUNT];
    private int[] hatchAmps = new int[HatchType.COUNT];
    /**
     * 上一次重算时各槽位的物品指纹（3.32.0-fix1）。
     * 内容没变就不再重算——否则每 20 tick 都会用槽位物品的读数覆盖玩家手改的档位/安培。
     */
    private final String[] lastSlotSignature = new String[HatchType.COUNT];

    public AdaptiveNetTerminal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 4, new String[] { "Adaptive Net Terminal" });
    }

    public AdaptiveNetTerminal(String aName, int aTier, int aInvSlotCount, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDesc, aTextures);
    }

    @Override
    public boolean isFacingValid(net.minecraftforge.common.util.ForgeDirection facing) {
        return true;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.1"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.2"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof" };
    }

    public UUID getNetworkOwner() {
        return networkOwner;
    }

    public int getNetworkFrequency() {
        return networkFrequency;
    }

    public int getTargetVoltageTier() {
        return currentVoltageTier;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    private AdaptiveNetwork getCurrentNetwork() {
        if (networkOwner == null) return null;
        return AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
    }

    private void sendHatchListSync(IGregTechTileEntity aBase, AdaptiveNetwork network) {
        // 3.32.0：列表改成「一台主机一行」，主机归属走 AdaptiveHostIndex 的**权威反查**
        // （反向利用 GT 自己维护的仓清单），不再依赖仓 onFirstTick 的一次性解析。
        dropStaleHelpers(network);

        String ownerName = "";
        if (networkOwner != null) {
            net.minecraft.entity.player.EntityPlayerMP ownerPlayer = findPlayerByUUID(aBase.getWorld(), networkOwner);
            if (ownerPlayer != null) {
                ownerName = ownerPlayer.getCommandSenderName();
            } else {
                ownerName = networkOwner.toString()
                    .substring(0, 8);
            }
        }

        java.util.List<HatchListCache.HatchEntry> entries = new java.util.ArrayList<>();
        int globalIndex = 0;
        int totalIn = 0, totalOut = 0, hatchTotal = 0;

        for (AdaptiveHostIndex.HostGroup group : AdaptiveHostIndex.scan(networkOwner, networkFrequency)) {
            long ratedSum = 0L;
            long flowSum = 0L;
            int inCount = 0, outCount = 0;
            java.util.List<HatchListCache.Sub> subs = new java.util.ArrayList<>();

            // 本模组自己的仓（四类）
            for (AdaptiveHatchHelper h : group.adaptive) {
                HatchType ht = h.getHatchType();
                int tier = h.getCurrentVoltageTier();
                int amps = h.getCurrentAmps();
                long rated = ratedEut(ht, tier, amps);
                ratedSum += rated;
                flowSum += h.getRealFlowEUt();
                if (isInputType(ht)) {
                    inCount++;
                    totalIn++;
                } else {
                    outCount++;
                    totalOut++;
                }
                // 先按类型反查 MTE id（带缓存、不需要世界查询 ⇒ 区块没加载也能显示正确的仓名/图标），
                // 查不到才退回按坐标查那个方块
                int hatchMetaId = AdaptiveHostIndex.hatchMetaIdForType(ht);
                if (hatchMetaId < 0) hatchMetaId = AdaptiveHostIndex.hatchMetaIdOf(h);
                subs.add(
                    new HatchListCache.Sub(
                        hatchMetaId,
                        ht == null ? 0 : ht.ordinal(),
                        tier,
                        amps,
                        rated,
                        h.getRealFlowEUt(),
                        h.getX(),
                        h.getY(),
                        h.getZ(),
                        h.getDim(),
                        false));
            }

            // TecTech 兼容激光仓（用户口径：纳入统计与配置）。
            // 档位**写不了**（由物品本身决定），这里取它自己的档位、取不到才回落到本网络该类配置；
            // 安培取本网络该类配置，因为我们每 20 tick 会把它写进去（见 applyCompatAmps）。
            for (MetaTileEntity compat : group.compat) {
                boolean source = HatchType.isTecTechLaserSource(compat);
                HatchType ct = source ? HatchType.LASER_SOURCE : HatchType.LASER_TARGET;
                int tier = (int) (source ? compat.getInputTier() : compat.getOutputTier());
                if (tier < 0) tier = hatchTiers[ct.slotIndex];
                int amps = preferredCompatAmps(ct);
                long rated = ratedEut(ct, tier, amps);
                ratedSum += rated;
                if (source) {
                    inCount++;
                    totalIn++;
                } else {
                    outCount++;
                    totalOut++;
                }
                int cx = group.x, cy = group.y, cz = group.z, cdim = group.dim;
                IGregTechTileEntity cb = compat.getBaseMetaTileEntity();
                if (cb != null && cb.getWorld() != null) {
                    cx = cb.getXCoord();
                    cy = cb.getYCoord();
                    cz = cb.getZCoord();
                    cdim = cb.getWorld().provider.dimensionId;
                }
                subs.add(
                    new HatchListCache.Sub(
                        metaIdOf(compat),
                        COMPAT_HATCH_TYPE,
                        tier,
                        amps,
                        rated,
                        0L,
                        cx,
                        cy,
                        cz,
                        cdim,
                        true));
            }

            hatchTotal += subs.size();
            // 名称/图标只发 MTE id，由客户端本地化解析（专用服上服务端字符串必是英文）
            int displayMetaId = group.hostKnown() ? group.hostMetaId : firstSubMetaId(subs);
            entries.add(
                new HatchListCache.HatchEntry(
                    displayMetaId,
                    group.hostKnown(),
                    group.running,
                    group.hostEut(),
                    subs.size(),
                    inCount,
                    outCount,
                    ratedSum,
                    flowSum,
                    globalIndex++,
                    group.x,
                    group.y,
                    group.z,
                    group.dim,
                    ownerName,
                    subs));
        }

        HatchListSyncPacket packet = new HatchListSyncPacket(
            networkOwner,
            networkFrequency,
            hatchTotal,
            entries.size(),
            entries,
            totalIn,
            totalOut);
        for (UUID viewerUUID : network.getActiveViewers()) {
            net.minecraft.entity.player.EntityPlayerMP viewer = findPlayerByUUID(aBase.getWorld(), viewerUUID);
            if (viewer != null) {
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendTo(packet, viewer);
            }
        }
    }

    /** 输入类：能源仓 + 激光源仓；输出类：动力仓 + 激光靶仓。 */
    private static boolean isInputType(HatchType ht) {
        return ht == HatchType.ENERGY || ht == HatchType.LASER_SOURCE;
    }

    /** 额定功率（沿用旧口径：{@code V[tier] × 安培}，激光类 ×2）。 */
    private static long ratedEut(HatchType ht, int tier, int amps) {
        if (ht == null || tier < 0 || tier >= V.length) return 0L;
        long mult = (ht == HatchType.LASER_SOURCE || ht == HatchType.LASER_TARGET) ? 2L : 1L;
        return V[tier] * mult * Math.max(1, amps);
    }

    /** 兼容激光仓要写/要显示的安培：本网络该类配置；为 0 时回落该类默认（激光 256A）。 */
    private int preferredCompatAmps(HatchType type) {
        int v = hatchAmps[type.slotIndex];
        return v > 0 ? v : type.defaultAmps;
    }

    private static int metaIdOf(MetaTileEntity mte) {
        try {
            ItemStack stack = mte.getStackForm(1L);
            return stack == null ? -1 : stack.getItemDamage();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int firstSubMetaId(java.util.List<HatchListCache.Sub> subs) {
        for (HatchListCache.Sub s : subs) {
            if (s.metaId >= 0) return s.metaId;
        }
        return -1;
    }

    /**
     * 清理"确定已经不在了"的仓。
     *
     * <p>
     * 3.32.0 修正两处旧判据的坑：
     * <ol>
     * <li><b>未加载区块不等于被拆除</b>：旧实现只判 {@code getTileEntity(...) == null}，
     * 于是远方未加载区块里的仓会被直接注销（"漏仓"的来源之一）；</li>
     * <li><b>不校验身份</b>：旧实现不看那个位置现在还是不是我们的仓 ⇒ 拆掉换别的机器后仍留着幽灵条目
     * （"多仓"的来源之一）。</li>
     * </ol>
     * 现在的判据：维度/区块没加载 → 跳过；已加载但方块已不是本模组的自适应仓 → 才注销，并留一行 INFO。
     */
    private void dropStaleHelpers(AdaptiveNetwork network) {
        java.util.List<AdaptiveHatchHelper> stale = new java.util.ArrayList<>();
        for (AdaptiveHatchHelper h : network.getAllHelpers()) {
            try {
                WorldServer ws = MinecraftServer.getServer()
                    .worldServerForDimension(h.getDim());
                if (ws == null) continue;
                if (!ws.blockExists(h.getX(), h.getY(), h.getZ())) continue;
                TileEntity te = ws.getTileEntity(h.getX(), h.getY(), h.getZ());
                if (!(te instanceof IGregTechTileEntity)) {
                    stale.add(h);
                    continue;
                }
                IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                if (!(mte instanceof MetaTileEntity) || !AdaptiveHatchHelper.isAdaptiveHatch((MetaTileEntity) mte)) {
                    stale.add(h);
                }
            } catch (Throwable t) {
                // 不静默：否则"仓莫名从列表消失"无法定性
                LOG.warn("[AE2QoL] 清理陈旧仓时出错 pos={},{},{} dim={}", h.getX(), h.getY(), h.getZ(), h.getDim(), t);
            }
        }
        for (AdaptiveHatchHelper s : stale) {
            LOG.info("[AE2QoL] 注销已不存在的自适应仓 pos={},{},{} dim={}", s.getX(), s.getY(), s.getZ(), s.getDim());
            AdaptiveNetworkManager.unregisterHatch(s);
        }
    }

    /**
     * 从四个槽位重算"该类仓的档位/安培"。
     *
     * <h2>3.32.0-fix1 的两处修正（用户实测：放 1A 识别成 2A、放 64A 识别成 80A、机械外壳也被识别）</h2>
     * <ol>
     * <li><b>不再从外来仓读 {@code maxAmperesIn/Out()}</b>：那不是"这个仓是多少安"——
     * GT 标准能源仓硬编码返回 **2**（所以"1A"变 2A）；TecTech 能源类返回
     * {@code Amperes + (Amperes >> 2)}（**×1.25**，64安→**80A**，字节码已核对）。
     * 现在：本模组自己的四类一律取 {@link HatchType#defaultTier}/{@link HatchType#defaultAmps}
     * （它们的 {@code maxAmperes*} 返回的是**当前配置值**，读原型实例只会得到出厂初值）；
     * TecTech 兼容类取物品档位 + **评级字段** {@code maxAmperes}（见 {@link AdaptiveHostIndex#ratedAmps}）。</li>
     * <li><b>只在槽位内容变化时重算</b>：旧实现每 20 tick 无条件重算并 {@code applySettings()}，
     * 会把玩家在终端上手动改的档位/安培按槽位物品的读数**打回**（手改等于不生效）。</li>
     * </ol>
     */
    private void updateParamsFromSlots() {
        // ① 槽位内容没变就直接返回（保住玩家手改的档位/安培）
        boolean slotsChanged = false;
        for (int i = 0; i < HatchType.COUNT; i++) {
            String sig = slotSignature(mInventory[i]);
            if (!sig.equals(lastSlotSignature[i])) {
                lastSlotSignature[i] = sig;
                slotsChanged = true;
            }
        }
        if (!slotsChanged) return;

        boolean anyValid = false;
        int bestTier = 0;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = mInventory[i];
            HatchType ht = HatchType.fromSlotIndex(i);
            if (stack != null && stack.stackSize >= REQUIRED_STACK_SIZE && ht != null) {
                int damage = stack.getItemDamage();
                IMetaTileEntity imte = GregTechAPI.METATILEENTITIES[damage];
                if (imte instanceof MetaTileEntity) {
                    MetaTileEntity mte = (MetaTileEntity) imte;
                    if (!ht.isValidMTEType(mte)) {
                        // 机械外壳、GT 普通能源/动力仓等一切非认类物品：拒收，不把任何规格写进网络
                        hatchTiers[i] = 0;
                        hatchAmps[i] = 0;
                        continue;
                    }
                    boolean isOutput = (ht == HatchType.DYNAMO || ht == HatchType.LASER_TARGET);
                    if (ht.isOwnClass(mte)) {
                        // 本模组自己的仓：规格=该类的出厂额定（不读实例——实例返回的是当前配置值）
                        hatchTiers[i] = ht.defaultTier;
                        hatchAmps[i] = ht.defaultAmps;
                        if (ht.defaultTier > bestTier) bestTier = ht.defaultTier;
                    } else {
                        // TecTech 兼容仓：档位由物品决定；安培取评级字段（避开 ×1.25 派生值）
                        int tier = (int) (isOutput ? mte.getOutputTier() : mte.getInputTier());
                        hatchTiers[i] = (tier >= 0) ? tier : ht.defaultTier;
                        if (tier > bestTier) bestTier = tier;
                        hatchAmps[i] = AdaptiveHostIndex.ratedAmps(mte, isOutput, ht.defaultAmps);
                    }
                    anyValid = true;
                } else {
                    hatchTiers[i] = ht.defaultTier;
                    hatchAmps[i] = ht.defaultAmps;
                    if (ht.defaultTier > bestTier) bestTier = ht.defaultTier;
                }
            } else {
                hatchTiers[i] = 0;
                hatchAmps[i] = 0;
            }
        }

        currentVoltageTier = bestTier;
        applySettings();
    }

    /** 槽位物品指纹（注册名:meta:数量）；空槽为空串。 */
    private static String slotSignature(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        Object regName = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        return (regName == null ? "?" : regName.toString()) + ":" + stack.getItemDamage() + ":" + stack.stackSize;
    }

    /**
     * P1-014：判断当前玩家是否允许修改本终端配置。
     * 语义与数据棒/传送一致：终端所有者所属团队的成员可写，其他玩家只能查看。
     */
    private boolean canModify(EntityPlayer player) {
        if (networkOwner == null) return true;
        if (player == null) return false;
        try {
            return AdaptiveTeamHelper.isMemberOf(player.getUniqueID(), networkOwner);
        } catch (Throwable t) {
            return false;
        }
    }

    /** P1-014：仓档位写入（团队鉴权 + 范围钳制）。 */
    private void setHatchTierSafe(PanelSyncManager syncManager, int index, int v) {
        if (index < 0 || index >= HatchType.COUNT) return;
        if (!canModify(syncManager.getPlayer())) return;
        hatchTiers[index] = Math.max(0, Math.min(15, v));
        applySettings();
    }

    /** P1-014：仓电流写入（团队鉴权 + 范围钳制）。 */
    private void setHatchAmpsSafe(PanelSyncManager syncManager, int index, int v) {
        if (index < 0 || index >= HatchType.COUNT) return;
        if (!canModify(syncManager.getPlayer())) return;
        hatchAmps[index] = Math.max(1, v);
        applySettings();
    }

    public void applySettings() {
        if (networkOwner != null) {
            AdaptiveNetwork network = AdaptiveNetworkManager.getOrCreateNetwork(networkOwner, networkFrequency);
            network.setVoltageTier(currentVoltageTier);
            network.setAutoReconnect(autoReconnect);
            for (HatchType type : HatchType.values()) {
                network.setHatchTier(type, hatchTiers[type.slotIndex]);
                network.setHatchAmps(type, hatchAmps[type.slotIndex]);
            }
            if (autoReconnect) {
                network.updateAllHelpers();
            }
        }
    }

    /**
     * 把"激光源仓 / 激光靶仓"两类的安培写给主机上的 TecTech 兼容仓。
     *
     * <p>
     * 用户口径（本轮）：TecTech 激光仓**纳入配置**，并**接受覆盖**——我们周期写入会盖掉玩家在
     * TecTech 自己 GUI 里的手动安培设置（已明确接受）。作用范围只限"已接入我们电网的主机"
     * （{@link AdaptiveHostIndex#scan} 已按此过滤）。档位写不了：它由物品本身决定。
     */
    private void applyCompatAmpsTick() {
        if (networkOwner == null) return;
        try {
            AdaptiveHostIndex.applyCompatAmps(
                networkOwner,
                networkFrequency,
                preferredCompatAmps(HatchType.LASER_SOURCE),
                preferredCompatAmps(HatchType.LASER_TARGET));
        } catch (Throwable t) {
            LOG.warn("[AE2QoL] 兼容激光仓安培写入失败", t);
        }
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBase, long aTick) {
        super.onPreTick(aBase, aTick);
        if (aBase.isServerSide() && aTick % 20 == 0) {
            updateParamsFromSlots();
            // 3.32.0：TecTech 兼容激光仓的 Amperes 不进 NBT ⇒ 必须周期重写才保持生效
            applyCompatAmpsTick();
        }
        if (aBase.isServerSide() && networkOwner != null) {
            AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
            if (network != null) {
                // 3.35.0-fix23：统计在 BigInteger 域推进（数据源 = fix3 的精确电网余额；
                // 旧 getGridEULong 的 9.22E 钳制会让变化/预测行在超限电网下全程失真）
                network.tickStats(AdaptiveHatchHelper.getGridEUBigInteger(networkOwner));
                syncTick++;
                if (syncTick >= 20) {
                    syncTick = 0;
                    if (network.isHatchListDirty()) {
                        network.clearHatchListDirty();
                        sendHatchListSync(aBase, network);
                    }
                }
            }
        }
        if (aBase.isServerSide()) {
            com.wztwzt.ae2_qof.network.HatchActionPacket.tickPendingClears();
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBase) {
        super.onFirstTick(aBase);
        if (aBase.isServerSide()) {
            this.world = aBase.getWorld();
            networkOwner = AdaptiveTeamHelper.resolveLeader(aBase.getOwnerUuid());
            if (networkOwner != null) {
                AdaptiveNetworkManager.registerTerminal(this, world);
            }
        }
    }

    @Override
    public void onRemoval() {
        if (networkOwner != null) {
            AdaptiveNetworkManager.unregisterTerminal(this);
        }
        super.onRemoval();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();

        if (heldItem != null && heldItem.getItem() instanceof ItemNetworkDataStick) {
            boolean hasData = ItemNetworkDataStick.hasData(heldItem);
            if (!aPlayer.worldObj.isRemote) {
                LOG.debug(
                    "[AE2QoL] Terminal rightclick: player={}, hasData={}",
                    aPlayer.getCommandSenderName(),
                    hasData);
            }

            if (hasData) {
                // 有数据的闪存 → 读取配置到终端
                UUID stickOwner = ItemNetworkDataStick.getOwner(heldItem);
                int stickFreq = ItemNetworkDataStick.getFrequency(heldItem);
                if (!aPlayer.worldObj.isRemote) {
                    LOG.debug("[AE2QoL] Reading flash drive: owner={}, freq={}", stickOwner, stickFreq);
                    if (stickOwner == null) {
                        LOG.warn("[AE2QoL] Flash drive owner is null!");
                        aPlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                EnumChatFormatting.RED
                                    + StatCollector.translateToLocal("ae2_qof.adaptive.bind.invalid_stick")));
                        return true;
                    }
                    // P1-013：必须先按「旧 owner + 旧频率」解析并迁移/注销网络，再更新字段，
                    // 否则注销会按新键查找，旧网络引用与仓室绑定会残留在旧频率上。
                    java.util.UUID oldOwner = networkOwner;
                    int oldFreq = networkFrequency;
                    if (oldOwner != null && (oldFreq != stickFreq || !oldOwner.equals(stickOwner))) {
                        AdaptiveNetworkManager.migrateHatches(oldOwner, oldFreq, stickOwner, stickFreq);
                        AdaptiveNetworkManager.unregisterTerminal(this);
                    }
                    networkOwner = stickOwner;
                    networkFrequency = stickFreq;
                    LOG.debug(
                        "[AE2QoL] Terminal updated: oldFreq={} -> newFreq={}, owner={}",
                        oldFreq,
                        stickFreq,
                        stickOwner);
                    AdaptiveNetworkManager.registerTerminal(this, world);
                    applySettings();
                    aPlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.GREEN + StatCollector.translateToLocal("ae2_qof.data_stick.read.success")
                                + " F:"
                                + stickFreq));
                }
                return true;
            } else {
                // 空闪存 → 写入终端配置到闪存
                if (!aPlayer.worldObj.isRemote) {
                    if (networkOwner == null) {
                        networkOwner = aPlayer.getUniqueID();
                        AdaptiveNetworkManager.registerTerminal(this, world);
                        LOG.debug("[AE2QoL] Auto-initialized owner from player: {}", networkOwner);
                    }
                    LOG.debug("[AE2QoL] Writing to flash drive: owner={}, freq={}", networkOwner, networkFrequency);
                    ItemNetworkDataStick.writeData(heldItem, networkOwner, networkFrequency);
                    aPlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("ae2_qof.data_stick.write.success")));
                }
                return true;
            }
        }

        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        if (guiData != null && guiData.getPlayer() != null && networkOwner != null) {
            UUID viewerUUID = guiData.getPlayer()
                .getUniqueID();
            AdaptiveNetwork net = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
            // P1-014：只有团队成员才登记为查看者，未授权玩家无法借由本会话触发高亮/传送。
            if (net != null && canModify(guiData.getPlayer())) {
                net.addViewer(viewerUUID);
            }
        }

        IntSyncValue frequencySync = new IntSyncValue(() -> networkFrequency, v -> {
            if (!canModify(syncManager.getPlayer())) return;
            v = Math.max(0, v);
            if (v != networkFrequency) {
                // P1-013：先按旧频率完成迁移与注销，再写入新频率并重新注册。
                int oldFreq = networkFrequency;
                if (networkOwner != null) {
                    AdaptiveNetworkManager.migrateHatches(networkOwner, oldFreq, networkOwner, v);
                    AdaptiveNetworkManager.unregisterTerminal(this);
                }
                networkFrequency = v;
                if (networkOwner != null) {
                    AdaptiveNetworkManager.registerTerminal(this, world);
                    applySettings();
                }
            }
        }).allowC2S();

        IntSyncValue voltageTierSync = new IntSyncValue(() -> currentVoltageTier, v -> {
            if (!canModify(syncManager.getPlayer())) return;
            currentVoltageTier = Math.max(0, Math.min(15, v));
            applySettings();
        }).allowC2S();

        // P1-014：仓档位/电流写入回调统一做团队鉴权与范围钳制（档位 0..15，电流 1..Integer.MAX_VALUE）。
        IntSyncValue hatchTier0Sync = new IntSyncValue(() -> hatchTiers[0], v -> setHatchTierSafe(syncManager, 0, v));
        IntSyncValue hatchTier1Sync = new IntSyncValue(() -> hatchTiers[1], v -> setHatchTierSafe(syncManager, 1, v));
        IntSyncValue hatchTier2Sync = new IntSyncValue(() -> hatchTiers[2], v -> setHatchTierSafe(syncManager, 2, v));
        IntSyncValue hatchTier3Sync = new IntSyncValue(() -> hatchTiers[3], v -> setHatchTierSafe(syncManager, 3, v));
        IntSyncValue hatchAmp0Sync = new IntSyncValue(() -> hatchAmps[0], v -> setHatchAmpsSafe(syncManager, 0, v));
        IntSyncValue hatchAmp1Sync = new IntSyncValue(() -> hatchAmps[1], v -> setHatchAmpsSafe(syncManager, 1, v));
        IntSyncValue hatchAmp2Sync = new IntSyncValue(() -> hatchAmps[2], v -> setHatchAmpsSafe(syncManager, 2, v));
        IntSyncValue hatchAmp3Sync = new IntSyncValue(() -> hatchAmps[3], v -> setHatchAmpsSafe(syncManager, 3, v));

        IntSyncValue[] hatchTierSyncs = { hatchTier0Sync, hatchTier1Sync, hatchTier2Sync, hatchTier3Sync };
        IntSyncValue[] hatchAmpSyncs = { hatchAmp0Sync, hatchAmp1Sync, hatchAmp2Sync, hatchAmp3Sync };

        IntSyncValue[] hatchCountSyncs = new IntSyncValue[HatchType.COUNT];
        for (int i = 0; i < HatchType.COUNT; i++) {
            final HatchType ht = HatchType.values()[i];
            hatchCountSyncs[i] = new IntSyncValue(() -> {
                if (networkOwner == null) return 0;
                AdaptiveNetwork n = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
                return n != null ? n.getHatchCount(ht) : 0;
            }, v -> {});
        }

        // 3.35.0-fix23（用户实测：监控页统计超 9.2E 截断）：统计值经 **StringSyncValue 承载
        // 十进制串**（BigInteger 无原生 sync 类型）——客户端按串解析后再按 displayMode 格式化。
        // 数据源全部改为 NetworkStats 的 BigInteger 接口；旧 Long/getGridEULong 链退役。
        StringSyncValue totalInputSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            return n == null ? "0"
                : n.getStats()
                    .getTotalInput()
                    .toString();
        }, v -> {});
        StringSyncValue totalOutputSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            return n == null ? "0"
                : n.getStats()
                    .getTotalOutput()
                    .toString();
        }, v -> {});
        StringSyncValue instantInputSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            return n == null ? "0"
                : n.getStats()
                    .getInstantInputRate()
                    .toString();
        }, v -> {});
        StringSyncValue instantOutputSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            return n == null ? "0"
                : n.getStats()
                    .getInstantOutputRate()
                    .toString();
        }, v -> {});

        syncManager.syncValue("wFr", frequencySync);
        syncManager.syncValue("wVT", voltageTierSync);
        syncManager.syncValue("wTI", totalInputSync);
        syncManager.syncValue("wTO", totalOutputSync);
        syncManager.syncValue("wII", instantInputSync);
        syncManager.syncValue("wIO", instantOutputSync);
        for (int i = 0; i < 4; i++) {
            syncManager.syncValue("wHT" + i, hatchTierSyncs[i]);
            syncManager.syncValue("wHA" + i, hatchAmpSyncs[i]);
            syncManager.syncValue("wHC" + i, hatchCountSyncs[i]);
        }

        final LongSyncValue gridEUSync = new LongSyncValue(() -> {
            if (networkOwner == null) return 0;
            return AdaptiveHatchHelper.getGridEULong(networkOwner);
        }, v -> {});
        syncManager.syncValue("wGE", gridEUSync);

        final StringSyncValue ownerSync = new StringSyncValue(
            () -> networkOwner != null ? networkOwner.toString() : "",
            v -> {});
        syncManager.syncValue("wNO", ownerSync);

        final StringSyncValue ownerNameSync = new StringSyncValue(() -> {
            if (networkOwner == null) return "";
            if (world == null) return networkOwner.toString()
                .substring(
                    0,
                    Math.min(
                        8,
                        networkOwner.toString()
                            .length()));
            net.minecraft.entity.player.EntityPlayerMP p = findPlayerByUUID(world, networkOwner);
            if (p != null) return p.getCommandSenderName();
            return networkOwner.toString()
                .substring(
                    0,
                    Math.min(
                        8,
                        networkOwner.toString()
                            .length()));
        }, v -> {});
        syncManager.syncValue("wNM", ownerNameSync);

        // 3.35.0-fix23：变化/平均速率改用 **BigInteger 电网余额**做窗口比较源——
        // 旧链先经 getGridEULong 钳制，电网超 9.22E 后两个快照相等 ⇒ 变化恒 0（用户实测的"截断"）。
        final StringSyncValue change1hSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return "0";
            return n.getStats()
                .getChange1h(bigGrid(networkOwner))
                .toString();
        }, v -> {});
        syncManager.syncValue("wC1", change1hSync);

        final StringSyncValue change10mSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return "0";
            return n.getStats()
                .getChange10min(bigGrid(networkOwner))
                .toString();
        }, v -> {});
        syncManager.syncValue("wCM", change10mSync);

        final StringSyncValue avgOut10mSync = new StringSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return "0";
            return n.getStats()
                .getAvgOutputRate10min(bigGrid(networkOwner))
                .toString();
        }, v -> {});
        syncManager.syncValue("wAO", avgOut10mSync);

        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ModularPanel panel = ModularPanel.defaultPanel("adaptive_net_terminal", PANEL_W, PANEL_H);

        int TAB_W = 36;
        int TAB_H = 28;
        int CONTENT_X = TAB_W;
        int CONTENT_W = 370 - CONTENT_X - 4;

        Flow tabStrip = Flow.column()
            .pos(0, 4)
            .width(TAB_W)
            .childPadding(0);
        tabStrip.child(
            new PageButton(0, tabController).tab(GuiTextures.TAB_LEFT, -1)
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.status")))
                .size(TAB_W, TAB_H));
        tabStrip.child(
            new PageButton(1, tabController).tab(GuiTextures.TAB_LEFT, 0)
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.settings")))
                .size(TAB_W, TAB_H));
        tabStrip.child(
            new PageButton(2, tabController).tab(GuiTextures.TAB_LEFT, 0)
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.frequency")))
                .size(TAB_W, TAB_H));
        tabStrip.child(
            new PageButton(3, tabController).tab(GuiTextures.TAB_LEFT, 0)
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.monitor")))
                .size(TAB_W, TAB_H));
        tabStrip.child(
            new PageButton(4, tabController).tab(GuiTextures.TAB_LEFT, 3)
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.hatch_list")))
                .size(TAB_W, TAB_H));
        panel.child(tabStrip);

        PagedWidget<?> pages = new PagedWidget<>().controller(tabController)
            .pos(CONTENT_X, 4)
            .size(CONTENT_W, PAGE_H)
            .addPage(
                buildStatusTab(
                    frequencySync,
                    voltageTierSync,
                    hatchTierSyncs,
                    hatchAmpSyncs,
                    hatchCountSyncs,
                    ownerNameSync))
            .addPage(buildSettingsTab(hatchTierSyncs, hatchAmpSyncs))
            .addPage(buildFrequencyTab(frequencySync))
            .addPage(
                buildMonitorTab(
                    gridEUSync,
                    change1hSync,
                    change10mSync,
                    avgOut10mSync,
                    instantInputSync,
                    instantOutputSync))
            .addPage(buildHatchListTab(ownerSync, frequencySync, syncManager));
        panel.child(pages);

        panel.bindPlayerInventory();

        if (guiData != null && guiData.getPlayer() != null && networkOwner != null) {
            UUID viewerUUID = guiData.getPlayer()
                .getUniqueID();
            panel.onCloseAction(() -> {
                AdaptiveNetwork net = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
                if (net != null) {
                    net.removeViewer(viewerUUID);
                }
            });
        }

        return panel;
    }

    private Flow buildStatusTab(IntSyncValue frequencySync, IntSyncValue voltageTierSync, IntSyncValue[] hatchTierSyncs,
        IntSyncValue[] hatchAmpSyncs, IntSyncValue[] hatchCountSyncs, StringSyncValue ownerNameSync) {
        Flow tab = Flow.column()
            .size(CONTENT_W, 0)
            .childPadding(4);

        tab.child(
            Flow.row()
                .size(CONTENT_W, 16)
                .childPadding(2)
                .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.title")).size(150, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    return EnumChatFormatting.AQUA
                        + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.frequency")
                        + ": "
                        + EnumChatFormatting.WHITE
                        + frequencySync.getIntValue();
                })).size(CONTENT_W - 154, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));
        tab.child(separator(CONTENT_W));

        tab.child(new TextWidget<>(IKey.dynamic(() -> {
            boolean bound = frequencySync.getIntValue() >= 0;
            if (!bound) {
                return EnumChatFormatting.RED + "\u25cf "
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.no_owner");
            }
            String ownerDisplay = ownerNameSync.getValue();
            if (ownerDisplay == null || ownerDisplay.isEmpty()) ownerDisplay = "---";
            return EnumChatFormatting.GREEN + "\u25cf "
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.owner")
                + ": "
                + EnumChatFormatting.WHITE
                + ownerDisplay;
        })).size(CONTENT_W, 14));

        tab.child(separator(CONTENT_W));

        String[] labels = { "ae2_qof.gui.adaptive_terminal.dynamo_hatch", "ae2_qof.gui.adaptive_terminal.energy_hatch",
            "ae2_qof.gui.adaptive_terminal.laser_source", "ae2_qof.gui.adaptive_terminal.laser_target" };

        for (HatchType type : HatchType.values()) {
            final HatchType ft = type;
            final int idx = ft.slotIndex;
            tab.child(new TextWidget<>(IKey.dynamic(() -> {
                int loaded = hatchCountSyncs[idx].getIntValue();
                int tier = hatchTierSyncs[idx].getIntValue();
                int amps = hatchAmpSyncs[idx].getIntValue();
                String loadedStr = (loaded > 0) ? EnumChatFormatting.GREEN + String.valueOf(loaded)
                    : EnumChatFormatting.YELLOW + "0";
                if (amps > 0 && tier >= 0) {
                    String tierName = GTUtility.getColoredTierNameFromTier((byte) tier);
                    return EnumChatFormatting.WHITE + StatCollector.translateToLocal(labels[idx])
                        + ": "
                        + tierName
                        + " ("
                        + EnumChatFormatting.GOLD
                        + V[tier]
                        + EnumChatFormatting.WHITE
                        + ")"
                        + " "
                        + EnumChatFormatting.GREEN
                        + amps
                        + "A"
                        + " | "
                        + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.loaded")
                        + ": "
                        + loadedStr;
                }
                return EnumChatFormatting.WHITE + StatCollector.translateToLocal(labels[idx])
                    + ": "
                    + EnumChatFormatting.YELLOW
                    + "ULV 0V 0A"
                    + " | "
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.loaded")
                    + ": "
                    + loadedStr;
            })).size(CONTENT_W, 14));
        }

        return tab;
    }

    private Flow buildSettingsTab(IntSyncValue[] hatchTierSyncs, IntSyncValue[] hatchAmpSyncs) {
        Flow tab = Flow.column()
            .coverChildren()
            .childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.title")).size(CONTENT_W, 16));
        tab.child(separator(CONTENT_W));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.slots")).size(CONTENT_W, 14));

        String[] slotLabels = { "ae2_qof.gui.adaptive_terminal.dynamo_slot",
            "ae2_qof.gui.adaptive_terminal.energy_slot", "ae2_qof.gui.adaptive_terminal.laser_source_slot",
            "ae2_qof.gui.adaptive_terminal.laser_target_slot" };

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tab.child(
                Flow.row()
                    .coverChildren()
                    .childPadding(6)
                    .child(new TextWidget<>(IKey.lang(slotLabels[i])).size(100, 14))
                    .child(
                        new ItemSlot().slot(new ModularSlot(inventoryHandler, idx))
                            .size(18))
                    .child(new TextWidget<>(IKey.dynamic(() -> {
                        int tier = hatchTierSyncs[idx].getIntValue();
                        int amps = hatchAmpSyncs[idx].getIntValue();
                        if (tier >= 0 && amps > 0) {
                            return GTUtility.getColoredTierNameFromTier((byte) tier) + " " + amps + "A";
                        }
                        return EnumChatFormatting.YELLOW + "ULV 0V 0A";
                    })).size(120, 14)));
            if (i < 3) tab.child(separator(CONTENT_W));
        }

        tab.child(separator(CONTENT_W));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.hint")).size(CONTENT_W, 14));
        tab.child(
            new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.all_slots_required"))
                .size(CONTENT_W, 14));

        return tab;
    }

    private Flow buildFrequencyTab(IntSyncValue frequencySync) {
        Flow tab = Flow.column()
            .coverChildren()
            .childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.title")).size(CONTENT_W, 16));
        tab.child(separator(CONTENT_W));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.input")).size(CONTENT_W, 14));

        tab.child(
            new TextFieldWidget().value(frequencySync)
                .formatAsInteger(true)
                .numbersInt(() -> (long) Integer.MIN_VALUE, () -> (long) Integer.MAX_VALUE)
                .setMaxLength(12)
                .size(200, 16));

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.hint")).size(CONTENT_W, 14));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.hint2")).size(CONTENT_W, 14));

        return tab;
    }

    private static String formatEU(long eu, int mode) {
        switch (mode) {
            case 1:
                return formatScientific(eu);
            case 2:
                return formatKMG(eu);
            default:
                return formatRegular(eu);
        }
    }

    /**
     * 3.33.0-fix3：电网余额的 BigInteger 精确显示（去除 9.2E 显示上限）。
     * <p>
     * 为什么需要：{@code getGridEULong} 把电网钳到 {@code Long.MAX_VALUE}（9.22E），
     * 用户电网已实测超过 9.2E —— 监控页"电网蓄电"从此永远停在同一数字上。
     * 这里直接读 {@code BigInteger} 并用 {@link #formatKMG(BigInteger)} 格式化，
     * 超过 9.22E 也能显示真实值（如 15.80E）。统计/补电等算术路径仍用 long 快照（够用）。
     */
    // ==================== 3.35.0-fix23：统计 BigInteger 显示链 ====================

    /**
     * 把 StringSyncValue 承载的十进制串安全解析（空/失败 → 0）。
     * StringSync 的值在上一次同步前可能为 null —— 面板首帧的兜底。
     */
    /** 3.35.0-fix23：统计窗口比较用的 BigInteger 电网余额（静态；owner 未知 → 0）。 */
    private static BigInteger bigGrid(UUID owner) {
        return owner == null ? BigInteger.ZERO : AdaptiveHatchHelper.getGridEUBigInteger(owner);
    }

    private static BigInteger biFromString(String text) {
        if (text == null || text.isEmpty()) return BigInteger.ZERO;
        try {
            return new BigInteger(text.trim());
        } catch (RuntimeException e) {
            return BigInteger.ZERO;
        }
    }

    /**
     * 客户端可用的**精确电网余额**（与电网总能量行同源：owner 可知时读 helper 的 BigInteger
     * 快照；不可知时回退钳制 long——与旧显示一致的可用上界）。
     */
    private BigInteger biGridExact() {
        if (networkOwner == null) return BigInteger.ZERO;
        return AdaptiveHatchHelper.getGridEUBigInteger(networkOwner);
    }

    /** 给 formatAmpTier（long 形参）的安培档名：绝对值封顶到 long——档位语义只看数量级。 */
    private static long capToLong(BigInteger v) {
        if (v == null) return 0;
        if (v.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return Long.MAX_VALUE;
        if (v.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) return Long.MIN_VALUE;
        return v.longValue();
    }

    /**
     * displayMode（常规/科学/KMG）感知的 BigInteger 格式化——对齐 {@link #formatEU(long, int)}：
     * 常规=千分位分组；科学=【首位.三位尾数 E 指数】；KMG=两档位小数。>9.22E 显示真实值。
     */
    private static String formatEUBig(BigInteger eu, int mode) {
        if (eu == null) return "0";
        switch (mode) {
            case 1:
                return formatScientificBig(eu);
            case 2:
                return formatKMG(eu);
            default:
                return formatRegularBig(eu);
        }
    }

    private static String formatRegularBig(BigInteger eu) {
        int s = eu.signum();
        if (s == 0) return "0";
        String digits = eu.abs()
            .toString();
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(digits.charAt(i));
        }
        return s < 0 ? "-" + sb : sb.toString();
    }

    private static String formatScientificBig(BigInteger eu) {
        int s = eu.signum();
        if (s == 0) return "0";
        if (s < 0) return "-" + formatScientificBig(eu.negate());
        String digits = eu.toString();
        int exp = digits.length() - 1;
        // 首位.三位尾数 —— 对齐 formatScientific(long) 的 %.2fe%d 口径
        StringBuilder sb = new StringBuilder();
        sb.append(digits.charAt(0))
            .append('.');
        for (int i = 1; i <= 2 && i < digits.length(); i++) {
            sb.append(digits.charAt(i));
        }
        if (digits.length() < 3) sb.append('0');
        sb.append('e')
            .append(exp);
        return sb.toString();
    }

    private static String formatGridEUPrecise(long fallback, UUID owner) {
        if (owner == null) return formatKMG(fallback);
        BigInteger exact = AdaptiveHatchHelper.getGridEUBigInteger(owner);
        if (exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 0) {
            // 未超界：保持旧路径（含 0/负值的原样格式化），避免双轨显示不一致
            return formatKMG(fallback);
        }
        return formatKMG(exact);
    }

    private static String formatKMG(BigInteger eu) {
        if (eu.signum() < 0) return "-" + formatKMG(eu.negate());
        BigInteger thousand = BigInteger.valueOf(1000);
        String[] suffixes = { "", "K", "M", "G", "T", "P", "E" };
        int tier = 0;
        BigInteger val = eu;
        while (val.compareTo(thousand) >= 0 && tier < suffixes.length - 1) {
            val = val.divide(thousand);
            tier++;
        }
        // 保留两位小数：用余数手工拼（BigInteger 没有 %f）
        if (tier > 0) {
            BigInteger frac = eu.divide(BigInteger.TEN.pow(tier * 3 - 2));
            long frac100 = frac.mod(BigInteger.valueOf(100))
                .longValue();
            return String.format("%d.%02d%s", val.longValue(), frac100, suffixes[tier]);
        }
        return eu.toString();
    }

    private static String formatRegular(long eu) {
        if (eu == 0) return "0";
        boolean neg = eu < 0;
        String digits = String.valueOf(Math.abs(eu));
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(digits.charAt(i));
        }
        return neg ? "-" + sb : sb.toString();
    }

    private static String formatScientific(long eu) {
        if (eu == 0) return "0";
        if (eu < 0) return "-" + formatScientific(-eu);
        int exp = (int) Math.floor(Math.log10(eu));
        double mantissa = eu / Math.pow(10, exp);
        return String.format("%.2fe%d", mantissa, exp);
    }

    private static String formatKMG(long eu) {
        if (eu == 0) return "0";
        if (eu < 0) return "-" + formatKMG(-eu);
        String[] suffixes = { "", "K", "M", "G", "T", "P", "E" };
        int tier = 0;
        double val = eu;
        while (val >= 1000 && tier < suffixes.length - 1) {
            val /= 1000;
            tier++;
        }
        if (tier > 0) return String.format("%.2f%s", val, suffixes[tier]);
        return String.valueOf(eu);
    }

    private static String formatAmpTier(long euPerTick) {
        if (euPerTick <= 0) return "";
        for (int tier = 14; tier >= 0; tier--) {
            if (euPerTick >= V[tier]) {
                double amps = (double) euPerTick / V[tier];
                return String.format("(%.1fA ", amps) + GTUtility.getColoredTierNameFromTier((byte) tier) + ")";
            }
        }
        return "";
    }

    private static String formatDuration(long ticks) {
        if (ticks <= 0) return "\u221e";
        long seconds = ticks / 20;
        if (seconds <= 0) return "1\u79d2";
        long years = seconds / 31536000L;
        seconds %= 31536000L;
        long months = seconds / 2592000L;
        seconds %= 2592000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years)
            .append("\u5e74 ");
        if (months > 0) sb.append(months)
            .append("\u6708 ");
        if (days > 0) sb.append(days)
            .append("\u5929 ");
        if (hours > 0) sb.append(hours)
            .append("\u65f6 ");
        if (minutes > 0) sb.append(minutes)
            .append("\u5206 ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds)
            .append("\u79d2");
        return sb.toString()
            .trim();
    }

    private static final String[] MODE_KEYS = { "ae2_qof.gui.adaptive_terminal.monitor.mode_regular",
        "ae2_qof.gui.adaptive_terminal.monitor.mode_scientific", "ae2_qof.gui.adaptive_terminal.monitor.mode_kmg" };

    private Flow buildMonitorTab(LongSyncValue gridEUSync, StringSyncValue change1hSync, StringSyncValue change10mSync,
        StringSyncValue avgOut10mSync, StringSyncValue instantInputSync, StringSyncValue instantOutputSync) {
        Flow tab = Flow.column()
            .size(CONTENT_W, 0)
            .childPadding(2);
        final int labelW = 130;
        final int valueW = CONTENT_W - labelW - 4;

        // 标题行：标题（左）+ 计数按钮（右）
        Flow titleRow = Flow.row()
            .coverChildren()
            .childPadding(4);
        titleRow.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.title")).size(180, 16));
        titleRow.child(
            new ButtonWidget<>().size(64, 14)
                .child(
                    new TextWidget<>(IKey.dynamic(() -> StatCollector.translateToLocal(MODE_KEYS[displayMode])))
                        .size(60, 12)
                        .textAlign(com.cleanroommc.modularui.utils.Alignment.Center))
                .background(GuiTextures.BUTTON_CLEAN)
                .onMousePressed((event) -> {
                    displayMode = (displayMode + 1) % 3;
                    return true;
                }));
        tab.child(titleRow);
        tab.child(separator(CONTENT_W));

        // 区块：总览
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(
                    new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.grid_energy")).size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    long gridEU = gridEUSync.getLongValue();
                    // 3.33.0-fix3：电网余额 >9.22E 时走 BigInteger 精确显示（其余情况与旧路径一致）
                    return EnumChatFormatting.WHITE + formatGridEUPrecise(gridEU, networkOwner) + " EU";
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 区块：能量变化
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.change_1h")).size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    // 3.35.0-fix23：BigInteger 精确值（串 → BigInt 客户端解析 → 按 displayMode 格式化）
                    BigInteger change = biFromString(change1hSync.getValue());
                    BigInteger avgRate = change.abs()
                        .divide(BigInteger.valueOf(72000L));
                    String changeStr = formatEUBig(change, displayMode) + " EU";
                    String rateStr = avgRate.signum() > 0
                        ? " (" + formatEUBig(avgRate, displayMode) + " EU/t " + formatAmpTier(capToLong(avgRate)) + ")"
                        : "";
                    String arrow = change.signum() >= 0 ? EnumChatFormatting.GREEN + "↑ "
                        : EnumChatFormatting.RED + "↓ ";
                    return arrow + EnumChatFormatting.WHITE + changeStr + EnumChatFormatting.YELLOW + rateStr;
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.change_10m")).size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    // 3.35.0-fix23：BigInteger 精确值（串 → BigInt 客户端解析 → 按 displayMode 格式化）
                    BigInteger change = biFromString(change10mSync.getValue());
                    BigInteger avgRate = change.abs()
                        .divide(BigInteger.valueOf(72000L));
                    String changeStr = formatEUBig(change, displayMode) + " EU";
                    String rateStr = avgRate.signum() > 0
                        ? " (" + formatEUBig(avgRate, displayMode) + " EU/t " + formatAmpTier(capToLong(avgRate)) + ")"
                        : "";
                    String arrow = change.signum() >= 0 ? EnumChatFormatting.GREEN + "↑ "
                        : EnumChatFormatting.RED + "↓ ";
                    return arrow + EnumChatFormatting.WHITE + changeStr + EnumChatFormatting.YELLOW + rateStr;
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 区块：预测
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(
                    new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.estimated_empty"))
                        .size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    // 3.35.0-fix23：分子/分母都 BigInteger 精确（旧链被 9.22E 钳制 ⇒ 恒"无穷"）
                    BigInteger avgOut = biFromString(avgOut10mSync.getValue());
                    BigInteger gridBig = biGridExact();
                    String timeStr;
                    if (avgOut.signum() <= 0 || gridBig.signum() <= 0) {
                        timeStr = "\u221e";
                    } else {
                        BigInteger[] q = gridBig.divideAndRemainder(avgOut);
                        if (q[0].compareTo(BigInteger.valueOf(6307200000L)) > 0) {
                            timeStr = ">100年（近似无穷）";
                        } else {
                            long ticks = q[0].longValue();
                            if (q[1].signum() > 0) ticks++;
                            timeStr = formatDuration(ticks);
                        }
                    }
                    return EnumChatFormatting.WHITE + timeStr;
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 区块：瞬时速率
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(
                    new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.instant_input")).size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    BigInteger inRate = biFromString(instantInputSync.getValue());
                    return EnumChatFormatting.GREEN + formatEUBig(inRate, displayMode)
                        + " EU/t"
                        + formatAmpTier(capToLong(inRate));
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(
                    new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.instant_output"))
                        .size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    BigInteger outRate = biFromString(instantOutputSync.getValue());
                    return EnumChatFormatting.RED + formatEUBig(outRate, displayMode)
                        + " EU/t"
                        + formatAmpTier(capToLong(outRate));
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 能量活动
        tab.child(
            Flow.row()
                .size(CONTENT_W, 14)
                .childPadding(2)
                .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.activity")).size(labelW, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    BigInteger inRate = biFromString(instantInputSync.getValue());
                    BigInteger outRate = biFromString(instantOutputSync.getValue());
                    if (inRate.signum() == 0 && outRate.signum() == 0) {
                        return EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.monitor.no_activity");
                    }
                    String inStr = formatEUBig(inRate, displayMode) + " EU/t" + formatAmpTier(capToLong(inRate));
                    String outStr = formatEUBig(outRate, displayMode) + " EU/t" + formatAmpTier(capToLong(outRate));
                    return EnumChatFormatting.GREEN + "+"
                        + inStr
                        + EnumChatFormatting.WHITE
                        + " / "
                        + EnumChatFormatting.RED
                        + "-"
                        + outStr;
                })).size(valueW, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        return tab;
    }

    private Flow buildHatchListTab(StringSyncValue ownerSync, IntSyncValue frequencySync,
        PanelSyncManager syncManager) {
        Flow tab = Flow.column()
            .childPadding(2)
            .size(CONTENT_W, PAGE_H);

        // 标题行：标题（左）+ 总数（右）
        tab.child(
            Flow.row()
                .size(CONTENT_W, 16)
                .childPadding(2)
                .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.hatch_list.title")).size(120, 14))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache cache = com.wztwzt.ae2_qof.client.ClientState.hatchListCache;
                    if (cache == null) return EnumChatFormatting.YELLOW + "---";
                    return EnumChatFormatting.AQUA
                        + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.total")
                        + ": "
                        + EnumChatFormatting.WHITE
                        + cache.totalCount
                        + EnumChatFormatting.DARK_GRAY
                        + " ("
                        + cache.hostCount
                        + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.host_unit")
                        + ")";
                })).size(CONTENT_W - 124, 14)
                    .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        final int rowW = CONTENT_W - 8;

        // 3.35.0-fix6（审查 P1-15）：子仓列表改**数据驱动重建**（与库存统计终端同款范式）。
        // 旧实现把行控件在建树那一刻一次性 list.child(...) 印死——而数据最快 20 tick 后才到、
        // "数据到了"只是替换静态缓存（ClientProxy.handleHatchListSync），没有任何代码重建行控件
        // ⇒ 首次打开列表页大概率为空、仓变化要"关掉重开"才看得到。
        // 现在挂一个 DynamicLinkedSyncHandler：服务端 getter 从网络读最新缓存（列表 dirty 时
        // 自动 S2C），widgetProvider 在快照到达后**重建全部行控件**。首开即有数据、变化即时刷新。
        // 与范例同款：GLSH 本体留局部引用（initialChild 读当前快照），DLH 包它 + widgetProvider
        GenericListSyncHandler<HatchListCache.HatchEntry> hatchRows = GenericListSyncHandler.<HatchListCache.HatchEntry>builder()
            .getter(() -> {
                com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache c = com.wztwzt.ae2_qof.client.ClientState.hatchListCache;
                return c == null ? java.util.Collections.<HatchListCache.HatchEntry>emptyList() : c.entries;
            })
            .serializer(HatchListCache::writeEntry)
            .deserializer(HatchListCache::readEntry)
            .build();
        DynamicLinkedSyncHandler<GenericListSyncHandler<HatchListCache.HatchEntry>> hatchListHandler = new DynamicLinkedSyncHandler<>(
            hatchRows).widgetProvider(
                (dynamicSyncManager,
                    value) -> buildHatchListRows(value.getValue(), rowW, ownerSync, frequencySync, displayMode));
        syncManager.syncValue("wHL", hatchListHandler);

        // DynamicSyncedWidget 本身不滚动——滚动由 initialChild 的 ListWidget 承担（范例同款）
        DynamicSyncedWidget<?> list = new DynamicSyncedWidget<>().size(CONTENT_W, PAGE_H - 30)
            .initialChild(buildHatchListRows(hatchRows.getValue(), rowW, ownerSync, frequencySync, displayMode))
            .syncHandler(hatchListHandler);

        tab.child(list);
        tab.child(separator(CONTENT_W));

        // footer：输入仓/输出仓统计
        tab.child(new TextWidget<>(IKey.dynamic(() -> {
            com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache c = com.wztwzt.ae2_qof.client.ClientState.hatchListCache;
            if (c == null) return EnumChatFormatting.GRAY + "---";
            return EnumChatFormatting.WHITE + c.inputCountText
                + EnumChatFormatting.GRAY
                + " | "
                + EnumChatFormatting.WHITE
                + c.outputCountText;
        })).size(CONTENT_W, 12));

        return tab;
    }

    /**
     * 3.35.0-fix6（审查 P1-15）：构建子仓列表的**全部行控件**（由
     * DynamicLinkedSyncHandler 的 widgetProvider 在快照到达/变化时调用重建）。
     * 旧实现在 buildHatchListTab 里一次性 list.child(...) 印死——数据晚于建树到达时
     * 列表永远空，仓变化要"关掉重开"。现在数据驱动：cache 快照 → 行。
     */
    private com.cleanroommc.modularui.widgets.ListWidget buildHatchListRows(
        java.util.List<com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache.HatchEntry> entries, int rowW,
        StringSyncValue ownerSync, IntSyncValue frequencySync, int displayMode) {
        com.cleanroommc.modularui.widgets.ListWidget list = new com.cleanroommc.modularui.widgets.ListWidget();
        list.scrollDirection(com.cleanroommc.modularui.api.GuiAxis.Y);
        list.size(CONTENT_W, PAGE_H - 30);

        for (int i = 0; i < entries.size(); i++) {
            final com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache.HatchEntry entry = entries.get(i);
            // 3.32.0：名称与图标都在**客户端**按 MTE id 解析。旧实现把服务端算好的名字当字符串发下来，
            // 专用服务器上必然是英文（服务端语言表只有 en_US）—— 与库存统计终端 3.31.0 同一个坑。
            final net.minecraft.item.ItemStack iconStack = stackForMetaId(entry.displayMetaId);
            final IWidget iconWidget = iconStack != null ? new ItemDrawable(iconStack).asWidget()
                .size(16, 16)
                : new ItemDrawable().asWidget()
                    .size(16, 16);
            final String hostLabel = hostLabelOf(iconStack);
            final int textW = rowW - 16 - 46 - 8;

            // 右侧操作按钮：左键高亮 / Shift+左键传送。
            // 3.32.0：改成**按坐标**寻址并定位到**主机**（旧实现按 helpers 的下标寻址，
            // 列表重建/仓增删后索引会错位，可能跳到别的仓）。
            ButtonWidget<?> actionBtn = new ButtonWidget<>().size(42, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .child(
                    new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "定位"))
                        .textAlign(com.cleanroommc.modularui.utils.Alignment.Center)
                        .size(42, 18))
                .onMousePressed(event -> {
                    String ownerStr = ownerSync.getValue();
                    if (ownerStr == null || ownerStr.isEmpty()) return true;
                    boolean shift = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                        || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
                    int action = shift ? HatchActionPacket.ACTION_TELEPORT : HatchActionPacket.ACTION_HIGHLIGHT;
                    // 主机行的着色：有输入仓用蓝色(1)，否则橙色(0)
                    int colorType = entry.inCount > 0 ? 1 : 0;
                    ModNetwork.CHANNEL.sendToServer(
                        new HatchActionPacket(
                            action,
                            ownerStr,
                            frequencySync.getIntValue(),
                            entry.dim,
                            entry.x,
                            entry.y,
                            entry.z,
                            colorType));
                    return true;
                });

            // 行布局：图标 + 两行文字（主机名/耗电 + 仓数与额定·实测）+ 定位
            Flow textCol = Flow.column()
                .size(textW, 24)
                .childPadding(0)
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    // 3.32.0-fix4：MUI2 面板是浅色底 ⇒ 一律用**深色**字，白/亮色在浅底上糊成一片（用户反馈"颜色不清晰"）
                    String prefix = entry.hostKnown ? EnumChatFormatting.BLACK + hostLabel
                        : EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.unknown_host")
                            + EnumChatFormatting.BLACK
                            + hostLabel;
                    // 状态标签：GT 的 mEUt 停机不清零 ⇒ 显示耗电前必须先问"在不在跑"
                    String state = entry.running
                        ? EnumChatFormatting.DARK_GREEN
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.running")
                        : EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.idle");
                    // 主数值 = **实测流量**（我们自己的仓量到的真实吞吐；停机自然为 0）
                    EnumChatFormatting flowColor = entry.realFlowEUt != 0 ? EnumChatFormatting.DARK_RED
                        : EnumChatFormatting.DARK_GRAY;
                    return prefix + EnumChatFormatting.DARK_GRAY
                        + "  ·  "
                        + state
                        + EnumChatFormatting.DARK_GRAY
                        + "  ·  "
                        + EnumChatFormatting.DARK_AQUA
                        + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.flow")
                        + " "
                        + flowColor
                        + formatEU(entry.realFlowEUt, displayMode)
                        + " EU/t";
                })).size(textW, 12))
                .child(
                    new TextWidget<>(
                        IKey.dynamic(
                            () -> EnumChatFormatting.DARK_GRAY.toString() + entry.hatchCount
                                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.hatch_unit")
                                + " · "
                                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.in")
                                + " "
                                + entry.inCount
                                + " / "
                                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.out")
                                + " "
                                + entry.outCount
                                + " · "
                                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.rated")
                                + " "
                                + EnumChatFormatting.DARK_BLUE
                                + formatEU(entry.eut, displayMode)
                                // 配方 EUt（GT 的 mEUt）**只在运行时**显示：停机时它是上一次配方的残留值
                                + (entry.running
                                    ? " · " + EnumChatFormatting.DARK_GRAY
                                        + StatCollector
                                            .translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.recipe")
                                        + " "
                                        + EnumChatFormatting.DARK_PURPLE
                                        + formatEU(entry.hostEut, displayMode)
                                    : ""))).size(textW, 11)
                                        .scale(0.85F));

            Flow rowFlow = Flow.row()
                .size(rowW, 24)
                .childPadding(2)
                .crossAxisAlignment(com.cleanroommc.modularui.utils.Alignment.CrossAxis.CENTER)
                .child(iconWidget)
                .child(textCol)
                .child(actionBtn);

            rowFlow.tooltipBuilder(t -> {
                t.addLine(
                    IKey.str(
                        EnumChatFormatting.WHITE + hostLabel
                            + (entry.hostKnown ? ""
                                : EnumChatFormatting.GRAY + " ("
                                    + StatCollector
                                        .translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.unknown_host")
                                    + ")")));
                if (entry.ownerName != null && !entry.ownerName.isEmpty()) {
                    t.addLine(
                        IKey.str(
                            EnumChatFormatting.AQUA
                                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.owner")
                                + ": "
                                + entry.ownerName));
                }
                t.addLine(
                    IKey.str(
                        EnumChatFormatting.GRAY + "["
                            + entry.x
                            + ", "
                            + entry.y
                            + ", "
                            + entry.z
                            + "] dim:"
                            + entry.dim));
                t.addLine(
                    IKey.str(
                        EnumChatFormatting.YELLOW
                            + (entry.running
                                ? EnumChatFormatting.GREEN
                                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.running")
                                : EnumChatFormatting.GRAY
                                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.idle"))
                            + EnumChatFormatting.YELLOW
                            + " | "
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.flow")
                            + ": "
                            + formatEU(entry.realFlowEUt, displayMode)
                            + " EU/t"
                            + " | "
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.rated")
                            + ": "
                            + formatEU(entry.eut, displayMode)
                            + " EU/t"
                            + (entry.running
                                ? " | "
                                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.recipe")
                                    + ": "
                                    + formatEU(entry.hostEut, displayMode)
                                    + " EU/t"
                                : "")));
                // 明细：逐仓列出类型/档位/安培/额定/实测（名称同样客户端解析）
                for (com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache.Sub sub : entry.subs) {
                    net.minecraft.item.ItemStack subStack = stackForMetaId(sub.metaId);
                    String subName = subStack != null ? subStack.getDisplayName() : ("#" + sub.metaId);
                    t.addLine(
                        IKey.str(
                            EnumChatFormatting.GRAY + " - "
                                + EnumChatFormatting.WHITE
                                + subName
                                + EnumChatFormatting.GRAY
                                + " "
                                + hatchTypeName(sub.hatchType)
                                + " "
                                + GTUtility.getColoredTierNameFromTier((byte) Math.max(0, Math.min(15, sub.tier)))
                                + EnumChatFormatting.GRAY
                                + " "
                                + sub.amps
                                + "A"
                                + " · "
                                + formatEU(sub.eut, displayMode)
                                + " / "
                                + formatEU(sub.realFlowEUt, displayMode)
                                + " ["
                                + sub.x
                                + ","
                                + sub.y
                                + ","
                                + sub.z
                                + "]"));
                }
                t.addLine(IKey.str(EnumChatFormatting.YELLOW + "左键定位: 高亮 | Shift+定位: 传送"));
            });

            list.child(rowFlow);
            list.child(separator(rowW));
        }

        return list;
    }

    private static IWidget separator(int width) {
        return new TextWidget<>(IKey.str("")).size(width, 1);
    }

    /**
     * 客户端按 MTE id 取物品 —— 名称与图标都由它**在客户端**解析。
     *
     * <p>
     * 为什么不直接发名字：服务端 {@code ItemStack.getDisplayName()} 走 {@code StatCollector}，
     * 而专用服务器只有 en_US 语言表 ⇒ 发下来必是英文（与库存统计终端 3.31.0 同一个坑）。
     */
    private static ItemStack stackForMetaId(int metaId) {
        if (metaId < 0 || metaId >= GregTechAPI.METATILEENTITIES.length) return null;
        IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[metaId];
        if (!(mte instanceof MetaTileEntity)) return null;
        try {
            return ((MetaTileEntity) mte).getStackForm(1L);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String hostLabelOf(ItemStack iconStack) {
        if (iconStack != null) {
            try {
                String n = iconStack.getDisplayName();
                if (n != null && !n.isEmpty()) return n;
            } catch (Throwable ignored) {}
        }
        return StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.unknown_host");
    }

    /** 明细行的仓类型名（0..3 = 本模组四类；其它 = TecTech 兼容）。 */
    private static String hatchTypeName(int hatchType) {
        String[] keys = { "ae2_qof.gui.adaptive_terminal.dynamo_hatch", "ae2_qof.gui.adaptive_terminal.energy_hatch",
            "ae2_qof.gui.adaptive_terminal.laser_source", "ae2_qof.gui.adaptive_terminal.laser_target" };
        if (hatchType >= 0 && hatchType < keys.length) return StatCollector.translateToLocal(keys[hatchType]);
        return StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.compat");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AdaptiveNetTerminal(mName, mTier, TERMINAL_TIER, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (networkOwner != null) {
            aNBT.setString("ae2qolNO", networkOwner.toString());
        }
        aNBT.setInteger("ae2qolNF", networkFrequency);
        aNBT.setInteger("ae2qolVT", currentVoltageTier);
        aNBT.setBoolean("ae2qolAR", autoReconnect);
        for (int i = 0; i < HatchType.COUNT; i++) {
            aNBT.setInteger("ae2qolHT" + i, hatchTiers[i]);
            aNBT.setInteger("ae2qolHA" + i, hatchAmps[i]);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        String s = aNBT.getString("ae2qolNO");
        if (s != null && !s.isEmpty()) {
            try {
                networkOwner = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        }
        networkFrequency = aNBT.getInteger("ae2qolNF");
        currentVoltageTier = aNBT.getInteger("ae2qolVT");
        autoReconnect = aNBT.getBoolean("ae2qolAR");
        for (int i = 0; i < HatchType.COUNT; i++) {
            hatchTiers[i] = aNBT.getInteger("ae2qolHT" + i);
            hatchAmps[i] = aNBT.getInteger("ae2qolHA" + i);
            if (hatchAmps[i] <= 0) hatchAmps[i] = HatchType.values()[i].defaultAmps;
        }
    }

    @Override
    public boolean canExtractItem(int aIndex, ItemStack aStack, int aSide) {
        return false;
    }

    @Override
    public boolean canInsertItem(int aIndex, ItemStack aStack, int aSide) {
        return true;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int aSide) {
        return new int[] { SLOT_DYNAMO, SLOT_ENERGY, SLOT_LASER_SOURCE, SLOT_LASER_TARGET };
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        HatchType ht = HatchType.fromSlotIndex(aIndex);
        if (ht == null) return false;
        int damage = aStack.getItemDamage();
        IMetaTileEntity imte = GregTechAPI.METATILEENTITIES[damage];
        if (!(imte instanceof MetaTileEntity)) return false;
        return ht.isValidMTEType((MetaTileEntity) imte);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public int getSizeInventory() {
        return 4;
    }

    @Override
    public ItemStack getStackInSlot(int aIndex) {
        if (aIndex >= 0 && aIndex < mInventory.length) return mInventory[aIndex];
        return null;
    }

    @Override
    public ItemStack decrStackSize(int aIndex, int aAmount) {
        if (aIndex >= 0 && aIndex < mInventory.length && mInventory[aIndex] != null) {
            if (mInventory[aIndex].stackSize <= aAmount) {
                ItemStack stack = mInventory[aIndex];
                mInventory[aIndex] = null;
                return stack;
            }
            ItemStack split = mInventory[aIndex].splitStack(aAmount);
            if (mInventory[aIndex].stackSize == 0) mInventory[aIndex] = null;
            return split;
        }
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int aIndex) {
        if (aIndex >= 0 && aIndex < mInventory.length && mInventory[aIndex] != null) {
            ItemStack stack = mInventory[aIndex];
            mInventory[aIndex] = null;
            return stack;
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aIndex >= 0 && aIndex < mInventory.length) {
            mInventory[aIndex] = aStack;
        }
    }

    @Override
    public String getInventoryName() {
        return mName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public void markDirty() {}

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        return new ITexture[0][][];
    }

    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.common.util.ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("adaptive_net_terminal") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("adaptive_net_terminal") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("adaptive_net_terminal") };
        }
        return super.getTexture(aBaseMetaTileEntity, side, facing, aColorIndex, aActive, aRedstone);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity a, int i, net.minecraftforge.common.util.ForgeDirection s,
        ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity a, int i, net.minecraftforge.common.util.ForgeDirection s,
        ItemStack stack) {
        return false;
    }

    private static net.minecraft.entity.player.EntityPlayerMP findPlayerByUUID(net.minecraft.world.World world,
        UUID uuid) {
        if (world == null || uuid == null) return null;
        for (Object obj : world.playerEntities) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP p = (net.minecraft.entity.player.EntityPlayerMP) obj;
                if (uuid.equals(
                    p.getGameProfile()
                        .getId()))
                    return p;
            }
        }
        return null;
    }
}
