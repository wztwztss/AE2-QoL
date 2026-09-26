package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ServerTerminalHelper;
import com.wztwzt.ae2_qof.wildcard.ContainerSmartWildcard;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 通配样板规则写回包（C2S，3.22.0 M2-A）。
 *
 * <h2>为什么要发包、为什么必须服务端写</h2>
 * 规则是在**客户端**推导出来的（NEI 的配方数据只在客户端），但样板 NBT 必须由**服务端**写：
 * <ul>
 * <li>客户端直接改 NBT 会被服务端槽位同步覆盖（幽灵物品）；</li>
 * <li>展开读的是服务端权威 NBT（{@code ItemEncodedPattern} 的静态输出缓存也按服务端物品算）。</li>
 * </ul>
 * 因此这里只传**纯数据**（一份 NBT），服务端在归队 tick 线程后写进
 * {@link ContainerSmartWildcard} 指的那个真实槽位，再 {@code detectAndSendChanges()} 同步回客户端。
 *
 * <p>所有失败分支都写日志（本项目原则：不允许静默回退）；反序列化也做保护（坏包不踢人、只记 WARN）。
 */
public class SmartWildcardRulesPacket implements IMessage {

    private NBTTagCompound tag;

    public SmartWildcardRulesPacket() {}

    public SmartWildcardRulesPacket(SmartWildcardState state) {
        this.tag = encode(state);
    }

    // ================= 序列化 =================

    /** 把状态拍成一份独立 NBT（与物品 NBT 里的子树同构，便于两端对账）。 */
    public static NBTTagCompound encode(SmartWildcardState state) {
        NBTTagCompound root = new NBTTagCompound();
        if (state == null) return root;
        NBTTagList rules = new NBTTagList();
        for (SmartWildcardState.Rule rule : state.rules) {
            if (rule == null || rule.matcher == null || rule.matcher.isEmpty()) continue;
            NBTTagCompound r = new NBTTagCompound();
            r.setInteger("Slot", rule.slot);
            r.setBoolean("OreDict", rule.oreDictMode);
            r.setString("Matcher", rule.matcher);
            r.setLong("Amount", rule.amount);
            rules.appendTag(r);
        }
        root.setTag("Rules", rules);
        root.setTag("Blacklist", stringList(state.blacklist));
        root.setTag("Whitelist", stringList(state.whitelist));
        root.setInteger("Circuit", state.circuit);
        root.setInteger("Revision", state.revision);
        NBTTagList nc = new NBTTagList();
        for (ItemStack item : state.nonConsumed) {
            if (item == null) continue;
            NBTTagCompound itemTag = new NBTTagCompound();
            item.writeToNBT(itemTag);
            nc.appendTag(itemTag);
        }
        root.setTag("NonConsumed", nc);
        return root;
    }

    /** 反向还原（防御性：任何缺失字段都按“不设置”处理，不抛异常）。 */
    public static SmartWildcardState decode(NBTTagCompound root) {
        SmartWildcardState state = new SmartWildcardState();
        if (root == null) return state;
        NBTTagList rules = root.getTagList("Rules", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rules.tagCount(); i++) {
            NBTTagCompound r = rules.getCompoundTagAt(i);
            state.rules.add(
                new SmartWildcardState.Rule(
                    r.getInteger("Slot"),
                    !r.hasKey("OreDict") || r.getBoolean("OreDict"),
                    r.getString("Matcher"),
                    r.hasKey("Amount") ? r.getLong("Amount") : 1L));
        }
        readStrings(root, "Blacklist", state.blacklist);
        readStrings(root, "Whitelist", state.whitelist);
        state.circuit = root.hasKey("Circuit") ? root.getInteger("Circuit") : -1;
        state.revision = root.getInteger("Revision");
        NBTTagList nc = root.getTagList("NonConsumed", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < nc.tagCount(); i++) {
            ItemStack item = ItemStack.loadItemStackFromNBT(nc.getCompoundTagAt(i));
            if (item != null) state.nonConsumed.add(item);
        }
        return state;
    }

    private static NBTTagList stringList(java.util.List<String> values) {
        NBTTagList list = new NBTTagList();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.trim()
                    .isEmpty()) list.appendTag(new NBTTagString(v.trim()));
            }
        }
        return list;
    }

    private static void readStrings(NBTTagCompound root, String key, java.util.List<String> out) {
        NBTTagList list = root.getTagList(key, Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            String v = list.getStringTagAt(i);
            if (v != null && !v.trim()
                .isEmpty()) out.add(v.trim());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.tag = ByteBufUtils.readTag(buf);
        } catch (Throwable t) {
            this.tag = null;
            MyMod.LOG.warn("[AE2QoL] 通配样板规则包解析失败（已忽略该包）", t);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.tag == null ? new NBTTagCompound() : this.tag);
    }

    // ================= 服务端处理 =================

    public static class Handler implements IMessageHandler<SmartWildcardRulesPacket, IMessage> {

        @Override
        public IMessage onMessage(final SmartWildcardRulesPacket message, final MessageContext ctx) {
            // 网络线程里绝不能碰 container/inventory ⇒ 归队服务端 tick 线程（与本仓既有包同一手法）
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    if (player == null) return;
                    if (!(player.openContainer instanceof ContainerSmartWildcard container)) {
                        MyMod.LOG.warn(
                            "[AE2QoL] 通配样板规则包被丢弃：当前打开的不是通配样板界面（container={}）",
                            player.openContainer == null ? "null"
                                : player.openContainer.getClass()
                                    .getName());
                        return;
                    }
                    SmartWildcardState state = decode(message.tag);
                    if (!container.applyRules(state)) {
                        MyMod.LOG.warn("[AE2QoL] 通配样板规则写回被容器拒绝：player={}", player.getCommandSenderName());
                    }
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 通配样板规则包处理异常", t);
                }
            });
            return null;
        }
    }
}
