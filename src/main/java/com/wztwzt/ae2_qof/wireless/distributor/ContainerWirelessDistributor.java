package com.wztwzt.ae2_qof.wireless.distributor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;

/**
 * 无线分配器容器：没有物品槽，只承载 GUI 生命周期与服务端 Tile 引用。
 */
public class ContainerWirelessDistributor extends Container {

    public final TileWirelessDistributor tile;

    public ContainerWirelessDistributor(InventoryPlayer playerInventory, TileWirelessDistributor tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile != null && !tile.isInvalid();
    }
}
