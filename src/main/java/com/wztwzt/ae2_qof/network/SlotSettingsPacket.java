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
 * 「按格设置」写入包（C2S，4.0.0）：把**整格状态**（电路 + 9 个催化剂位）一次性写到机器。
 *
 * <h2>为什么传整格而不是增量</h2>
 * 弹窗里的编辑是玩家连续动作（点电路、放催化剂），传"整格快照"语义最清晰：
 * 服务端直接用客户端所见覆盖，不需要维护增量合并规则，也就不会出现"两处各改一半"的错位。
 *
 * <h2>为什么服务端写</h2>
 * 与既有样板槽手势包同一理由：机器是服务端权威对象；并且这里还带
 * **距离校验**（{@link SlotSettingsAccess#near}）与"机器是否支持按格设置"的判断（{@link SlotSettingsAccess#storeOf}），
 * 失败一律留日志（本项目原则：不允许静默失败）。
 */
public class SlotSettingsPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private int slot;
    private int circuit;
    private boolean circuitExplicit;
    private ItemStack[] catalysts = new ItemStack[SlotSettings.CATALYST_SLOTS];

    public SlotSettingsPacket() {}

    public SlotSettingsPacket(int x, int y, int z, int slot, int circuit, boolean circuitExplicit,
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
            MyMod.LOG.warn("[AE2QoL] 按格设置包解析失败（已忽略）", t);
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

    public static class Handler implements IMessageHandler<SlotSettingsPacket, IMessage> {

        @Override
        public IMessage onMessage(SlotSettingsPacket message, MessageContext ctx) {
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    if (player == null) return;
                    if (!SlotSettingsAccess.near(player, message.x, message.y, message.z, "按格设置写入")) return;
                    IMetaTileEntity mte = SlotSettingsAccess
                        .machineAt(player, message.x, message.y, message.z, "按格设置写入");
                    if (mte == null) return;
                    SlotSettingsStore store = SlotSettingsAccess.storeOf(mte, "按格设置写入");
                    if (store == null) return;
                    SlotSettings settings = store.get(message.slot, true);
                    if (settings == null) {
                        MyMod.LOG.warn("[AE2QoL] 按格设置写入：槽位下标非法 {}", message.slot);
                        return;
                    }
                    settings.circuit = message.circuit;
                    settings.circuitExplicit = message.circuitExplicit;
                    for (int i = 0; i < SlotSettings.CATALYST_SLOTS; i++) {
                        ItemStack stack = message.catalysts != null && i < message.catalysts.length
                            ? message.catalysts[i]
                            : null;
                        settings.catalysts[i] = stack == null ? null : stack.copy();
                    }
                    if (settings.isEmpty()) store.clear(message.slot);
                    gregtech.api.interfaces.tileentity.IGregTechTileEntity base = mte.getBaseMetaTileEntity();
                    if (base != null) base.markDirty();
                    MyMod.LOG.info(
                        "[AE2QoL] 按格设置写入成功：slot={} circuit={} 手改={} 催化位非空={} 机器={} player={}",
                        message.slot,
                        settings.circuit,
                        settings.circuitExplicit,
                        countNonEmpty(settings),
                        mte.getClass()
                            .getSimpleName(),
                        player.getCommandSenderName());
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 按格设置写入异常", t);
                }
            });
            return null;
        }

        private static int countNonEmpty(SlotSettings settings) {
            int count = 0;
            for (ItemStack stack : settings.catalysts) {
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) count++;
            }
            return count;
        }
    }
}
