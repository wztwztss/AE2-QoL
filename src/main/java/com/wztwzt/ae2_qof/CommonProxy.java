package com.wztwzt.ae2_qof;

import com.wztwzt.ae2_qof.block.BlockExIOPort;
import com.wztwzt.ae2_qof.item.ItemInfinityWaterLavaCell;
import com.wztwzt.ae2_qof.merged.BlockMergedTerminal;
import com.wztwzt.ae2_qof.merged.MergedGuiHandler;
import com.wztwzt.ae2_qof.merged.TileMergedTerminal;
import com.wztwzt.ae2_qof.tile.TileExIOPort;
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
    public static ItemInfinityWaterLavaCell itemInfinityWaterLavaCell;
    public static BlockMergedTerminal blockMergedTerminal;
    public static com.wztwzt.ae2_qof.merged.part.ItemPartMergedTerminal itemPartMergedTerminal;
    public static com.wztwzt.ae2_qof.merged.wireless.ItemWirelessMergedTerminal itemWirelessMergedTerminal;

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
            GameRegistry.registerBlock(blockExIOPort, appeng.block.AEBaseItemBlock.class, "ex_io_port");
            GameRegistry.registerTileEntity(TileExIOPort.class, "ex_io_portTile");
        } catch (Throwable t) {
            MyMod.LOG.error("[DIAG] BlockExIOPort registration FAILED", t);
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
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(MyMod.instance, new MergedGuiHandler());
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new WirelessBlockEventListener());
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandAe2QoL());
    }

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
