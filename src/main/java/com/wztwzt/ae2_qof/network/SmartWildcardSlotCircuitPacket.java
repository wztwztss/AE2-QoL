package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.mixin.gt.MixinGtHostAccess;
import com.wztwzt.ae2_qof.mixin.gt.MixinGtnlPatternSlotAccess;
import com.wztwzt.ae2_qof.mixin.gt.MixinPatternSlotAccess;
import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchAccess;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 「机器样板槽上的手势」写回包（C2S，3.22.0 M3）。
 *
 * <h2>用途</h2>
 * 在机器 GUI 的样板槽上 **Shift+中键** → 客户端弹出电路选择屏（1~24 / 清除 / 手持物品记为不消耗）→
 * 由本包把结果写到**那张样板自己的 NBT**（`SmartWildcardState.circuit` / `nonConsumed`），
 * 而不是去改机器的电路槽：这样一份设置在样板跟着走，机器读取时由
 * {@code SmartWildcardCircuit} 按「样板自带 &gt; 槽位 &gt; 整机」写进虚拟电路槽。
 *
 * <h2>为什么服务端写</h2>
 * 客户端改 NBT 会被服务端槽位同步覆盖（幽灵物品）；且机器里的样板是服务端权威对象。
 *
 * <h2>覆盖范围与诚实边界</h2>
 * 支持 GT 样板输入仓（2714/2715）、PH 家族（22069 / MK.II / 我们的 MK.III 32108）、
 * GTNL 超级样板总成（21504/21505）。**AE2 ME 接口**的样板槽是 AE2 自己的 {@code Slot}
 * （不是 MUI2 的 {@code ItemSlot}），手势不触发；那条路请用右键样板的「电路」页。
 * 每个失败分支都记日志（本项目原则：不允许静默回退）。
 */
public class SmartWildcardSlotCircuitPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private int slot;
    private int circuit;
    private boolean addHeldAsNonConsumed;

    public SmartWildcardSlotCircuitPacket() {}

    public SmartWildcardSlotCircuitPacket(int x, int y, int z, int slot, int circuit, boolean addHeld) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
        this.circuit = circuit;
        this.addHeldAsNonConsumed = addHeld;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.slot = buf.readInt();
            this.circuit = buf.readInt();
            this.addHeldAsNonConsumed = buf.readBoolean();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板槽电路包解析失败（已忽略）", t);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.slot);
        buf.writeInt(this.circuit);
        buf.writeBoolean(this.addHeldAsNonConsumed);
    }

    /** 按机器家族取出该槽位里的样板物品（均为只读访问，见各自 accessor 的注释）。 */
    private static ItemStack findPattern(IMetaTileEntity mte, int slot) {
        try {
            if (mte instanceof reobf.proghatches.gt.metatileentity.PatternDualInputHatch ph) {
                ItemStack[] array = ((MixinPatternDualInputHatchAccess) ph).getAe2qolPattern();
                if (array == null || slot < 0 || slot >= array.length) return null;
                return array[slot];
            }
            if (mte instanceof com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME gtnl) {
                if (gtnl.internalInventory == null || slot < 0 || slot >= gtnl.internalInventory.length) return null;
                Object patternSlot = gtnl.internalInventory[slot];
                return patternSlot == null ? null
                    : ((MixinGtnlPatternSlotAccess) patternSlot).getAe2qolSlotPattern();
            }
            if (mte instanceof gregtech.common.tileentities.machines.MTEHatchCraftingInputME gt) {
                var array = ((MixinGtHostAccess) gt).getAe2qolInternalInventory();
                if (array == null || slot < 0 || slot >= array.length) return null;
                Object patternSlot = array[slot];
                return patternSlot == null ? null
                    : ((MixinPatternSlotAccess) patternSlot).getAe2qolSlotPattern();
            }
            MyMod.LOG.warn("[AE2QoL] 样板槽手势写回：暂不支持该机器类型 {}（可用右键样板的「电路」页）", mte.getClass());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板槽手势写回：读取样板失败", t);
        }
        return null;
    }

    public static class Handler implements IMessageHandler<SmartWildcardSlotCircuitPacket, IMessage> {

        @Override
        public IMessage onMessage(SmartWildcardSlotCircuitPacket message, MessageContext ctx) {
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    if (player == null) return;
                    TileEntity te = player.worldObj.getTileEntity(message.x, message.y, message.z);
                    if (!(te instanceof IGregTechTileEntity gregTechTileEntity)) {
                        MyMod.LOG.warn("[AE2QoL] 样板槽手势写回：坐标处不是 GT 机器 ({} {} {})", message.x, message.y, message.z);
                        return;
                    }
                    IMetaTileEntity mte = gregTechTileEntity.getMetaTileEntity();
                    if (mte == null) {
                        MyMod.LOG.warn("[AE2QoL] 样板槽手势写回：机器元数据为空");
                        return;
                    }
                    ItemStack pattern = findPattern(mte, message.slot);
                    if (pattern == null || !SmartWildcardState.isSmartWildcard(pattern)) {
                        MyMod.LOG.warn(
                            "[AE2QoL] 样板槽手势写回：槽位 {} 里不是智能通配样板（family={}）",
                            message.slot,
                            mte.getClass()
                                .getSimpleName());
                        return;
                    }
                    SmartWildcardState state = SmartWildcardState.of(pattern);
                    if (state == null) state = new SmartWildcardState();
                    if (message.circuit == -2) {
                        // -2 = 记为不消耗：把玩家手持物品记进样板列表
                        ItemStack held = player.getCurrentEquippedItem();
                        if (held == null) {
                            MyMod.LOG.warn("[AE2QoL] 样板槽手势写回：手持物品为空，无法记为不消耗物品");
                            return;
                        }
                        ItemStack mark = held.copy();
                        mark.stackSize = 1;
                        state.nonConsumed.add(mark);
                    } else {
                        state.circuit = message.circuit; // -1 = 清除（继承）
                    }
                    state.writeAndBumpRevision(pattern);
                    gregTechTileEntity.markDirty();
                    SmartWildcardExpander.clearCache();
                    MyMod.LOG.info(
                        "[AE2QoL] 样板槽手势写回成功：slot={} circuit={} nonConsumed={} @ {} 家族 {} player={}",
                        message.slot,
                        state.circuit,
                        state.nonConsumed.size(),
                        mte.getClass()
                            .getSimpleName(),
                        message.circuit == -2 ? "（记为不消耗物品）" : "",
                        player.getCommandSenderName());
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 样板槽手势写回异常", t);
                }
            });
            return null;
        }
    }
}
