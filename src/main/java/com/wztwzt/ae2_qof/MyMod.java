package com.wztwzt.ae2_qof;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    dependencies = "required-after:appliedenergistics2;after:guidenh")
public class MyMod {

    @Mod.Instance(MyMod.MODID)
    public static MyMod instance;

    public static final String MODID = "ae2_qof";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.wztwzt.ae2_qof.ClientProxy", serverSide = "com.wztwzt.ae2_qof.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        // 网络包注册必须完整：半注册（部分 discriminator 缺失）会导致玩家操作时被
        // "Undefined message for discriminator N" 踢出服务器，且极难排查（#74）。
        // 因此这里 fail-fast——注册失败直接抛出，宁可启动失败也不带病运行。
        ModNetwork.registerPackets();
        // 玩家登录时推送当前配置，供配置页面显示服务端真实值（该事件走 FML 总线，仅服务端触发）。
        FMLCommonHandler.instance()
            .bus()
            .register(new ConfigSyncHandler());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
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
