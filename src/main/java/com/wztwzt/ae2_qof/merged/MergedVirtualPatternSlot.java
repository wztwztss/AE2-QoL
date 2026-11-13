package com.wztwzt.ae2_qof.merged;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMESlot;

/**
 * F 面板的只读虚拟槽：只负责按 AE2 原生 {@link VirtualMESlot} 的规则绘制
 * 物品/流体图标与数量，不接管点击与同步。
 *
 * <p>当前 F 终端仍保留真实槽位处理点击、NEI 拖拽和容器同步；本类只把真实槽里的
 * 展示栈转成 AE 栈后交给 AE2 的绘制逻辑，避免流体/大量物品在真实槽里显示成普通物品。
 */
public final class MergedVirtualPatternSlot extends VirtualMESlot {

    private final IAEStack<?> stack;

    public MergedVirtualPatternSlot(int x, int y, IAEStack<?> stack) {
        super(x, y, 0);
        this.stack = stack;
        setShowAmount(true);
    }

    @Override
    public IAEStack<?> getAEStack() {
        return stack;
    }
}
