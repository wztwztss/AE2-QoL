package com.wztwzt.ae2_qof.network;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.client.gui.GuiSlotSettings;
import com.wztwzt.ae2_qof.wildcard.SlotSettings;
import com.wztwzt.ae2_qof.wildcard.SlotSettingsAccess;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 「按格设置」权威回读（S2C，4.0.0）：服务端把该格当前状态（电路 + 9 催化剂位）发给客户端。
 *
 * <p>客户端收到后：若"格设置"弹窗正开着且坐标/槽位匹配，就直接把状态填进弹窗（权威覆盖本地编辑）。
 */
public class SlotSettingsSyncPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private int slot;
    private int circuit;
    private boolean circuitExplicit;
    private ItemStack[] catalysts = new ItemStack[SlotSettings.CATALYST_SLOTS];

    public SlotSettingsSyncPacket() {}

    public SlotSettingsSyncPacket(int x, int y, int z, int slot, int circuit, boolean circuitExplicit,
        ItemStack[] catalysts) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
        this.circuit = circuit;
        this.circuitExplicit = circuitExplicit;
        this.catalysts = catalysts;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.slot = buf.readInt();
            this.circuit = buf.readInt();
            this.circuitExplicit = buf.readBoolean();
            this.catalysts = SlotSettingsAccess.readStacks(buf);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 按格设置回读包解析失败（已忽略）", t);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.slot);
        buf.writeInt(this.circuit);
        buf.writeBoolean(this.circuitExplicit);
        SlotSettingsAccess.writeStacks(buf, this.catalysts);
    }

    public static class Handler implements IMessageHandler<SlotSettingsSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(SlotSettingsSyncPacket message, MessageContext ctx) {
            try {
                GuiSlotSettings.applyServerState(
                    message.x,
                    message.y,
                    message.z,
                    message.slot,
                    message.circuit,
                    message.circuitExplicit,
                    message.catalysts);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 应用按格设置回读失败", t);
            }
            return null;
        }
    }
}
