package com.wztwzt.ae2_qof.util;

import java.lang.reflect.Field;

import net.minecraft.inventory.Container;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;

import appeng.api.networking.security.IActionHost;
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
            // 3.5.0 起合并终端为独立 AEBaseContainer 子类，anchor 是其自有字段
            // （IInterfaceTerminal extends IActionHost），不再是 AE2 ContainerInterfaceTerminal 子类；
            // 反射需沿类层级查找自有字段，写死原生容器类会导致上传/撤回服务端解析永远失败
            try {
                Field field = findDeclaredField(container.getClass(), "anchor");
                field.setAccessible(true);
                Object anchor = field.get(container);
                if (anchor instanceof IActionHost host) {
                    return host;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findDeclaredField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    public static boolean isMerged(Container container) {
        return container instanceof IMergedPatternTerminal;
    }
}
