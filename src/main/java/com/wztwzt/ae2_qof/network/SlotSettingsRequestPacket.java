package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SlotSettings;
import com.wztwzt.ae2_qof.wildcard.SlotSettingsAccess;
import com.wztwzt.ae2_qof.wildcard.SlotSettingsStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 「按格设置」回读请求（C2S，4.0.0）：弹窗打开时先问一次服务端"这一格现在是什么"，
 * 服务端回 {@link SlotSettingsSyncPacket}。
 *
 * <h2>为什么需要它</h2>
 * 按格设置存在**机器 NBT** 里，客户端机器对象并不保证有这份数据 ⇒ 不能靠读本地对象显示，
 * 必须由服务端下发（与 3.21.3 起的"坐标包 + S2C 权威回读"是同一套做法）。
 */
public class SlotSettingsRequestPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private int slot;

    public SlotSettingsRequestPacket() {}

    public SlotSettingsRequestPacket(int x, int y, int z, int slot) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.slot = buf.readInt();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 按格设置请求包解析失败（已忽略）", t);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.slot);
    }

    public static class Handler implements IMessageHandler<SlotSettingsRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(SlotSettingsRequestPacket message, MessageContext ctx) {
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    if (player == null) return;
                    if (!SlotSettingsAccess.near(player, message.x, message.y, message.z, "按格设置回读")) return;
                    IMetaTileEntity mte = SlotSettingsAccess
                        .machineAt(player, message.x, message.y, message.z, "按格设置回读");
                    if (mte == null) return;
                    SlotSettingsStore store = SlotSettingsAccess.storeOf(mte, "按格设置回读");
                    if (store == null) return;
                    SlotSettings settings = store.get(message.slot, false);
                    int circuit = settings == null ? -1 : settings.circuit;
                    boolean explicit = settings != null && settings.circuitExplicit;
                    ItemStack[] catalysts = new ItemStack[SlotSettings.CATALYST_SLOTS];
                    if (settings != null) {
                        for (int i = 0; i < SlotSettings.CATALYST_SLOTS; i++) {
                            catalysts[i] = settings.catalysts[i] == null ? null : settings.catalysts[i].copy();
                        }
                    }
                    ModNetwork.CHANNEL
                        .sendTo(new SlotSettingsSyncPacket(message.x, message.y, message.z, message.slot, circuit, explicit, catalysts), player);
                    MyMod.LOG.info(
                        "[AE2QoL] 按格设置回读：slot={} circuit={} 手改={} 机器={} player={}",
                        message.slot,
                        circuit,
                        explicit,
                        mte.getClass()
                            .getSimpleName(),
                        player.getCommandSenderName());
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 按格设置回读异常", t);
                }
            });
            return null;
        }
    }
}
