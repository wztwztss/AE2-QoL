package com.wztwzt.ae2_qof.merged.slot;

import net.minecraft.inventory.IInventory;

import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;

/**
 * 处理模式扩展槽，移植自 AE2Things SlotPatternFake。
 * <p>
 * 通过 ±9000 偏移隐藏/显示槽位（替代 SlotHidden 机制）。
 */
public class SlotPatternFake extends OptionalSlotFake {

    private static final int POSITION_SHIFT = 9000;
    private boolean hidden = false;

    public SlotPatternFake(IInventory inv, IOptionalSlotHost host, int idx, int x, int y, int offX, int offY,
        int groupNum) {
        super(inv, host, idx, x, y, offX, offY, groupNum);
        this.setRenderDisabled(false);
    }

    public void setHidden(boolean hide) {
        if (this.hidden != hide) {
            this.hidden = hide;
            this.xDisplayPosition += (hide ? -1 : 1) * POSITION_SHIFT;
        }
    }

    public boolean isHidden() {
        return this.hidden;
    }
}
