package com.wztwzt.ae2_qof;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wztwzt.ae2_qof.network.ConfigUpdatePacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

@Mod(
    modid = MyMod.MODID,
    version = Tags.VERSION,
    name = "AE2 QoL",
    acceptedMinecraftVersions = "[1.7.10]",
    guiFactory = "com.wztwzt.ae2_qof.client.gui.ConfigGuiFactory",
    dependencies = "required-after:appliedenergistics2")
public class MyMod {

    @Mod.Instance(MyMod.MODID)
    public static MyMod instance;

    public static final String MODID = "ae2_qof";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.wztwzt.ae2_qof.ClientProxy",
        serverSide = "com.wztwzt.ae2_qof.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        try {
            ModNetwork.registerPackets();
        } catch (Throwable t) {
            LOG.error("[DIAG] ModNetwork.registerPackets() FAILED", t);
            t.printStackTrace(System.err);
        }
        // 玩家登录时推送当前配置，供配置页面显示服务端真实值（该事件走 FML 总线，仅服务端触发）。
        FMLCommonHandler.instance().bus().register(new ConfigSyncHandler());
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    /** 玩家登录时向该玩家推送当前配置（服务端）。 */
    public static class ConfigSyncHandler {

        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
            try {
                if (event.player != null && !event.player.worldObj.isRemote && event.player instanceof EntityPlayerMP) {
                    ModNetwork.CHANNEL.sendTo(
                        new ConfigUpdatePacket(
                            Config.exIOPortTransferContentsRate,
                            Config.smartDoublingMaxRounds,
                            Config.neiOverlayEnabled),
                        (EntityPlayerMP) event.player);
                }
            } catch (Throwable t) {
                LOG.warn("[AE2QoL] config sync failed: " + t.getMessage());
            }
        }
    }
}
