/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.network;

import com.wztwzt.ae2_qof.MyMod;


import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class WildcardNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MyMod.MODID);

    private WildcardNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(MessageUpdateWildcardConfig.Handler.class, MessageUpdateWildcardConfig.class, 0, Side.SERVER);
        CHANNEL.registerMessage(
            MessageUpdateCompositeWildcardConfig.Handler.class,
            MessageUpdateCompositeWildcardConfig.class,
            1,
            Side.SERVER);
    }
}
