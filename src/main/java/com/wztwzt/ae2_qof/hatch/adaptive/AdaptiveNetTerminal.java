package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

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
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.api.widget.IWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;
import com.wztwzt.ae2_qof.network.HatchActionPacket;
import com.wztwzt.ae2_qof.network.HatchListSyncPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

public class AdaptiveNetTerminal extends MTEHatch {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");
    private static final int SLOT_DYNAMO = 0;
    private static final int SLOT_ENERGY = 1;
    private static final int SLOT_LASER_SOURCE = 2;
    private static final int SLOT_LASER_TARGET = 3;
    private static final int REQUIRED_STACK_SIZE = 64;
    private static final int TERMINAL_TIER = 4;
    private static final int CONTENT_W = 330;

    private UUID networkOwner;
    private int networkFrequency = 0;
    private boolean autoReconnect = true;
    private int displayMode = 0; // 0=regular, 1=scientific, 2=KMG
    private net.minecraft.world.World world;
    private int syncTick = 0;

    private int currentVoltageTier = 0;
    private int[] hatchTiers = new int[HatchType.COUNT];
    private int[] hatchAmps = new int[HatchType.COUNT];

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
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.1"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.2"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
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
        java.util.List<AdaptiveHatchHelper> helpers = network.getAllHelpers();

        java.util.List<AdaptiveHatchHelper> stale = new java.util.ArrayList<>();
        for (AdaptiveHatchHelper h : helpers) {
            WorldServer ws = MinecraftServer.getServer().worldServerForDimension(h.getDim());
            if (ws == null) {
                stale.add(h);
                continue;
            }
            TileEntity te = ws.getTileEntity(h.getX(), h.getY(), h.getZ());
            if (te == null) {
                stale.add(h);
            }
        }
        for (AdaptiveHatchHelper s : stale) {
            AdaptiveNetworkManager.unregisterHatch(s);
        }
        if (!stale.isEmpty()) {
            helpers = network.getAllHelpers();
        }

        java.util.LinkedHashSet<String> seenPositions = new java.util.LinkedHashSet<>();
        java.util.List<HatchListCache.HatchEntry> entries = new java.util.ArrayList<>(helpers.size());
        int globalIndex = 0;
        int totalIn = 0, totalOut = 0;

        String ownerName = "";
        if (networkOwner != null) {
            net.minecraft.entity.player.EntityPlayerMP ownerPlayer = findPlayerByUUID(aBase.getWorld(), networkOwner);
            if (ownerPlayer != null) {
                ownerName = ownerPlayer.getCommandSenderName();
            } else {
                ownerName = networkOwner.toString().substring(0, 8);
            }
        }

        for (AdaptiveHatchHelper h : helpers) {
            String posKey = h.getX() + "," + h.getY() + "," + h.getZ() + "," + h.getDim();
            if (!seenPositions.add(posKey)) continue;

            int tier = h.getCurrentVoltageTier();
            int amps = h.getCurrentAmps();
            HatchType ht = h.getHatchType();
            long eut = 0;

            if (ht == HatchType.ENERGY) {
                eut = V[tier] * (long) amps;
                totalIn++;
            } else if (ht == HatchType.LASER_SOURCE) {
                eut = V[tier] * 2L * amps;
                totalIn++;
            } else if (ht == HatchType.DYNAMO) {
                eut = V[tier] * (long) amps;
                totalOut++;
            } else if (ht == HatchType.LASER_TARGET) {
                eut = V[tier] * 2L * amps;
                totalOut++;
            } else {
                totalOut++;
            }

            String displayName = h.getMachineName();
            if (displayName == null || displayName.isEmpty()) displayName = h.getCachedName();
            int displayMetaId = h.getMachineMetaId();
            if (displayMetaId < 0) displayMetaId = h.getCachedMetaId();

            entries.add(new HatchListCache.HatchEntry(
                displayName, h.getCachedMetaId(), displayMetaId, eut, h.getRealFlowEUt(), tier, amps,
                ht.ordinal(), globalIndex,
                h.getX(), h.getY(), h.getZ(), h.getDim(),
                ownerName));
            globalIndex++;
        }

