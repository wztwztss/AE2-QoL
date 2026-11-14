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

    /**
     * @return 是否成功写入 settings.json；失败时开关保持原值
     */
    public static boolean setEnabled(boolean enabled) {
        return Config.setNeiOverlayEnabled(enabled);
    }
}
