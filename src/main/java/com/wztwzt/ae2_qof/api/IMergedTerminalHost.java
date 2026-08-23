package com.wztwzt.ae2_qof.api;

import appeng.api.parts.IInterfaceTerminal;
import appeng.tile.inventory.AppEngInternalInventory;

/**
 * 二合一终端三形态（方块 / 线缆面板部件 / 手持无线）的统一宿主接口。
 * <p>
 * Container/Gui 仅依赖本接口，业务逻辑（PatternContainer 面板编辑、上传/撤回/交换包）
 * 对宿主形态零感知。三种形态的差异只在数据落盘位置：
 * <ul>
 * <li>方块：TileEntity NBT</li>
 * <li>部件：经 part.writeToNBT 存入线缆 TileEntity</li>
 * <li>无线：物品 ItemStack NBT（关容器时回写）</li>
 * </ul>
 */
public interface IMergedTerminalHost extends IInterfaceTerminal {

    /** 样板面板的空白(0)/已编码(1)样板槽 */
    AppEngInternalInventory getPatternInv();

    /** 面板编辑快照：0-8 合成3×3 | 9-40 扩展输入32 | 41-72 输出32 */
    AppEngInternalInventory getSavedGrid();

    boolean getSavedCraftingMode();

    void setSavedCraftingMode(boolean v);

    /**
     * 持久化脏标记：方块 markDirty / 部件 host.markForSave / 无线物品 NBT 回写。
     * 上传/撤回等网络包写入样板槽后必须调用。
     */
    void markPersistDirty();
}
