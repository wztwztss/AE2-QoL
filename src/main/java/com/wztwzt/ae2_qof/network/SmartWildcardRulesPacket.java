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

    /**
     * 目标槽位（3.34.0）：{@code >= 0} 时服务端**只写这个槽位**的物品；{@code -1} = 旧行为（在背包里找第一张）。
     *
     * <p>为什么必须带槽位：3.33.0 实测里，玩家在 Wild 窗口（打开于槽位 7）按加号，包却被写到"背包里第一张"
     * 我们的样板（日志指纹：同一张样板的 revision 由 3 回退到 1）⇒ 窗口那张始终是空的，用户看到"加号没效果"。
     */
    private int slot = -1;

    public SmartWildcardRulesPacket() {}

    public SmartWildcardRulesPacket(SmartWildcardState state) {
        this.tag = encode(state);
    }

    /** 3.34.0：指定目标槽位（Wild 窗口 / 电路页用），避免写错样板。 */
    public SmartWildcardRulesPacket(SmartWildcardState state, int slot) {
        this.tag = encode(state);
        this.slot = slot;
    }

    /**
     * 带模板的构造器：NEI 加号推导出的结果要**同时写规则与配方本体**（用户点一下加号就等于完成了编码）。
     * 这里刻意内联 NBT 列表构建、不引用任何客户端类（本包会在专用服务端加载）。
     */
    public SmartWildcardRulesPacket(SmartWildcardState state, java.util.List<ItemStack> templateIn,
        java.util.List<ItemStack> templateOut) {
        this.tag = encode(state);
        if (templateIn != null && !templateIn.isEmpty()) this.tag.setTag("TemplateIn", buildList(templateIn));
        if (templateOut != null && !templateOut.isEmpty()) this.tag.setTag("TemplateOut", buildList(templateOut));
    }

    /** 3.34.0：带模板 + 指定目标槽位（Wild 窗口里按 NEI 加号走这条）。 */
    public SmartWildcardRulesPacket(SmartWildcardState state, java.util.List<ItemStack> templateIn,
        java.util.List<ItemStack> templateOut, int slot) {
        this(state, templateIn, templateOut);
        this.slot = slot;
    }

    /** 3.34.0：改为 public —— Wild 窗口的"加号就地刷新"也要用它构造原生 in/out，单一来源避免两处形状不一致。 */
    public static NBTTagList buildList(java.util.List<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            stack.writeToNBT(entry);
            entry.setInteger("Count", Math.max(1, stack.stackSize));
            entry.setLong("Cnt", Math.max(1, stack.stackSize));
            list.appendTag(entry);
        }
        return list;
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
            this.slot = buf.readInt();
        } catch (Throwable t) {
            this.tag = null;
            this.slot = -1;
            MyMod.LOG.warn("[AE2QoL] 通配样板规则包解析失败（已忽略该包）", t);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.tag == null ? new NBTTagCompound() : this.tag);
        buf.writeInt(this.slot);
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
                    if (player.openContainer instanceof ContainerSmartWildcard container) {
                        SmartWildcardState state = decode(message.tag);
                        NBTTagList templateIn = message.tag == null ? null
                            : message.tag.getTagList("TemplateIn", Constants.NBT.TAG_COMPOUND);
                        NBTTagList templateOut = message.tag == null ? null
                            : message.tag.getTagList("TemplateOut", Constants.NBT.TAG_COMPOUND);
                        if (!container.applyRules(state, templateIn, templateOut)) {
                            MyMod.LOG
                                .warn("[AE2QoL] 通配样板规则写回被容器拒绝：player={}", player.getCommandSenderName());
                        }
                        return;
                    }
                    // 3.23.0：MUI2 编辑器（PlayerInventoryGuiFactory）打开时容器是 MUI2 的 ModularContainer，
                    // 目标样板就是玩家手持的那张 ⇒ 直接写主手物品（同样是服务端权威写入 + 记日志）。
                    // 3.32.0：**不要只看主手** —— Wild 窗口是 ModularUIContainer，打开时主手未必指着那张样板
                    //（用户实测日志：通配样板写回失败：…主手也不是通配样板（container=ModularUIContainer））。
                    // 改为**在背包里找**我们的通配样板；判据用物品实例（全新样板没有我们的 NBT，不能用 isSmartWildcard）。
                    // 3.34.0：**优先按发送方给的槽位**（Wild 窗口 / 电路页都会带），找不到才退回"背包里第一张"。
                    // 旧行为正是"加号写到另一张样板"的根因（revision 3→1 的指纹）。
                    ItemStack held = null;
                    boolean bySlot = false;
                    if (message.slot >= 0 && message.slot < player.inventory.mainInventory.length) {
                        ItemStack candidate = player.inventory.mainInventory[message.slot];
                        if (com.wztwzt.ae2_qof.wildcard.SmartWildcardGate.isOurs(candidate)) {
                            held = candidate;
                            bySlot = true;
                        } else {
                            MyMod.LOG.warn(
                                "[AE2QoL] 通配样板写回：指定槽位 {} 里不是我们的通配样板（item={}）⇒ 退回背包搜索",
                                message.slot,
                                candidate == null ? "null" : candidate.getItem());
                        }
                    }
                    if (held == null) {
                        for (ItemStack candidate : player.inventory.mainInventory) {
                            if (com.wztwzt.ae2_qof.wildcard.SmartWildcardGate.isOurs(candidate)) {
                                held = candidate;
                                break;
                            }
                        }
                    }
                    if (held == null) {
                        MyMod.LOG.warn(
                            "[AE2QoL] 通配样板写回失败：既不是通配样板容器，背包里也没有我们的通配样板（container={} slot={}）",
                            player.openContainer == null ? "null"
                                : player.openContainer.getClass()
                                    .getSimpleName(),
                            message.slot);
                        return;
                    }
                    SmartWildcardState state = decode(message.tag);
                    NBTTagList templateIn = message.tag == null ? null
                        : message.tag.getTagList("TemplateIn", Constants.NBT.TAG_COMPOUND);
                    NBTTagList templateOut = message.tag == null ? null
                        : message.tag.getTagList("TemplateOut", Constants.NBT.TAG_COMPOUND);
                    state.writeAndBumpRevision(held);
                    try {
                        if (held.getTagCompound() != null) {
                            if (templateIn != null && templateIn.tagCount() > 0) {
                                held.getTagCompound()
                                    .setTag("in", templateIn);
                                held.getTagCompound()
                                    .setBoolean("crafting", false);
                            }
                            if (templateOut != null && templateOut.tagCount() > 0) {
                                held.getTagCompound()
                                    .setTag("out", templateOut);
                            }
                        }
                    } catch (Throwable t) {
                        MyMod.LOG.warn("[AE2QoL] 通配样板模板 in/out 写入失败（规则已写）", t);
                    }
                    com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander.clearCache();
                    player.inventory.markDirty();
                    // 3.28.0：写回后把同一状态也推到 Wild 的键 —— Wild 窗口是**构建期**读 NBT 的，无法就地刷新，
                    // 所以明确告诉玩家「关掉重开一次即可看到」，绝不静默（本项目铁则）。
                    try {
                        com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge.pushToWild(held, state);
                        player.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                "\u00a7a[AE2QoL] \u5df2\u5199\u5165\u6837\u677f\uff1b\u82e5 Wild \u754c\u9762\u5f00\u7740\uff0c\u8bf7\u5173\u6389\u91cd\u5f00\u4e00\u6b21\u5373\u53ef\u770b\u5230"));
                    } catch (Throwable t) {
                        MyMod.LOG.warn("[AE2QoL] 写回后推送到 Wild 键失败（Wild 界面里的内容可能未同步）", t);
                    }
                    MyMod.LOG.info(
                        "[AE2QoL] 通配样板写回成功（{}）：player={} slot={} rules={} blacklist={} circuit={} revision={}",
                        bySlot ? "指定槽位" : "背包搜索",
                        player.getCommandSenderName(),
                        message.slot,
                        state.rules.size(),
                        state.blacklist.size(),
                        state.circuit,
                        state.revision);
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 通配样板规则包处理异常", t);
                }
            });
            return null;
        }
    }
}
