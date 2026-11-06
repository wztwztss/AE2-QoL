package com.wztwzt.ae2_qof.machindex.network;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.MachIndexPlugin;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.server.MachIndexServerBridge;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S：把客户端的扫描结果同步给服务端（专用服务器可用）。
 *
 * <h2>为什么需要这个包</h2>
 * NEI 是<b>客户端</b> mod，专用服务器没有 {@code RecipeCatalysts} / {@code IRecipeHandler}，
 * 所以「哪些机器能处理哪个配方类型」这份数据只有客户端算得出来。
 * 而 BQ 的任务库是<b>服务端权威</b>，客户端不能直接改。
 * 于是：客户端扫描 → 本包 → 服务端 {@code BqQuestWriter} 写库。
 *
 * <h2>为什么要分片</h2>
 * 1.7.10 的自定义 payload 单包有上限（实践值约 32 KB，超过会被截断/断开），
 * 而全量索引压缩后仍有几十 KB。因此按 {@link #CHUNK_SIZE} 切片，
 * 服务端按 {@code transferId} 重组，全部到齐后一次性反序列化并写库。
 *
 * <h2>安全</h2>
 * 只接受 **OP（权限等级 2）** 发来的数据 —— 否则任何客户端都能往服务器任务库里塞内容。
 */
public class MachIndexSyncPacket implements IMessage {

    /** 单片负载上限（留足余量，避免超过 1.7.10 的包长限制）。 */
    public static final int CHUNK_SIZE = 16 * 1024;

    /** 一次传输最多多少片（防御性上限：16KB × 256 ≈ 4MB，远超实际需要）。 */
    private static final int MAX_CHUNKS = 256;

    /** 传输标识（同一批分片共享）；不清空会随会话增长，超时/完成即移除。 */
    public String transferId = "";
    public int chunkIndex = 0;
    public int totalChunks = 1;
    public byte[] data = new byte[0];

    public MachIndexSyncPacket() {}

    public MachIndexSyncPacket(String transferId, int chunkIndex, int totalChunks, byte[] data) {
        this.transferId = transferId == null ? "" : transferId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data == null ? new byte[0] : data;
    }

    /**
     * 把一份扫描结果切成多个包（客户端侧调用）。
     *
     * @param result 扫描结果
     * @return 待发送的包列表；序列化失败返回空列表
     */
    public static java.util.List<MachIndexSyncPacket> split(IndexModel.ScanResult result) {
        java.util.List<MachIndexSyncPacket> out = new java.util.ArrayList<>();
        if (result == null) return out;
        byte[] raw;
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(result.toNbt(), bos);
            raw = bos.toByteArray();
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 索引序列化失败，无法同步到服务端", t);
            return out;
        }
        int total = (raw.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        if (total <= 0) total = 1;
        if (total > MAX_CHUNKS) {
            MyMod.LOG.warn("[AE2QoL] machindex 索引过大（" + raw.length + " B），放弃同步到服务端");
            return out;
        }
        String id = UUID.randomUUID()
            .toString();
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK_SIZE;
            int to = Math.min(raw.length, from + CHUNK_SIZE);
            out.add(new MachIndexSyncPacket(id, i, total, Arrays.copyOfRange(raw, from, to)));
        }
        MyMod.LOG.info("[AE2QoL] machindex 索引同步：压缩后 " + raw.length + " B，分 " + total + " 片");
        return out;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int len = buf.readUnsignedShort();
            byte[] idb = new byte[len];
            buf.readBytes(idb);
            transferId = new String(idb, java.nio.charset.StandardCharsets.UTF_8);
            chunkIndex = buf.readUnsignedShort();
            totalChunks = buf.readUnsignedShort();
            int dlen = buf.readInt();
            if (dlen < 0 || dlen > CHUNK_SIZE * 2) {
                data = new byte[0];
                return;
            }
            data = new byte[dlen];
            buf.readBytes(data);
        } catch (Throwable t) {
            // 防御性解码：坏包不得踢人
            data = new byte[0];
            transferId = "";
            totalChunks = 1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] idb = transferId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int ilen = Math.min(idb.length, 64);
        buf.writeShort(ilen);
        buf.writeBytes(idb, 0, ilen);
        buf.writeShort(chunkIndex);
        buf.writeShort(totalChunks);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    /** 服务端侧的分片重组缓冲。 */
    private static final Map<String, Pending> PENDING = new HashMap<>();

    private static final class Pending {

        byte[][] chunks;
        int received;
    }

    public static class Handler implements IMessageHandler<MachIndexSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(MachIndexSyncPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 安全：只接受 OP（等级 2）推送的索引，避免任意客户端污染任务库
            if (!player.canCommandSenderUseCommand(2, "ae2qol")) {
                MyMod.LOG.warn("[AE2QoL] machindex 拒绝非 OP 的索引同步：" + player.getCommandSenderName());
                return null;
            }
            if (!MachIndexPlugin.hasBq()) return null;
            if (msg.totalChunks <= 0 || msg.totalChunks > MAX_CHUNKS) return null;
            if (msg.chunkIndex < 0 || msg.chunkIndex >= msg.totalChunks) return null;

            String id = msg.transferId;
            synchronized (PENDING) {
                Pending p = PENDING.get(id);
                if (p == null || p.chunks.length != msg.totalChunks) {
                    p = new Pending();
                    p.chunks = new byte[msg.totalChunks][];
                    PENDING.put(id, p);
                }
                if (p.chunks[msg.chunkIndex] == null) {
                    p.chunks[msg.chunkIndex] = msg.data;
                    p.received++;
                }
                if (p.received < p.chunks.length) return null;
                PENDING.remove(id);

                // 全部到齐：拼接 → 反序列化 → 写库
                try {
                    int totalLen = 0;
                    for (byte[] c : p.chunks) totalLen += c.length;
                    byte[] raw = new byte[totalLen];
                    int off = 0;
                    for (byte[] c : p.chunks) {
                        System.arraycopy(c, 0, raw, off, c.length);
                        off += c.length;
                    }
                    NBTTagCompound nbt = CompressedStreamTools.readCompressed(new java.io.ByteArrayInputStream(raw));
                    IndexModel.ScanResult result = IndexModel.ScanResult.fromNbt(nbt);
                    MyMod.LOG.info(
                        "[AE2QoL] machindex 收到客户端索引：" + result.quests.size()
                            + " 任务 / "
                            + result.machines.size()
                            + " 机器 → 写库");
                    MachIndexServerBridge.applyOnServer(result, "client-sync");
                } catch (Throwable t) {
                    MyMod.LOG.error("[AE2QoL] machindex 客户端索引重组/写库失败", t);
                }
            }
            return null;
        }
    }

    /** 清空缓冲（服务端停止时调用，避免跨存档残留）。 */
    public static void clearPending() {
        synchronized (PENDING) {
            PENDING.clear();
        }
    }
}
