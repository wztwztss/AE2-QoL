package com.wztwzt.ae2_qof.api;

/**
 * 智能倍增（Smart Doubling）的容器侧接口。
 * <p>
 * 由 ME 接口的 {@code ContainerInterface} 实现，供客户端 GUI / 服务端数据包以编译期安全
 * 的方式读写倍增开关（其真正状态存储在 {@code DualityInterface} 上）。
 */
public interface ISmartDoublingContainer {

    /**
     * @return 当前容器（已被服务端同步）的倍增开关状态。
     */
    boolean getSmartDoubling();

    /**
     * 设置倍增开关，并立即写回接口介质（DualityInterface）持久化。
     *
     * @param enabled 是否启用
     */
    void setSmartDoubling(boolean enabled);
}