        HatchListSyncPacket packet = new HatchListSyncPacket(
            networkOwner, networkFrequency, helpers.size(), entries, totalIn, totalOut);
        for (UUID viewerUUID : network.getActiveViewers()) {
            net.minecraft.entity.player.EntityPlayerMP viewer = findPlayerByUUID(aBase.getWorld(), viewerUUID);
            if (viewer != null) {
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendTo(packet, viewer);
            }
        }
    }

    private void updateParamsFromSlots() {
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
                        hatchTiers[i] = 0;
                        hatchAmps[i] = 0;
                        continue;
                    }
                    boolean isOutput = (ht == HatchType.DYNAMO || ht == HatchType.LASER_TARGET);
                    int tier = (int) (isOutput ? mte.getOutputTier() : mte.getInputTier());
                    hatchTiers[i] = (tier >= 0) ? tier : ht.defaultTier;
                    if (tier > bestTier) bestTier = tier;
                    anyValid = true;
                    hatchAmps[i] = (int) (isOutput ? mte.maxAmperesOut() : mte.maxAmperesIn());
                } else {
                    hatchTiers[i] = ht.defaultTier;
                    hatchAmps[i] = ht.defaultAmps;
                }
            } else {
                hatchTiers[i] = 0;
                hatchAmps[i] = 0;
            }
        }

        currentVoltageTier = bestTier;
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

    @Override
    public void onPreTick(IGregTechTileEntity aBase, long aTick) {
        super.onPreTick(aBase, aTick);
        if (aBase.isServerSide() && aTick % 20 == 0) {
            updateParamsFromSlots();
        }
        if (aBase.isServerSide() && networkOwner != null) {
            AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
            if (network != null) {
                network.tickStats(AdaptiveHatchHelper.getGridEULong(networkOwner));
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
                LOG.info("[AE2QoL] Terminal rightclick: player={}, hasData={}", aPlayer.getCommandSenderName(), hasData);
            }

            if (hasData) {
                // 有数据的闪存 → 读取配置到终端
                UUID stickOwner = ItemNetworkDataStick.getOwner(heldItem);
                int stickFreq = ItemNetworkDataStick.getFrequency(heldItem);
                if (!aPlayer.worldObj.isRemote) {
                    LOG.info("[AE2QoL] Reading flash drive: owner={}, freq={}", stickOwner, stickFreq);
                    if (stickOwner == null) {
                        LOG.warn("[AE2QoL] Flash drive owner is null!");
                        aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.RED + "Flash drive owner data invalid"));
                        return true;
                    }
                    int oldFreq = networkFrequency;
                    networkOwner = stickOwner;
                    networkFrequency = stickFreq;
                    LOG.info("[AE2QoL] Terminal updated: oldFreq={} -> newFreq={}, owner={}", oldFreq, stickFreq, stickOwner);
                    AdaptiveNetworkManager.migrateHatches(stickOwner, oldFreq, stickOwner, stickFreq);
                    AdaptiveNetworkManager.unregisterTerminal(this);
                    AdaptiveNetworkManager.registerTerminal(this, world);
                    applySettings();
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("ae2_qof.data_stick.read.success")
                            + " F:" + stickFreq));
                }
                return true;
            } else {
                // 空闪存 → 写入终端配置到闪存
                if (!aPlayer.worldObj.isRemote) {
                    if (networkOwner == null) {
                        networkOwner = aPlayer.getUniqueID();
                        AdaptiveNetworkManager.registerTerminal(this, world);
                        LOG.info("[AE2QoL] Auto-initialized owner from player: {}", networkOwner);
                    }
                    LOG.info("[AE2QoL] Writing to flash drive: owner={}, freq={}", networkOwner, networkFrequency);
                    ItemNetworkDataStick.writeData(heldItem, networkOwner, networkFrequency);
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
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
            UUID viewerUUID = guiData.getPlayer().getUniqueID();
            AdaptiveNetwork net = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
            if (net != null) {
                net.addViewer(viewerUUID);
            }
        }

        IntSyncValue frequencySync = new IntSyncValue(
            () -> networkFrequency,
            v -> {
                v = Math.max(0, v);
                if (v != networkFrequency) {
                    int oldFreq = networkFrequency;
                    networkFrequency = v;
                    if (networkOwner != null) {
                        AdaptiveNetworkManager.migrateHatches(networkOwner, oldFreq, networkOwner, v);
                        AdaptiveNetworkManager.unregisterTerminal(this);
                        AdaptiveNetworkManager.registerTerminal(this, world);
                        applySettings();
                    }
                }
            }
        ).allowC2S();

        IntSyncValue voltageTierSync = new IntSyncValue(
            () -> currentVoltageTier,
            v -> { currentVoltageTier = v; applySettings(); }
        ).allowC2S();

        IntSyncValue hatchTier0Sync = new IntSyncValue(() -> hatchTiers[0], v -> hatchTiers[0] = v);
        IntSyncValue hatchTier1Sync = new IntSyncValue(() -> hatchTiers[1], v -> hatchTiers[1] = v);
        IntSyncValue hatchTier2Sync = new IntSyncValue(() -> hatchTiers[2], v -> hatchTiers[2] = v);
        IntSyncValue hatchTier3Sync = new IntSyncValue(() -> hatchTiers[3], v -> hatchTiers[3] = v);
        IntSyncValue hatchAmp0Sync = new IntSyncValue(() -> hatchAmps[0], v -> hatchAmps[0] = v);
        IntSyncValue hatchAmp1Sync = new IntSyncValue(() -> hatchAmps[1], v -> hatchAmps[1] = v);
        IntSyncValue hatchAmp2Sync = new IntSyncValue(() -> hatchAmps[2], v -> hatchAmps[2] = v);
        IntSyncValue hatchAmp3Sync = new IntSyncValue(() -> hatchAmps[3], v -> hatchAmps[3] = v);

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

        LongSyncValue totalInputSync = new LongSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return 0L;
            return n.getStats().getTotalInput();
        }, v -> {});
        LongSyncValue totalOutputSync = new LongSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return 0L;
            return n.getStats().getTotalOutput();
        }, v -> {});
        IntSyncValue instantInputSync = new IntSyncValue(() -> {
            if (networkOwner == null) return 0;
            AdaptiveNetwork n = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
            if (n == null) return 0;
            return (int) n.getStats().getInstantInputRate();
        }, v -> {});
        IntSyncValue instantOutputSync = new IntSyncValue(() -> {
            if (networkOwner == null) return 0;
            AdaptiveNetwork n = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
            if (n == null) return 0;
            return (int) n.getStats().getInstantOutputRate();
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

        final StringSyncValue ownerNameSync = new StringSyncValue(
            () -> {
                if (networkOwner == null) return "";
                if (world == null) return networkOwner.toString().substring(0, Math.min(8, networkOwner.toString().length()));
                net.minecraft.entity.player.EntityPlayerMP p = findPlayerByUUID(world, networkOwner);
                if (p != null) return p.getCommandSenderName();
                return networkOwner.toString().substring(0, Math.min(8, networkOwner.toString().length()));
            },
            v -> {});
        syncManager.syncValue("wNM", ownerNameSync);

        final LongSyncValue change1hSync = new LongSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return 0;
            long gridEU = gridEUSync.getLongValue();
            return n.getStats().getChange1h(gridEU);
        }, v -> {});
        syncManager.syncValue("wC1", change1hSync);

        final LongSyncValue change10mSync = new LongSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return 0;
            long gridEU = gridEUSync.getLongValue();
            return n.getStats().getChange10min(gridEU);
        }, v -> {});
        syncManager.syncValue("wCM", change10mSync);

        final LongSyncValue avgOut10mSync = new LongSyncValue(() -> {
            AdaptiveNetwork n = getCurrentNetwork();
            if (n == null) return 0;
            long gridEU = gridEUSync.getLongValue();
            return n.getStats().getAvgOutputRate10min(gridEU);
        }, v -> {});
        syncManager.syncValue("wAO", avgOut10mSync);

        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ModularPanel panel = ModularPanel.defaultPanel("adaptive_net_terminal", 370, 280);

        int TAB_W = 36;
        int TAB_H = 28;
        int CONTENT_X = TAB_W;
        int CONTENT_W = 370 - CONTENT_X - 4;

        Flow tabStrip = Flow.column().pos(0, 4).width(TAB_W).childPadding(0);
        tabStrip.child(new PageButton(0, tabController).tab(GuiTextures.TAB_LEFT, -1)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.status"))).size(TAB_W, TAB_H));
        tabStrip.child(new PageButton(1, tabController).tab(GuiTextures.TAB_LEFT, 0)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.settings"))).size(TAB_W, TAB_H));
        tabStrip.child(new PageButton(2, tabController).tab(GuiTextures.TAB_LEFT, 0)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.frequency"))).size(TAB_W, TAB_H));
        tabStrip.child(new PageButton(3, tabController).tab(GuiTextures.TAB_LEFT, 0)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.monitor"))).size(TAB_W, TAB_H));
        tabStrip.child(new PageButton(4, tabController).tab(GuiTextures.TAB_LEFT, 3)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.hatch_list"))).size(TAB_W, TAB_H));
        panel.child(tabStrip);

        PagedWidget<?> pages = new PagedWidget<>()
            .controller(tabController)
            .pos(CONTENT_X, 4)
            .size(CONTENT_W, 260)
            .addPage(buildStatusTab(frequencySync, voltageTierSync, hatchTierSyncs, hatchAmpSyncs, hatchCountSyncs, ownerNameSync))
            .addPage(buildSettingsTab(hatchTierSyncs, hatchAmpSyncs))
            .addPage(buildFrequencyTab(frequencySync))
            .addPage(buildMonitorTab(gridEUSync, change1hSync, change10mSync, avgOut10mSync,
                instantInputSync, instantOutputSync))
            .addPage(buildHatchListTab(ownerSync, frequencySync));
        panel.child(pages);

        panel.bindPlayerInventory();

        if (guiData != null && guiData.getPlayer() != null && networkOwner != null) {
            UUID viewerUUID = guiData.getPlayer().getUniqueID();
            panel.onCloseAction(() -> {
                AdaptiveNetwork net = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
                if (net != null) {
                    net.removeViewer(viewerUUID);
                }
            });
        }

        return panel;
    }

    private Flow buildStatusTab(IntSyncValue frequencySync, IntSyncValue voltageTierSync,
                                IntSyncValue[] hatchTierSyncs, IntSyncValue[] hatchAmpSyncs,
                                IntSyncValue[] hatchCountSyncs, StringSyncValue ownerNameSync) {
        Flow tab = Flow.column().size(CONTENT_W, 0).childPadding(4);

        tab.child(Flow.row().size(CONTENT_W, 16).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.title")).size(150, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                return EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.frequency")
                    + ": " + EnumChatFormatting.WHITE + frequencySync.getIntValue();
            })).size(CONTENT_W - 154, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));
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
                + ": " + EnumChatFormatting.WHITE + ownerDisplay;
        })).size(CONTENT_W, 14));

        tab.child(separator(CONTENT_W));

        String[] labels = {
            "ae2_qof.gui.adaptive_terminal.dynamo_hatch",
            "ae2_qof.gui.adaptive_terminal.energy_hatch",
            "ae2_qof.gui.adaptive_terminal.laser_source",
            "ae2_qof.gui.adaptive_terminal.laser_target"
        };

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
                    return EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal(labels[idx])
                        + ": " + tierName
                        + " (" + EnumChatFormatting.GOLD + V[tier] + EnumChatFormatting.WHITE + ")"
                        + " " + EnumChatFormatting.GREEN + amps + "A"
                        + " | " + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.loaded") + ": "
                        + loadedStr;
                }
                return EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal(labels[idx])
                    + ": " + EnumChatFormatting.YELLOW + "ULV 0V 0A"
                    + " | " + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.loaded") + ": "
                    + loadedStr;
            })).size(CONTENT_W, 14));
        }

        return tab;
    }

    private Flow buildSettingsTab(IntSyncValue[] hatchTierSyncs, IntSyncValue[] hatchAmpSyncs) {
        Flow tab = Flow.column().coverChildren().childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.title")).size(CONTENT_W, 16));
        tab.child(separator(CONTENT_W));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.slots")).size(CONTENT_W, 14));

        String[] slotLabels = {
            "ae2_qof.gui.adaptive_terminal.dynamo_slot",
            "ae2_qof.gui.adaptive_terminal.energy_slot",
            "ae2_qof.gui.adaptive_terminal.laser_source_slot",
            "ae2_qof.gui.adaptive_terminal.laser_target_slot"
        };

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tab.child(Flow.row()
                .coverChildren()
                .childPadding(6)
                .child(new TextWidget<>(IKey.lang(slotLabels[i])).size(100, 14))
                .child(new ItemSlot().slot(new ModularSlot(inventoryHandler, idx)).size(18))
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
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.all_slots_required"))
            .size(CONTENT_W, 14));

        return tab;
    }

    private Flow buildFrequencyTab(IntSyncValue frequencySync) {
        Flow tab = Flow.column().coverChildren().childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.title")).size(CONTENT_W, 16));
        tab.child(separator(CONTENT_W));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.input")).size(CONTENT_W, 14));

        tab.child(new TextFieldWidget().value(frequencySync).formatAsInteger(true)
            .numbersInt(() -> (long) Integer.MIN_VALUE, () -> (long) Integer.MAX_VALUE)
            .setMaxLength(12).size(200, 16));

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.hint")).size(CONTENT_W, 14));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.hint2")).size(CONTENT_W, 14));

        return tab;
    }

    private static String formatEU(long eu, int mode) {
        switch (mode) {
            case 1: return formatScientific(eu);
            case 2: return formatKMG(eu);
            default: return formatRegular(eu);
        }
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
        String[] suffixes = {"", "K", "M", "G", "T", "P", "E"};
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
        if (years > 0) sb.append(years).append("\u5e74 ");
        if (months > 0) sb.append(months).append("\u6708 ");
        if (days > 0) sb.append(days).append("\u5929 ");
        if (hours > 0) sb.append(hours).append("\u65f6 ");
        if (minutes > 0) sb.append(minutes).append("\u5206 ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("\u79d2");
        return sb.toString().trim();
    }

    private static final String[] MODE_KEYS = {
        "ae2_qof.gui.adaptive_terminal.monitor.mode_regular",
        "ae2_qof.gui.adaptive_terminal.monitor.mode_scientific",
        "ae2_qof.gui.adaptive_terminal.monitor.mode_kmg"
    };

    private Flow buildMonitorTab(LongSyncValue gridEUSync, LongSyncValue change1hSync, LongSyncValue change10mSync,
                                  LongSyncValue avgOut10mSync, IntSyncValue instantInputSync,
                                  IntSyncValue instantOutputSync) {
        Flow tab = Flow.column().size(CONTENT_W, 0).childPadding(2);
        final int labelW = 130;
        final int valueW = CONTENT_W - labelW - 4;

        // 标题行：标题（左）+ 计数按钮（右）
        Flow titleRow = Flow.row().coverChildren().childPadding(4);
        titleRow.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.title")).size(180, 16));
        titleRow.child(new ButtonWidget<>()
            .size(64, 14)
            .child(new TextWidget<>(IKey.dynamic(() ->
                StatCollector.translateToLocal(MODE_KEYS[displayMode])
            )).size(60, 12).textAlign(com.cleanroommc.modularui.utils.Alignment.Center))
            .background(GuiTextures.BUTTON_CLEAN)
            .onMousePressed((event) -> {
                displayMode = (displayMode + 1) % 3;
                return true;
            }));
        tab.child(titleRow);
        tab.child(separator(CONTENT_W));

        // 区块：总览
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.grid_energy")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                long gridEU = gridEUSync.getLongValue();
                return EnumChatFormatting.WHITE + formatEU(gridEU, displayMode) + " EU";
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 区块：能量变化
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.change_1h")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                long change = change1hSync.getLongValue();
                long avgRate = Math.abs(change) / 72000L;
                String changeStr = formatEU(change, displayMode) + " EU";
                String rateStr = avgRate > 0 ? " (" + formatEU(avgRate, displayMode) + " EU/t " + formatAmpTier(avgRate) + ")" : "";
                String arrow = change >= 0 ? EnumChatFormatting.GREEN + "↑ " : EnumChatFormatting.RED + "↓ ";
                return arrow + EnumChatFormatting.WHITE + changeStr + EnumChatFormatting.YELLOW + rateStr;
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.change_10m")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                long change = change10mSync.getLongValue();
                long avgRate = Math.abs(change) / 12000L;
                String changeStr = formatEU(change, displayMode) + " EU";
                String rateStr = avgRate > 0 ? " (" + formatEU(avgRate, displayMode) + " EU/t " + formatAmpTier(avgRate) + ")" : "";
                String arrow = change >= 0 ? EnumChatFormatting.GREEN + "↑ " : EnumChatFormatting.RED + "↓ ";
                return arrow + EnumChatFormatting.WHITE + changeStr + EnumChatFormatting.YELLOW + rateStr;
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 区块：预测
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.estimated_empty")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                long avgOut = avgOut10mSync.getLongValue();
                long gridEU = gridEUSync.getLongValue();
                String timeStr;
                if (avgOut <= 0 || gridEU <= 0) {
                    timeStr = "\u221e";
                } else {
                    long ticks = gridEU / avgOut;
                    if (ticks > 6307200000L) {
                        timeStr = ">100年（近似无穷）";
                    } else {
                        timeStr = formatDuration(ticks);
                    }
                }
                return EnumChatFormatting.WHITE + timeStr;
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 区块：瞬时速率
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.instant_input")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                int inRate = instantInputSync.getIntValue();
                return EnumChatFormatting.GREEN + formatEU(inRate, displayMode) + " EU/t" + formatAmpTier(inRate);
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.instant_output")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                int outRate = instantOutputSync.getIntValue();
                return EnumChatFormatting.RED + formatEU(outRate, displayMode) + " EU/t" + formatAmpTier(outRate);
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        // 能量活动
        tab.child(Flow.row().size(CONTENT_W, 14).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.monitor.activity")).size(labelW, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                int inRate = instantInputSync.getIntValue();
                int outRate = instantOutputSync.getIntValue();
                if (inRate == 0 && outRate == 0) {
                    return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.monitor.no_activity");
                }
                String inStr = formatEU(inRate, displayMode) + " EU/t" + formatAmpTier(inRate);
                String outStr = formatEU(outRate, displayMode) + " EU/t" + formatAmpTier(outRate);
                return EnumChatFormatting.GREEN + "+" + inStr
                    + EnumChatFormatting.WHITE + " / "
                    + EnumChatFormatting.RED + "-" + outStr;
            })).size(valueW, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        return tab;
    }

    private Flow buildHatchListTab(StringSyncValue ownerSync, IntSyncValue frequencySync) {
        Flow tab = Flow.column().childPadding(2).size(CONTENT_W, 260);

        // 标题行：标题（左）+ 总数（右）
        tab.child(Flow.row().size(CONTENT_W, 16).childPadding(2)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.hatch_list.title")).size(120, 14))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache cache = com.wztwzt.ae2_qof.client.ClientState.hatchListCache;
                if (cache == null) return EnumChatFormatting.YELLOW + "---";
                return EnumChatFormatting.AQUA + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.hatch_list.total")
                    + ": " + EnumChatFormatting.WHITE + cache.totalCount;
            })).size(CONTENT_W - 124, 14).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight)));

        tab.child(separator(CONTENT_W));

        final int rowW = CONTENT_W - 8;
        ListWidget list = new ListWidget();
        list.scrollDirection(com.cleanroommc.modularui.api.GuiAxis.Y);
        list.size(CONTENT_W, 148);

        com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache cache = com.wztwzt.ae2_qof.client.ClientState.hatchListCache;
        if (cache != null) {
            for (int i = 0; i < cache.entries.size(); i++) {
                final com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache.HatchEntry entry = cache.entries.get(i);

                net.minecraft.item.ItemStack iconStack = null;
                int iconMetaId = entry.machineMetaId >= 0 ? entry.machineMetaId : entry.metaId;
                if (iconMetaId >= 0 && iconMetaId < GregTechAPI.METATILEENTITIES.length
                    && GregTechAPI.METATILEENTITIES[iconMetaId] instanceof MetaTileEntity) {
                    iconStack = ((MetaTileEntity) GregTechAPI.METATILEENTITIES[iconMetaId]).getStackForm(1L);
                }
                final IWidget iconWidget = iconStack != null
                    ? new ItemDrawable(iconStack).asWidget().size(16, 16)
                    : new ItemDrawable().asWidget().size(16, 16);

                // 右侧操作按钮：左键高亮 / Shift+左键传送
                ButtonWidget<?> actionBtn = new ButtonWidget<>()
                    .size(42, 18)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .child(new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "定位"))
                        .textAlign(com.cleanroommc.modularui.utils.Alignment.Center)
                        .size(42, 18))
                    .onMousePressed(event -> {
                        String ownerStr = ownerSync.getValue();
                        if (ownerStr == null || ownerStr.isEmpty()) return true;
                        boolean shift = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                            || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
                        int action = shift ? HatchActionPacket.ACTION_TELEPORT : HatchActionPacket.ACTION_HIGHLIGHT;
                        ModNetwork.CHANNEL.sendToServer(new HatchActionPacket(
                            action, ownerStr, frequencySync.getIntValue(), entry.index));
                        return true;
                    });

                // 行布局：图标 + 机器名 + spacer + EU/t + tier + 按钮
                Flow rowFlow = Flow.row().size(rowW, 20).childPadding(2).coverChildrenHeight(20)
                    .child(iconWidget)
                    .child(new TextWidget<>(IKey.dynamic(() -> {
                        String name = entry.name != null ? entry.name : "";
                        if (name.length() > 16) name = name.substring(0, 16) + "...";
                        return EnumChatFormatting.WHITE + name;
                    })).size(110, 20))
                    .child(new TextWidget<>(IKey.str("")).size(10, 20))
                    .child(new TextWidget<>(IKey.dynamic(() -> {
                        String eutStr = entry.realFlowEUt > 0
                            ? formatEU(entry.realFlowEUt, displayMode) + " EU/t"
                            : "0 EU/t";
                        return EnumChatFormatting.GREEN + eutStr;
                    })).size(80, 20).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight))
                    .child(new TextWidget<>(IKey.dynamic(() -> {
                        return EnumChatFormatting.WHITE + "(" + GTUtility.getColoredTierNameFromTier((byte) entry.tier)
                            + EnumChatFormatting.WHITE + ")";
                    })).size(34, 20).textAlign(com.cleanroommc.modularui.utils.Alignment.CenterRight))
                    .child(actionBtn);

                rowFlow.tooltipBuilder(t -> {
                    t.addLine(IKey.str(EnumChatFormatting.WHITE + entry.name));
                    if (entry.ownerName != null && !entry.ownerName.isEmpty()) {
                        t.addLine(IKey.str(EnumChatFormatting.AQUA
                            + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.owner")
                            + ": " + entry.ownerName));
                    }
                    t.addLine(IKey.str(EnumChatFormatting.GRAY
                        + "[" + entry.x + ", " + entry.y + ", " + entry.z + "] dim:" + entry.dim));
                    t.addLine(IKey.str(EnumChatFormatting.YELLOW
                        + "Capacity: " + formatEU(entry.eut, displayMode) + " EU/t"
                        + " | Flow: " + formatEU(entry.realFlowEUt, displayMode) + " EU/t"));
                    t.addLine(IKey.str(EnumChatFormatting.YELLOW + "左键定位: 高亮 | Shift+定位: 传送"));
                });

                list.child(rowFlow);
                list.child(separator(rowW));
            }
        }

        tab.child(list);
        tab.child(separator(CONTENT_W));

        // footer：输入仓/输出仓统计
        tab.child(new TextWidget<>(IKey.dynamic(() -> {
            com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache c = com.wztwzt.ae2_qof.client.ClientState.hatchListCache;
            if (c == null) return EnumChatFormatting.GRAY + "---";
            return EnumChatFormatting.WHITE + c.inputCountText
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.WHITE + c.outputCountText;
        })).size(CONTENT_W, 12));

        return tab;
    }

    private static IWidget separator(int width) {
        return new TextWidget<>(IKey.str("")).size(width, 1);
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
            } catch (IllegalArgumentException ignored) {
            }
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
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
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

    private static net.minecraft.entity.player.EntityPlayerMP findPlayerByUUID(
            net.minecraft.world.World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        for (Object obj : world.playerEntities) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP p = (net.minecraft.entity.player.EntityPlayerMP) obj;
                if (uuid.equals(p.getGameProfile().getId())) return p;
            }
        }
        return null;
    }
}
