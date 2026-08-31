package com.wztwzt.ae2_qof;

import com.wztwzt.ae2_qof.block.BlockExIOPort;
import com.wztwzt.ae2_qof.block.BlockQuestDetector;
import com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal;
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
    public static AdaptiveNetTerminal adaptiveNetTerminal;
    public static AdaptiveNetHatch adaptiveNetHatch;
    public static AdaptiveNetLaserHatch adaptiveNetLaserHatch;
    public static AdaptiveNetDynamoHatch adaptiveNetDynamoHatch;
    public static AdaptiveNetLaserTargetHatch adaptiveNetLaserTargetHatch;

    public static WirelessEnergyInputTerminal wirelessEnergyInputTerminal;
    public static WirelessEnergyOutputTerminal wirelessEnergyOutputTerminal;
    public static ItemNetworkDataStick networkDataStick;

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        MyMod.LOG.info(Config.greeting);
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
            MyMod.LOG.error("[DIAG] AE2MaintenanceHatchUniversal registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] AdaptiveNetTerminal registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] AdaptiveNetHatch registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] AdaptiveNetLaserHatch registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] AdaptiveNetDynamoHatch registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] AdaptiveNetLaserTargetHatch registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] WirelessEnergyInputTerminal registration FAILED", t);
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
            MyMod.LOG.error("[DIAG] WirelessEnergyOutputTerminal registration FAILED", t);
            t.printStackTrace(System.err);
        }
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandAe2QoL());
    }

    // ===== S2C 包客户端处理分发（#74）=====
    // 专用服务器 JVM 没有 client 类：S2C Handler 若直接引用 Minecraft/thePlayer，
    // 注册时 Class.newInstance 触发类验证即抛 NoClassDefFoundError，导致网络包半注册。
    // Handler 只经 MyMod.proxy（声明类型 CommonProxy）分发；真逻辑在 ClientProxy override。
    // 消息类本体仅含 ItemStack/NBT 等 common 类型，此处 import 安全。

    public void handleProvidersList(ProvidersListS2CPacket message) {}

    public void handleWirelessChannelSync(WirelessChannelSyncPacket message) {}

    public void handleWirelessHighlight(WirelessHighlightPacket message) {}

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
