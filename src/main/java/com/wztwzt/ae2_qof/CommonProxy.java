package com.wztwzt.ae2_qof;

import com.wztwzt.ae2_qof.block.BlockExIOPort;
import com.wztwzt.ae2_qof.block.BlockQuestDetector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ItemStockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal;
import com.wztwzt.ae2_qof.terminal.StockMonitorTerminal;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetDynamoHatch;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetHatch;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetLaserHatch;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetLaserTargetHatch;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetTerminal;

import com.wztwzt.ae2_qof.hatch.wireless.WirelessEnergyInputTerminal;
import com.wztwzt.ae2_qof.hatch.wireless.WirelessEnergyOutputTerminal;
import com.wztwzt.ae2_qof.item.ItemInfinityWaterLavaCell;
import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;
import com.wztwzt.ae2_qof.merged.BlockMergedTerminal;
import com.wztwzt.ae2_qof.merged.MergedGuiHandler;
import com.wztwzt.ae2_qof.merged.TileMergedTerminal;
import com.wztwzt.ae2_qof.network.ConfigUpdatePacket;
import com.wztwzt.ae2_qof.network.CraftingCompletePacket;
import com.wztwzt.ae2_qof.network.CraftingResponsePacket;
import com.wztwzt.ae2_qof.network.MergedTerminalBlankCountPacket;
import com.wztwzt.ae2_qof.network.MergedTerminalResultPacket;
import com.wztwzt.ae2_qof.network.ProvidersListS2CPacket;
import com.wztwzt.ae2_qof.network.ReplaceCandidatesPacket;
import com.wztwzt.ae2_qof.network.SwapPatternPacket;
import com.wztwzt.ae2_qof.network.WirelessChannelSyncPacket;
import com.wztwzt.ae2_qof.network.WirelessHighlightPacket;
import com.wztwzt.ae2_qof.network.HatchListSyncPacket;
import com.wztwzt.ae2_qof.tile.TileExIOPort;
import com.wztwzt.ae2_qof.tile.TileQuestDetector;
import com.wztwzt.ae2_qof.wireless.WirelessBlockEventListener;
import com.wztwzt.ae2_qof.wireless.WirelessBlocks;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public static BlockExIOPort blockExIOPort;
    public static BlockQuestDetector blockQuestDetector;
    public static ItemInfinityWaterLavaCell itemInfinityWaterLavaCell;
    public static BlockMergedTerminal blockMergedTerminal;
    public static com.wztwzt.ae2_qof.merged.part.ItemPartMergedTerminal itemPartMergedTerminal;
    public static com.wztwzt.ae2_qof.merged.wireless.ItemWirelessMergedTerminal itemWirelessMergedTerminal;
    public static AE2MaintenanceHatchUniversal maintenanceHatchUniversal;
    public static StockMonitorTerminal stockMonitorTerminal;
    public static AdaptiveNetTerminal adaptiveNetTerminal;
    public static AdaptiveNetHatch adaptiveNetHatch;
    public static AdaptiveNetLaserHatch adaptiveNetLaserHatch;
    public static AdaptiveNetDynamoHatch adaptiveNetDynamoHatch;
    public static AdaptiveNetLaserTargetHatch adaptiveNetLaserTargetHatch;

    public static WirelessEnergyInputTerminal wirelessEnergyInputTerminal;
    public static WirelessEnergyOutputTerminal wirelessEnergyOutputTerminal;
    public static ItemNetworkDataStick networkDataStick;

    public void preInit(FMLPreInitializationEvent event) {
        // ===== RFB childDelegations 注入 =====
        // 背景：早期把 F22 启动崩溃误判为 RFB 类加载问题，因此加了这段注入。
        // 后续确认真实根因是 MetaTileEntity ID 32001 被 GT 本体占用（见下方 fix30），
        // 但这段注入对部分环境下的异常栈打印仍有帮助，故保留最小实现并去掉全部诊断刷屏。
        // P1-004：原先会打印类加载器全部字段 + VERIFY 结果，日志噪声极大，现已降为 debug。
        try {
            ClassLoader sysCl = ClassLoader.getSystemClassLoader();
            java.lang.reflect.Field cdField = null;
            for (String name : new String[]{"childDelegations", "childDelegation", "delegations",
                    "childDelegationPrefixes", "delegationPrefixes"}) {
                try {
                    cdField = sysCl.getClass().getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) { /* try next */ }
            }
            if (cdField != null) {
                cdField.setAccessible(true);
                Object cd = cdField.get(sysCl);
                if (cd instanceof java.util.Set) {
                    @SuppressWarnings("unchecked")
                    java.util.Set<Object> set = (java.util.Set<Object>) cd;
                    set.add("net.minecraft");
                } else if (cd instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) cd;
                    if (!list.contains("net.minecraft")) list.add("net.minecraft");
                } else if (cd instanceof String[]) {
                    String[] arr = (String[]) cd;
                    java.util.List<String> newList = new java.util.ArrayList<>(java.util.Arrays.asList(arr));
                    if (!newList.contains("net.minecraft")) {
                        newList.add("net.minecraft");
                        cdField.set(sysCl, newList.toArray(new String[0]));
                    }
                }
                MyMod.LOG.debug("[AE2QoL-RFB] childDelegations patched via {}", cdField.getName());
            } else {
                MyMod.LOG.debug("[AE2QoL-RFB] childDelegations field not found; skipping patch");
            }
        } catch (Throwable t) {
            MyMod.LOG.debug("[AE2QoL-RFB] patch skipped: {}", t.toString());
        }

        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        MyMod.LOG.info("I am MyMod at version " + Tags.VERSION);

        try {
            WirelessBlocks.preInit();
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] WirelessBlocks.preInit() FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            blockExIOPort = new BlockExIOPort();
            blockExIOPort.setCreativeTab(AE2QoLCreativeTab.INSTANCE);
            GameRegistry.registerBlock(blockExIOPort, appeng.block.AEBaseItemBlock.class, "ex_io_port");
            GameRegistry.registerTileEntity(TileExIOPort.class, "ex_io_portTile");
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] BlockExIOPort registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            blockQuestDetector = new BlockQuestDetector();
            blockQuestDetector.setCreativeTab(AE2QoLCreativeTab.INSTANCE);
            GameRegistry.registerBlock(blockQuestDetector, appeng.block.AEBaseItemBlock.class, "quest_detector");
            GameRegistry.registerTileEntity(TileQuestDetector.class, "quest_detectorTile");
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] BlockQuestDetector registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            itemInfinityWaterLavaCell = new ItemInfinityWaterLavaCell().register();
        } catch (Throwable t) {
            MyMod.LOG.error("[APU] ItemInfinityWaterLavaCell registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            blockMergedTerminal = new BlockMergedTerminal();
            blockMergedTerminal.setCreativeTab(AE2QoLCreativeTab.INSTANCE);
            GameRegistry.registerBlock(blockMergedTerminal, appeng.block.AEBaseItemBlock.class, "merged_terminal");
            GameRegistry.registerTileEntity(TileMergedTerminal.class, "merged_terminalTile");
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] BlockMergedTerminal registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            itemPartMergedTerminal = new com.wztwzt.ae2_qof.merged.part.ItemPartMergedTerminal();
            GameRegistry.registerItem(itemPartMergedTerminal, "merged_terminal_part");
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] ItemPartMergedTerminal registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            itemWirelessMergedTerminal = new com.wztwzt.ae2_qof.merged.wireless.ItemWirelessMergedTerminal();
            GameRegistry.registerItem(itemWirelessMergedTerminal, "wireless_merged_terminal");
            itemWirelessMergedTerminal.registerWirelessHandler();
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] ItemWirelessMergedTerminal registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            networkDataStick = new ItemNetworkDataStick();
            networkDataStick.register();
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] ItemNetworkDataStick registration FAILED", t);
            t.printStackTrace(System.err);
        }
        try {
            ItemStockMonitorCover itemStockMonitorCover = new ItemStockMonitorCover();
            GameRegistry.registerItem(itemStockMonitorCover, "stock_monitor_cover", MyMod.MODID);
            gregtech.api.covers.CoverRegistry.registerCover(
                new net.minecraft.item.ItemStack(itemStockMonitorCover),
                gregtech.api.render.TextureFactory.of(
                    gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS[2][0],
                    gregtech.api.render.TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_CONTROLLER)),
                context -> new StockMonitorCover(context,
                    gregtech.api.render.TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_CONTROLLER)),
                gregtech.api.covers.CoverPlacer.builder()
                    .onlyPlaceIf(StockMonitorCover::isCoverPlaceable)
                    .build());
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] StockMonitorCover registration FAILED", t);
            t.printStackTrace(System.err);
        }
        if (itemInfinityWaterLavaCell != null) {
            try {
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(itemInfinityWaterLavaCell),
                    "wbw",
                    "   ",
                    "   ",
                    'w',
                    net.minecraft.init.Items.water_bucket,
                    'b',
                    net.minecraft.init.Items.lava_bucket);
            } catch (Throwable t) {
                MyMod.LOG.error("[APU] InfinityWaterLavaCell recipe registration FAILED", t);
                t.printStackTrace(System.err);
            }
        }
        registerRecipes();
        try {
            FMLInterModComms
                .sendMessage("Waila", "register", "com.wztwzt.ae2_qof.wireless.TransceiverWailaProvider.register");
        } catch (Throwable t) {
            MyMod.LOG.error("[APU] Waila registration failed with 'Waila', trying 'waila'", t);
            try {
                FMLInterModComms
                    .sendMessage("waila", "register", "com.wztwzt.ae2_qof.wireless.TransceiverWailaProvider.register");
            } catch (Throwable t2) {
                MyMod.LOG.error("[APU] Waila registration also failed with 'waila'", t2);
            }
        }
        try {
            FMLInterModComms
                .sendMessage("Waila", "register", "com.wztwzt.ae2_qof.quest.QuestDetectorWailaProvider.register");
        } catch (Throwable t) {
            try {
                FMLInterModComms
                    .sendMessage("waila", "register", "com.wztwzt.ae2_qof.quest.QuestDetectorWailaProvider.register");
            } catch (Throwable t2) {
                MyMod.LOG.error("[APU] QuestDetector Waila registration failed on both channels", t2);
            }
        }
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(MyMod.instance, new MergedGuiHandler());
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new WirelessBlockEventListener());

        try {
            maintenanceHatchUniversal = new AE2MaintenanceHatchUniversal(
                32000,
                "hatch.maintenance.universal",
                "Universal Maintenance Hatch",
                1);
            GameRegistry.addShapedRecipe(
                maintenanceHatchUniversal.getStackForm(1L),
                "ici",
                "rgr",
                "ici",
                'i',
                net.minecraft.init.Items.iron_ingot,
                'g',
                net.minecraft.init.Blocks.glass,
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Basic.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] AE2MaintenanceHatchUniversal registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        // ===== fix30: StockMonitorTerminal 恢复注册（F22）=====
        // fix13 曾因“启动崩溃”禁用本终端。真正根因不是 RFB：MetaTileEntity ID 32001
        // 已被 GT 本体 LegacyUniversalChemicalFuelEngine（通用化学燃料引擎）占用。
        // CommonMetaTileEntity 构造器发现 ID 重复会抛 IllegalArgumentException，
        // 该异常在 FML init 阶段被 Log4j 记录时又触发 RFB 二次加载错误（NoClassDefFoundError），
        // 把真正的“ID 占用”异常掩盖成了类加载问题。改用空闲 ID 32101 后注册恢复正常。
        try {
            stockMonitorTerminal = new StockMonitorTerminal(
                32101,
                "stock_monitor_terminal",
                "Stock Monitor Terminal",
                1);
            GameRegistry.addShapedRecipe(
                stockMonitorTerminal.getStackForm(1L),
                "ici",
                "rgr",
                "ici",
                'i',
                net.minecraft.init.Items.iron_ingot,
                'g',
                net.minecraft.init.Blocks.glass,
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Basic.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        try {
            adaptiveNetTerminal = new AdaptiveNetTerminal(
                32100,
                "adaptive_net_terminal",
                "Adaptive Net Terminal",
                5);
            GameRegistry.addShapedRecipe(
                adaptiveNetTerminal.getStackForm(1L),
                "ece",
                "rgr",
                "ece",
                'e',
                gregtech.api.enums.ItemList.Hull_EV.get(1),
                'g',
                net.minecraft.init.Blocks.glass,
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Advanced.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] AdaptiveNetTerminal registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        try {
            adaptiveNetHatch = new AdaptiveNetHatch(
                32102,
                "adaptive_net_hatch",
                "Adaptive Net Hatch",
                5);
            GameRegistry.addShapedRecipe(
                adaptiveNetHatch.getStackForm(1L),
                "ehe",
                "rcr",
                "ehe",
                'e',
                gregtech.api.enums.ItemList.Hull_EV.get(1),
                'h',
                gregtech.api.enums.ItemList.Hatch_Energy_EV.get(1),
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Advanced.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] AdaptiveNetHatch registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        try {
            adaptiveNetLaserHatch = new AdaptiveNetLaserHatch(
                32103,
                "adaptive_net_laser_hatch",
                "Adaptive Net Laser Hatch",
                6);
            GameRegistry.addShapedRecipe(
                adaptiveNetLaserHatch.getStackForm(1L),
                "ehe",
                "rcr",
                "ehe",
                'e',
                gregtech.api.enums.ItemList.Hull_LuV.get(1),
                'h',
                gregtech.api.enums.ItemList.Hatch_Energy_LuV.get(1),
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Master.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] AdaptiveNetLaserHatch registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        try {
            adaptiveNetDynamoHatch = new AdaptiveNetDynamoHatch(
                32104,
                "adaptive_net_dynamo_hatch",
                "Adaptive Net Dynamo Hatch",
                5);
            GameRegistry.addShapedRecipe(
                adaptiveNetDynamoHatch.getStackForm(1L),
                "ehe",
                "rcr",
                "ehe",
                'e',
                gregtech.api.enums.ItemList.Hull_EV.get(1),
                'h',
                gregtech.api.enums.ItemList.Hatch_Dynamo_EV.get(1),
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Advanced.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] AdaptiveNetDynamoHatch registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        try {
            adaptiveNetLaserTargetHatch = new AdaptiveNetLaserTargetHatch(
                32105,
                "adaptive_net_laser_target_hatch",
                "Adaptive Net Laser Target Hatch",
                6);
            GameRegistry.addShapedRecipe(
                adaptiveNetLaserTargetHatch.getStackForm(1L),
                "ehe",
                "rcr",
                "ehe",
                'e',
                gregtech.api.enums.ItemList.Hull_LuV.get(1),
                'h',
                gregtech.api.enums.ItemList.Hatch_Dynamo_LuV.get(1),
                'r',
                net.minecraft.init.Items.redstone,
                'c',
                gregtech.api.enums.ItemList.Circuit_Master.get(1));
        } catch (Throwable t) {
            System.err.println("[AE2QoL] AdaptiveNetLaserTargetHatch registration FAILED: " + t);
            t.printStackTrace(System.err);
        }



        try {
            wirelessEnergyInputTerminal = new WirelessEnergyInputTerminal(
                32111,
                "wireless_energy_input_terminal",
                "Wireless Energy Input Terminal",
                5);
            GameRegistry.addShapedRecipe(
                wirelessEnergyInputTerminal.getStackForm(1L),
                "wrw",
                "ege",
                "wrw",
                'w',
                net.minecraft.init.Items.gold_ingot,
                'r',
                net.minecraft.init.Items.redstone,
                'e',
                gregtech.api.enums.ItemList.Hull_EV.get(1),
                'g',
                net.minecraft.init.Blocks.glass);
        } catch (Throwable t) {
            System.err.println("[AE2QoL] WirelessEnergyInputTerminal registration FAILED: " + t);
            t.printStackTrace(System.err);
        }

        try {
            wirelessEnergyOutputTerminal = new WirelessEnergyOutputTerminal(
                32110,
                "wireless_energy_output_terminal",
                "Wireless Energy Output Terminal",
                5);
            GameRegistry.addShapedRecipe(
                wirelessEnergyOutputTerminal.getStackForm(1L),
                "wrw",
                "ege",
                "wrw",
                'w',
                net.minecraft.init.Items.gold_ingot,
                'r',
                net.minecraft.init.Items.redstone,
                'e',
                gregtech.api.enums.ItemList.Hull_EV.get(1),
                'g',
                net.minecraft.init.Blocks.glass);
        } catch (Throwable t) {
            System.err.println("[AE2QoL] WirelessEnergyOutputTerminal registration FAILED: " + t);
            t.printStackTrace(System.err);
        }
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandAe2QoL());
    }

    /**
     * 服务器/单机世界停止时的统一清理（P1-012/P1-021）。
     * 保存自适应电网统计后清空静态网络与无线频道注册表，
     * 防止单机切换存档后旧世界的终端、仓室、频道与方块链接残留。
     */
    public void serverStopping(cpw.mods.fml.common.event.FMLServerStoppingEvent event) {
        try {
            com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetworkManager.shutdown();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] adaptive shutdown cleanup failed", t);
        }
        try {
            com.wztwzt.ae2_qof.wireless.WirelessData.instance()
                .clear();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] wireless registry cleanup failed", t);
        }
    }

    // ===== S2C 包客户端处理分发（#74）=====
    // 专用服务器 JVM 没有 client 类：S2C Handler 若直接引用 Minecraft/thePlayer，
    // 注册时 Class.newInstance 触发类验证即抛 NoClassDefFoundError，导致网络包半注册。
    // Handler 只经 MyMod.proxy（声明类型 CommonProxy）分发；真逻辑在 ClientProxy override。
    // 消息类本体仅含 ItemStack/NBT 等 common 类型，此处 import 安全。

    public void handleProvidersList(ProvidersListS2CPacket message) {}

    public void handleWirelessChannelSync(WirelessChannelSyncPacket message) {}

    public void handleWirelessHighlight(WirelessHighlightPacket message) {}

    public void handleHatchListSync(HatchListSyncPacket message) {}

    public void handleSwapPattern(SwapPatternPacket message) {}

    public void handleCraftingResponse(CraftingResponsePacket message) {}

    public void handleCraftingComplete(CraftingCompletePacket message) {}

    public void handleConfigUpdate(ConfigUpdatePacket message) {}

    public void handleMergedTerminalResult(MergedTerminalResultPacket message) {}

    public void handleMergedTerminalBlankCount(MergedTerminalBlankCountPacket message) {}

    public void handleReplaceCandidates(ReplaceCandidatesPacket message) {}

    private void registerRecipes() {
        try {
            if (blockExIOPort != null) {
                GameRegistry.addShapedRecipe(
                    blockExIOPort.stack(),
                    "igi",
                    "rdr",
                    "igi",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'g',
                    net.minecraft.init.Blocks.glass,
                    'r',
                    net.minecraft.init.Items.redstone,
                    'd',
                    net.minecraft.init.Items.diamond);
            }
            if (blockMergedTerminal != null) {
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(blockMergedTerminal),
                    "igi",
                    "rdr",
                    "iai",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'g',
                    net.minecraft.init.Blocks.glass,
                    'r',
                    net.minecraft.init.Items.redstone,
                    'd',
                    net.minecraft.init.Items.diamond,
                    'a',
                    net.minecraft.init.Items.paper);
            }
            if (blockQuestDetector != null) {
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(blockQuestDetector),
                    "igi",
                    "rbr",
                    "igi",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'g',
                    net.minecraft.init.Blocks.glass,
                    'r',
                    net.minecraft.init.Items.redstone,
                    'b',
                    net.minecraft.init.Items.book);
            }
            if (WirelessBlocks.blockWirelessTransceiver != null) {
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(WirelessBlocks.blockWirelessTransceiver),
                    "iii",
                    "rgr",
                    "iii",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'g',
                    net.minecraft.init.Items.gold_ingot,
                    'r',
                    net.minecraft.init.Items.redstone);
            }
            if (WirelessBlocks.itemWirelessConnector != null) {
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(WirelessBlocks.itemWirelessConnector),
                    " i ",
                    "grd",
                    "   ",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'g',
                    net.minecraft.init.Items.gold_ingot,
                    'r',
                    net.minecraft.init.Items.redstone,
                    'd',
                    net.minecraft.init.Items.diamond);
            }
            if (itemPartMergedTerminal != null && blockMergedTerminal != null) {
                // 部件形态：方块形态 + 铁锭简单合成（对齐原版终端部件与方块的成本关系）
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(itemPartMergedTerminal),
                    "i",
                    "b",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'b',
                    new net.minecraft.item.ItemStack(blockMergedTerminal));
            }
            if (itemWirelessMergedTerminal != null) {
                GameRegistry.addShapedRecipe(
                    new net.minecraft.item.ItemStack(itemWirelessMergedTerminal),
                    "dri",
                    "iei",
                    "iii",
                    'i',
                    net.minecraft.init.Items.iron_ingot,
                    'g',
                    net.minecraft.init.Items.gold_ingot,
                    'r',
                    net.minecraft.init.Items.redstone,
                    'd',
                    net.minecraft.init.Items.diamond,
                    'e',
                    net.minecraft.init.Blocks.diamond_block);
            }
        } catch (Throwable t) {
            MyMod.LOG.error("[APU] recipe registration FAILED", t);
            t.printStackTrace(System.err);
        }
    }
}
