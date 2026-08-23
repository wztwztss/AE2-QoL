package com.wztwzt.ae2_qof.merged;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.api.IMergedTerminalHost;
import com.wztwzt.ae2_qof.merged.part.PartMergedTerminal;
import com.wztwzt.ae2_qof.merged.wireless.ItemWirelessMergedTerminal;
import com.wztwzt.ae2_qof.merged.wireless.WirelessMergedGuiObject;
import com.wztwzt.ae2_qof.wireless.BlockWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.TileWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.WirelessGuiHandler;

import appeng.api.parts.IPart;
import cpw.mods.fml.common.network.IGuiHandler;

/**
 * 总 GUI 处理器：合并二合一终端三种形态的 Gui ID 与原有无线收发器 Gui ID。
 * <ul>
 * <li>100：方块形态（x/y/z 定位 Tile）</li>
 * <li>110+side：线缆面板部件形态（x/y/z 为宿主线缆坐标，ID 编码面板朝向）</li>
 * <li>120：手持无线形态（x 参数为终端所在背包槽位号）</li>
 * </ul>
 */
public class MergedGuiHandler implements IGuiHandler {

    private final WirelessGuiHandler wirelessGuiHandler = new WirelessGuiHandler();

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        IMergedTerminalHost host = resolveHost(ID, player, world, x, y, z);
        if (host != null) {
            return new ContainerMergedTerminal(player.inventory, host);
        }
        if (ID == BlockWirelessTransceiver.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileWirelessTransceiver) {
                return wirelessGuiHandler.getServerGuiElement(ID, player, world, x, y, z);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        IMergedTerminalHost host = resolveHost(ID, player, world, x, y, z);
        if (host != null) {
            return new GuiMergedTerminal(player.inventory, host);
        }
        if (ID == BlockWirelessTransceiver.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileWirelessTransceiver) {
                return wirelessGuiHandler.getClientGuiElement(ID, player, world, x, y, z);
            }
        }
        return null;
    }

    /** 按 GUI ID 解析三形态宿主；非合并终端返回 null */
    private IMergedTerminalHost resolveHost(int ID, EntityPlayer player, World world, int x, int y, int z) {
        // 方块形态
        if (ID == BlockMergedTerminal.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            return te instanceof TileMergedTerminal tmt ? tmt : null;
        }
        // 线缆面板部件形态
        if (ID >= BlockMergedTerminal.PART_GUI_BASE && ID < BlockMergedTerminal.PART_GUI_BASE + 6) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof appeng.api.parts.IPartHost cableHost) {
                IPart part = cableHost.getPart(
                    net.minecraftforge.common.util.ForgeDirection
                        .getOrientation(ID - BlockMergedTerminal.PART_GUI_BASE));
                return part instanceof PartMergedTerminal pmt ? pmt : null;
            }
            return null;
        }
        // 手持无线形态：x = 背包槽位号
        if (ID == BlockMergedTerminal.WIRELESS_GUI_ID) {
            ItemStack term = player.inventory.getStackInSlot(x);
            if (term != null && term.getItem() instanceof ItemWirelessMergedTerminal) {
                return new WirelessMergedGuiObject(term, player, x);
            }
        }
        return null;
    }
}
