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
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

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
        // 3.31.0：先把搬运界面的 id 分发给它们的 handler（Wild 窗口 140 / 生成器 101、102）
        Object delegatedServer = delegateUi(false, ID, player, world, x, y, z);
        if (delegatedServer != null) {
            return delegatedServer;
        }
        // 3.22.0：智能通配样板配置界面（手持形态，x = 玩家背包里的槽位号）
        if (ID == com.wztwzt.ae2_qof.wildcard.ItemSmartWildcardPattern.GUI_ID) {
            ItemStack held = player.inventory.getStackInSlot(x);
            if (held != null && held.getItem() instanceof com.wztwzt.ae2_qof.wildcard.ItemSmartWildcardPattern) {
                return new com.wztwzt.ae2_qof.wildcard.ContainerSmartWildcard(player.inventory, x);
            }
            com.wztwzt.ae2_qof.MyMod.LOG
                .warn("[AE2QoL] 打开通配样板界面失败：槽位 {} 里已不是通配样板（player={}）", x, player.getCommandSenderName());
            return null;
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
        // 3.31.0：先把搬运界面的 id 分发给它们的 handler（Wild 窗口 140 / 生成器 101、102）
        Object delegatedClient = delegateUi(true, ID, player, world, x, y, z);
        if (delegatedClient != null) {
            return delegatedClient;
        }
        // 3.22.0：智能通配样板配置界面（与容器同一槽位 ⇒ 是带槽位的 GuiContainer，NEI 加号才认它）
        if (ID == com.wztwzt.ae2_qof.wildcard.ItemSmartWildcardPattern.GUI_ID) {
            return new com.wztwzt.ae2_qof.client.gui.GuiSmartWildcard(
                new com.wztwzt.ae2_qof.wildcard.ContainerSmartWildcard(player.inventory, x));
        }
        if (ID == BlockWirelessTransceiver.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileWirelessTransceiver) {
                return wirelessGuiHandler.getClientGuiElement(ID, player, world, x, y, z);
            }
        }
        return null;
    }

    // ===== 3.31.0：搬运界面的 handler 委托 =====
    // 为什么需要它：`NetworkRegistry.registerGuiHandler` **每个模组只认最后一次调用** —— 我们曾在 CommonProxy 里
    // 分别注册 Wild 与 apgport 的 handler，结果把本类**整个顶掉**（用户实测：右键通配样板打不开界面 ✗）。
    // 现在只注册本类一个，由这里把两套 id 分发给各自的 handler。
    private static com.wztwzt.ae2_qof.wildport.gui.WildcardGuiHandler wildUiHandler;
    private static com.wztwzt.ae2_qof.apgport.gui.GuiHandler apgUiHandler;

    /** 由 CommonProxy 在 init 时注入（这里只保存引用，不做注册）。 */
    public static void setUiHandlers(com.wztwzt.ae2_qof.wildport.gui.WildcardGuiHandler wild,
        com.wztwzt.ae2_qof.apgport.gui.GuiHandler apg) {
        wildUiHandler = wild;
        apgUiHandler = apg;
    }

    /** 两套搬运界面的 id 分发；返回 null 表示"不是它们的 id"。 */
    private Object delegateUi(boolean client, int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (wildUiHandler != null && ID == com.wztwzt.ae2_qof.wildport.WildportIds.GUI_WILDCARD_PATTERN) {
            return client ? wildUiHandler.getClientGuiElement(ID, player, world, x, y, z)
                : wildUiHandler.getServerGuiElement(ID, player, world, x, y, z);
        }
        if (apgUiHandler != null
            && (ID == com.wztwzt.ae2_qof.apgport.item.ItemPatternGenerator.GUI_ID
                || ID == com.wztwzt.ae2_qof.apgport.item.ItemPatternGenerator.GUI_ID_STORAGE)) {
            return client ? apgUiHandler.getClientGuiElement(ID, player, world, x, y, z)
                : apgUiHandler.getServerGuiElement(ID, player, world, x, y, z);
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
