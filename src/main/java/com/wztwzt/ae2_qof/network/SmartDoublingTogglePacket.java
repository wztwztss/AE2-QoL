package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.ISmartDoublingContainer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S: 切换 ME 接口的智能倍增开关。服务端归队后写回容器与接口介质。
 */
public class SmartDoublingTogglePacket implements IMessage {

    private boolean enabled;

    public SmartDoublingTogglePacket() {}

    public SmartDoublingTogglePacket(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }

    public static class Handler implements IMessageHandler<SmartDoublingTogglePacket, IMessage> {

        @Override
        public IMessage onMessage(SmartDoublingTogglePacket message, MessageContext ctx) {
            final EntityPlayer player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }
            final boolean enabled = message.enabled;
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    Container c = player.openContainer;
                    if (c instanceof ISmartDoublingContainer sdc) {
                        sdc.setSmartDoubling(enabled);
                    }
                } catch (Throwable t) {
                    MyMod.LOG.error("Smart doubling toggle failed", t);
                }
            });
            return null;
        }
    }
}
