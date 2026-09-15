package com.wztwzt.ae2_qof.cover.stockmonitor.ae;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.networking.IGrid;

public final class WirelessAeConnector {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");

    private static volatile boolean initialized = false;
    private static boolean nexusAvailable = false;

    private static Class<?> clsSavedData;
    private static Class<?> clsService;
    private static Class<?> clsRecord;
    private static Class<?> clsController;

    private static Method mSavedDataGet;
    private static Method mSavedDataGetRecord;
    private static Method mServiceGetVisible;
    private static Method mServiceFindController;
    private static Method mRecordGetName;
    private static Method mRecordGetId;
    private static Method mRecordIsOnline;
    private static Method mControllerGetProxy;
    private static Method mProxyGetNode;

    private WirelessAeConnector() {}

    public static boolean isNexusAvailable() {
        ensureInitialized();
        return nexusAvailable;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getVisibleNetworks(World world, net.minecraft.entity.player.EntityPlayer player) {
        ensureInitialized();
        if (!nexusAvailable) return Collections.emptyList();
        try {
            return (List<Object>) mServiceGetVisible.invoke(null, world, player);
        } catch (Throwable t) {
            LOG.warn("[StockMonitor] getVisibleNetworks reflection failed: " + t.getMessage());
            return Collections.emptyList();
        }
    }

    public static String getRecordName(Object record) {
        ensureInitialized();
        if (!nexusAvailable || record == null) return "";
        try {
            return (String) mRecordGetName.invoke(record);
        } catch (Throwable t) {
            return "";
        }
    }

    public static UUID getRecordId(Object record) {
        ensureInitialized();
        if (!nexusAvailable || record == null) return null;
        try {
            return (UUID) mRecordGetId.invoke(record);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isRecordOnline(Object record) {
        ensureInitialized();
        if (!nexusAvailable || record == null) return false;
        try {
            return (Boolean) mRecordIsOnline.invoke(record);
        } catch (Throwable t) {
            return false;
        }
    }

    public static IGrid getGridForNetwork(UUID networkId, World world) {
        ensureInitialized();
        if (!nexusAvailable || networkId == null) return null;
        try {
            Object savedData = mSavedDataGet.invoke(null, world);
            if (savedData == null) {
                LOG.debug("[StockMonitor] getGridForNetwork: WirelessNetworkSavedData null");
                return null;
            }
            Object record = mSavedDataGetRecord.invoke(savedData, networkId);
            if (record == null) {
                LOG.debug("[StockMonitor] getGridForNetwork: record null for " + networkId);
                return null;
            }
            if (!(Boolean) mRecordIsOnline.invoke(record)) {
                LOG.debug("[StockMonitor] getGridForNetwork: record offline for " + networkId);
                return null;
            }
            Object controller = mServiceFindController.invoke(null, record);
            if (controller == null) {
                LOG.debug("[StockMonitor] getGridForNetwork: findController null for " + networkId);
                return null;
            }
            Object proxy = mControllerGetProxy.invoke(controller);
            if (proxy == null) {
                LOG.debug("[StockMonitor] getGridForNetwork: controller.getProxy() null");
                return null;
            }
            Object node = mProxyGetNode.invoke(proxy);
            if (node == null) {
                LOG.debug("[StockMonitor] getGridForNetwork: proxy.getNode() null");
                return null;
            }
            IGrid grid = (IGrid) node.getClass().getMethod("getGrid").invoke(node);
            if (grid == null) {
                LOG.debug("[StockMonitor] getGridForNetwork: node.getGrid() null");
            }
            return grid;
        } catch (Throwable t) {
            LOG.debug("[StockMonitor] getGridForNetwork failed: " + t);
            return null;
        }
    }

    private static void ensureInitialized() {
        if (initialized) return;
        synchronized (WirelessAeConnector.class) {
            if (initialized) return;
            initialized = true;
            try {
                if (!cpw.mods.fml.common.Loader.isModLoaded("ae_wireless_nexus")) {
                    nexusAvailable = false;
                    return;
                }
                clsSavedData = Class.forName("cn.dancingsnow.ae_wireless_nexus.network.WirelessNetworkSavedData");
                clsService = Class.forName("cn.dancingsnow.ae_wireless_nexus.network.WirelessNetworkService");
                clsRecord = Class.forName("cn.dancingsnow.ae_wireless_nexus.network.WirelessNetworkRecord");
                clsController = Class.forName("cn.dancingsnow.ae_wireless_nexus.tile.TileWirelessController");

                mSavedDataGet = clsSavedData.getMethod("get", World.class);
                mSavedDataGetRecord = clsSavedData.getMethod("get", UUID.class);
                mServiceGetVisible = clsService.getMethod("getVisibleNetworks", World.class,
                    net.minecraft.entity.player.EntityPlayer.class);
                mServiceFindController = clsService.getMethod("findController", clsRecord);
                mRecordGetName = clsRecord.getMethod("getName");
                mRecordGetId = clsRecord.getMethod("getId");
                mRecordIsOnline = clsRecord.getMethod("isOnline");
                mControllerGetProxy = clsController.getMethod("getProxy");

                // AE2UEL 977+: IGridProxyable 移至 appeng.me.helpers，getProxy() 返回 AENetworkProxy
                // 兼容 2.8.x 旧 AE2（appeng.api.networking.IGridProxyable）
                try {
                    Class<?> clsProxy = Class.forName("appeng.me.helpers.AENetworkProxy");
                    mProxyGetNode = clsProxy.getMethod("getNode");
                } catch (Throwable old) {
                    Class<?> clsProxy = Class.forName("appeng.api.networking.IGridProxyable");
                    mProxyGetNode = clsProxy.getMethod("getNode");
                }

                nexusAvailable = true;
                LOG.info("[StockMonitor] AE Wireless Nexus integration loaded successfully");
            } catch (Throwable t) {
                nexusAvailable = false;
                LOG.warn("[StockMonitor] AE Wireless Nexus not available: " + t.getMessage());
            }
        }
    }
}
