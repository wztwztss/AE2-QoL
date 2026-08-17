package com.wztwzt.ae2_qof.util;

import java.lang.reflect.Field;

import net.minecraft.inventory.Container;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;

import appeng.api.networking.security.IActionHost;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;

/**
 * 从打开容器解析终端 {@link IActionHost}。兼容原生样板终端与二合一接口终端。
 */
public final class ContainerTerminalResolver {

    private ContainerTerminalResolver() {}

    public static IActionHost resolveTerminal(Container container) {
        if (container instanceof ContainerPatternTerm pt) {
            return (IActionHost) pt.getPatternTerminal();
        }
        if (container instanceof ContainerPatternTermEx ptEx) {
            return (IActionHost) ptEx.getPatternTerminal();
        }
        if (container instanceof IMergedPatternTerminal merged) {
            try {
                // ContainerInterfaceTerminal.anchor 为 private final，反射读取；IInterfaceTerminal 继承 IActionHost
                Field field = ContainerInterfaceTerminal.class.getDeclaredField("anchor");
                field.setAccessible(true);
                Object anchor = field.get(container);
                if (anchor instanceof IActionHost host) {
                    return host;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean isMerged(Container container) {
        return container instanceof IMergedPatternTerminal;
    }
}