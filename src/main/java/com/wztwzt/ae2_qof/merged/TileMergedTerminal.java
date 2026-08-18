package com.wztwzt.ae2_qof.merged;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridNode;
import appeng.api.parts.IInterfaceTerminal;
import appeng.core.localization.GuiText;
import appeng.tile.grid.AENetworkTile;

/**
 * 样板与接口二合一终端（有线方块）。仅作为一个标准网格节点挂入 ME 网络，
 * 打开终端时由容器收集网络内所有可用的接口/样板供应器。
 */
public class TileMergedTerminal extends AENetworkTile implements IInterfaceTerminal {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public GuiText getName() {
        return GuiText.InterfaceTerminal;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return super.getGridNode(dir);
    }

    @Override
    public IGridNode getActionableNode() {
        return super.getActionableNode();
    }
}
