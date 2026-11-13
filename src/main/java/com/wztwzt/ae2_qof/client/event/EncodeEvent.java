package com.wztwzt.ae2_qof.client.event;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * NEI 侧“Alt + 编码按钮”兼容事件，移植自 AE2Things 1.3.0 `EncodeEvent`。
 * 当前 F 终端没有内置监听者；保留事件是让外部 NEI 集成/附属模组可以按上游语义接住。
 */
public class EncodeEvent extends Event {

    public static boolean encode = false;

    public EncodeEvent() {}

    public EncodeEvent(boolean encode) {
        EncodeEvent.encode = encode;
    }
}
