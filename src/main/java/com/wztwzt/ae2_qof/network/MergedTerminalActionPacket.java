package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端面板操作（C2S）：编码/清空/倍增/模式/替代/NEI 填充。
 * 编码成功后服务端回传机器中文名（见 {@link MergedTerminalResultPacket}）。
 */
public class MergedTerminalActionPacket implements IMessage {

    public enum Action {
        ENCODE,
        CLEAR,
        DOUBLE,
        SET_MODE,
        SET_SUBSTITUTE,
        SET_BE_SUBSTITUTE,
        FILL,
        SET_INVERTED,
        SET_PAGE
    }

    private Action action;
    private boolean crafting;
    private boolean substitute;
    private boolean beSubstitute;
    private int value;
    private ItemStack[] inputs = new ItemStack[0];
    private ItemStack[] outputs = new ItemStack[0];

    public MergedTerminalActionPacket() {
        this.action = Action.CLEAR;
    }

    public static MergedTerminalActionPacket value(Action action, int value) {
        MergedTerminalActionPacket p = new MergedTerminalActionPacket();
        p.action = action;
        p.value = value;
        return p;
    }

    public static MergedTerminalActionPacket flag(Action action, boolean value) {
        MergedTerminalActionPacket p = new MergedTerminalActionPacket();
        p.action = action;
        p.crafting = value;
        return p;
    }

    public static MergedTerminalActionPacket encode(boolean crafting, boolean substitute, boolean beSubstitute) {
        MergedTerminalActionPacket p = new MergedTerminalActionPacket();
        p.action = Action.ENCODE;
        p.crafting = crafting;
        p.substitute = substitute;
        p.beSubstitute = beSubstitute;
        return p;
    }

    public static MergedTerminalActionPacket fill(ItemStack[] inputs, ItemStack[] outputs, boolean crafting) {
        MergedTerminalActionPacket p = new MergedTerminalActionPacket();
        p.action = Action.FILL;
        p.crafting = crafting;
        p.inputs = inputs != null ? inputs : new ItemStack[0];
        p.outputs = outputs != null ? outputs : new ItemStack[0];
        return p;
    }

    public static MergedTerminalActionPacket simple(Action action) {
        MergedTerminalActionPacket p = new MergedTerminalActionPacket();
        p.action = action;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int ordinal = buf.readByte();
            this.action = ordinal >= 0 && ordinal < Action.values().length ? Action.values()[ordinal] : Action.CLEAR;
            this.crafting = buf.readBoolean();
            this.substitute = buf.readBoolean();
            this.beSubstitute = buf.readBoolean();
            this.value = buf.readInt();
            int inLen = buf.readInt();
            if (inLen < 0 || inLen > 64) {
                inLen = 0;
            }
            this.inputs = new ItemStack[inLen];
            for (int i = 0; i < inLen; i++) {
                this.inputs[i] = ByteBufUtils.readItemStack(buf);
            }
            int outLen = buf.readInt();
            if (outLen < 0 || outLen > 64) {
                outLen = 0;
            }
            this.outputs = new ItemStack[outLen];
            for (int i = 0; i < outLen; i++) {
                this.outputs[i] = ByteBufUtils.readItemStack(buf);
            }
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.action = Action.CLEAR;
            this.inputs = new ItemStack[0];
            this.outputs = new ItemStack[0];
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.action.ordinal());
        buf.writeBoolean(this.crafting);
        buf.writeBoolean(this.substitute);
        buf.writeBoolean(this.beSubstitute);
        buf.writeInt(this.value);
        buf.writeInt(this.inputs.length);
        for (ItemStack s : this.inputs) {
            ByteBufUtils.writeItemStack(buf, s);
        }
        buf.writeInt(this.outputs.length);
        for (ItemStack s : this.outputs) {
            ByteBufUtils.writeItemStack(buf, s);
        }
    }

    public static class Handler implements IMessageHandler<MergedTerminalActionPacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalActionPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 container/inventory
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    handleMessage(player, message);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
            return null;
        }

        private void handleMessage(EntityPlayerMP player, MergedTerminalActionPacket message) {
            Container container = player.openContainer;
            if (!(container instanceof IMergedPatternTerminal merged)) {
                return;
            }

            switch (message.action) {
                case ENCODE: {
                    merged.setMergedCraftingMode(message.crafting);
                    merged.setMergedSubstitute(message.substitute);
                    merged.setMergedBeSubstitute(message.beSubstitute);
                    String name = merged.mergedEncode();
                    if (name != null && !name.isEmpty()) {
                        ModNetwork.CHANNEL.sendTo(new MergedTerminalResultPacket(name), player);
                    }
                    break;
                }
                case CLEAR:
                    merged.mergedClear();
                    break;
                case DOUBLE:
                    merged.mergedDoubleStacks();
                    break;
                case SET_MODE:
                    merged.setMergedCraftingMode(message.crafting);
                    break;
                case SET_SUBSTITUTE:
                    merged.setMergedSubstitute(message.substitute);
                    break;
                case SET_BE_SUBSTITUTE:
                    merged.setMergedBeSubstitute(message.substitute);
                    break;
                case SET_INVERTED:
                    merged.setMergedInverted(message.crafting);
                    break;
                case SET_PAGE:
                    merged.setMergedActivePage(message.value);
                    break;
                case FILL:
                    merged.mergedFill(message.inputs, message.outputs, message.crafting);
                    break;
                default:
                    break;
            }
        }
    }
}
