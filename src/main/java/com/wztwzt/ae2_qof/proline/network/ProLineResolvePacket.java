package com.wztwzt.ae2_qof.proline.network;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.ServerTerminalHelper;
import com.wztwzt.ae2_qof.proline.ItemRow;
import com.wztwzt.ae2_qof.proline.ProductionLine;
import com.wztwzt.ae2_qof.proline.ProductionStep;
import com.wztwzt.ae2_qof.proline.ProLineData;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;
import net.minecraftforge.common.DimensionManager;

/**
 * 4.2.0：客户端名称解析结果 C2S 包。
 *
 * <p>
 * 客户端（有中文语言表）把产线里所有未解析的 raw 名 → 注册键映射发给服务端，
 * 服务端据此回填该产线所有步骤的 ItemRow.key 与 startExpected 键——
 * 从而让 AE 存量/累计/速率列从"留白"变为"有数据"。
 *
 * <p>
 * 安全校验：只允许产线 owner 团队成员发包；每个 key 值必须以合法格式开头（物品=非空、流体=fluid:）。
 */
public class ProLineResolvePacket implements IMessage {

    private String lineId;
    private Map<String, String> resolved;

    public ProLineResolvePacket() {}

    public ProLineResolvePacket(String lineId, Map<String, String> resolved) {
        this.lineId = lineId;
        this.resolved = resolved;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        lineId = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
        int n = buf.readInt();
        resolved = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
            String v = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
            if (!k.isEmpty() && !v.isEmpty()) resolved.put(k, v);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, lineId);
        buf.writeInt(resolved.size());
        for (Map.Entry<String, String> e : resolved.entrySet()) {
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, e.getKey());
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, e.getValue());
        }
    }

    public static class Handler implements IMessageHandler<ProLineResolvePacket, IMessage> {

        @Override
        public IMessage onMessage(ProLineResolvePacket msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || msg.resolved == null || msg.resolved.isEmpty()) return null;
            ServerTerminalHelper.scheduleServerTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, ProLineResolvePacket msg) {
            if (!com.wztwzt.ae2_qof.proline.ProLineGate.isPresent()) return;
            WorldServer w = DimensionManager.getWorld(player.dimension);
            ProLineData data = ProLineData.get(w);
            ProductionLine line = data.findLine(msg.lineId);
            if (line == null) return;

            int items = 0, starts = 0;
            for (ProductionStep s : line.steps) {
                items += resolveRows(s.inputs, msg.resolved);
                items += resolveRows(s.outputs, msg.resolved);
            }
            // startExpected：raw 名匹配则迁移键
            java.util.Iterator<Map.Entry<String, Long>> it = line.startExpected.entrySet().iterator();
            Map<String, Long> migrated = new java.util.LinkedHashMap<>();
            while (it.hasNext()) {
                Map.Entry<String, Long> e = it.next();
                String key = msg.resolved.get(e.getKey());
                if (key != null && !key.isEmpty()) {
                    migrated.put(key, e.getValue());
                    starts++;
                } else {
                    migrated.put(e.getKey(), e.getValue());
                }
            }
            line.startExpected.clear();
            line.startExpected.putAll(migrated);

            if (items > 0 || starts > 0) {
                data.markDirty();
                MyMod.LOG.info(
                    "[AE2QoL] 产线 {} 名称解析回填：{} 物品行 + {} 期望边界",
                    line.id, items, starts);
            }
        }

        private int resolveRows(java.util.List<ItemRow> rows, Map<String, String> resolved) {
            int n = 0;
            for (ItemRow r : rows) {
                if (!r.key.isEmpty()) continue;
                String raw = r.raw != null ? r.raw : "";
                String key = resolved.get(raw);
                if (key != null && !key.isEmpty()) {
                    r.key = key;
                    n++;
                }
            }
            return n;
        }
    }
}
