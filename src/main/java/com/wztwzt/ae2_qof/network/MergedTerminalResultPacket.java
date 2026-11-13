package com.wztwzt.ae2_qof.network;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端编码成功后的机器中文名回传（S2C），用于面板即时反馈显示。
 * 处理配方若无中文映射（needsMapping=true），客户端自动弹出供应器选择/映射页引导玩家命名。
 * Handler 仅作分发：客户端真逻辑在 ClientProxy.handleMergedTerminalResult（#74）。
 */
public class MergedTerminalResultPacket implements IMessage {

    public String machineName;
    public String recipeMap;
    public boolean needsMapping;
    /** true 表示这次编码由 NEI 自动上传流程触发，客户端应在产物同步后调用上传管线 */
    public boolean autoUpload;

    public MergedTerminalResultPacket() {
        this.machineName = "";
        this.recipeMap = null;
        this.needsMapping = false;
        this.autoUpload = false;
    }

    public MergedTerminalResultPacket(String machineName) {
        this(machineName, null, false, false);
    }

    public MergedTerminalResultPacket(String machineName, String recipeMap, boolean needsMapping) {
        this(machineName, recipeMap, needsMapping, false);
    }

    public MergedTerminalResultPacket(String machineName, String recipeMap, boolean needsMapping,
        boolean autoUpload) {
        this.machineName = machineName != null ? machineName : "";
        this.recipeMap = recipeMap;
        this.needsMapping = needsMapping;
        this.autoUpload = autoUpload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int len = buf.readInt();
            if (len < 0 || len > 4096) {
                this.machineName = "";
                this.recipeMap = null;
                this.needsMapping = false;
                return;
            }
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.machineName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            this.needsMapping = buf.readBoolean();
            boolean hasMap = buf.readBoolean();
            this.recipeMap = null;
            if (hasMap) {
                int mapLen = buf.readInt();
                if (mapLen > 0 && mapLen <= 2048) {
                    byte[] mapBytes = new byte[mapLen];
                    buf.readBytes(mapBytes);
                    this.recipeMap = new String(mapBytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            this.autoUpload = buf.isReadable() && buf.readBoolean();
        } catch (Throwable t) {
            this.machineName = "";
            this.recipeMap = null;
            this.needsMapping = false;
            this.autoUpload = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = this.machineName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        buf.writeBoolean(this.needsMapping);
        buf.writeBoolean(this.recipeMap != null);
        if (this.recipeMap != null) {
            byte[] mapBytes = this.recipeMap.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(mapBytes.length);
            buf.writeBytes(mapBytes);
        }
        buf.writeBoolean(this.autoUpload);
    }

    public static class Handler implements IMessageHandler<MergedTerminalResultPacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalResultPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleMergedTerminalResult(message);
            }
            return null;
        }
    }
}
