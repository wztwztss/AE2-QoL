package cn.dancingsnow.aeinfinitycell;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import appeng.api.AEApi;
import cn.dancingsnow.aeinfinitycell.ae.InfinityCellChannelSupport;
import cn.dancingsnow.aeinfinitycell.ae.InfinityCellHandler;
import cn.dancingsnow.aeinfinitycell.item.ModItems;
import cn.dancingsnow.aeinfinitycell.nei.NEIHandlerInfoRegistration;
import cn.dancingsnow.aeinfinitycell.recipe.ModRecipeLoader;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellDataAccess;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommonProxy {

    private static final String APPEU_MOD_ID = "appeu";
    private static final String APPEU_INTEGRATION_CLASS = "cn.dancingsnow.aeinfinitycell.integration.appeu.AppEUInfinityCellChannelSupport";

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        ModItems.register();

        AEInfinityCell.LOG.info(Config.greeting);
        AEInfinityCell.LOG.info("AE2 Infinity Cell version " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        InfinityCellHandler cellHandler = new InfinityCellHandler();
        if (Loader.isModLoaded(APPEU_MOD_ID)) {
            // 吞并版：appeu 集成类未随源码搬运，检测到 appeu 时仅提示跳过（EU 通道不可用）
            try {
                cellHandler.addChannelSupport(loadChannelSupport(APPEU_INTEGRATION_CLASS));
                AEInfinityCell.LOG.info("Enabled AppEU EU storage support");
            } catch (Throwable t) {
                AEInfinityCell.LOG.warn("AppEU detected but its integration class is absent in this build; EU channel disabled: " + t);
            }
        }
        AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(cellHandler);
        NEIHandlerInfoRegistration.sendCellViewHandlerInfo();
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void postInit(FMLPostInitializationEvent event) {
        ModRecipeLoader.loadRecipes();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        ServerWorldAccess.setServer(event.getServer());
        InfinityCellDataAccess.migrateLegacy(null);
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        InfinityCellStorage.getInstance()
            .saveAll();
        InfinityCellStorage.getInstance()
            .clear();
        ServerWorldAccess.clear();
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (event.world.provider.dimensionId == 0) {
            InfinityCellStorage.getInstance()
                .saveAll();
        }
    }

    private static InfinityCellChannelSupport loadChannelSupport(String className) {
        try {
            return Class.forName(className)
                .asSubclass(InfinityCellChannelSupport.class)
                .getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                "Failed to initialize optional storage channel support: " + className,
                exception);
        }
    }
}
