package com.wztwzt.ae2_qof.client;

import com.wztwzt.ae2_qof.Config;

/**
 * NEI 叠加层开关的客户端门面：读写统一配置 {@code config/ae2_qof/settings.json}。
 * 实际存储委托给 {@link Config}，避免单独写文件时覆盖其它配置项。
 */
public final class OverlayConfig {

    private OverlayConfig() {}

    public static boolean isEnabled() {
        Config.ensureFresh();
        return Config.neiOverlayEnabled;
    }

    public static void setEnabled(boolean enabled) {
        Config.setNeiOverlayEnabled(enabled);
    }
}