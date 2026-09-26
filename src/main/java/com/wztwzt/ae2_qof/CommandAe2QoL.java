package com.wztwzt.ae2_qof;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.cover.stockmonitor.ItemStockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCoverData;
import com.wztwzt.ae2_qof.util.RecipeNameUtil;

/**
 * {@code /ae2qof} 管理命令（OP 权限，等级 2）：
 * - {@code /ae2qof reload}：立即热重载 {@code config/ae2_qof/settings.json} 与 {@code recipe_names.json}
 * - {@code /ae2qof status}：显示当前生效的配置值
 *
 * 服务端需要 OP 权限；单机/局域网主机默认即 OP，可直接使用。
 */
public class CommandAe2QoL extends CommandBase {

    @Override
    public String getCommandName() {
        return "ae2qof";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ae2qof reload | /ae2qof status | /ae2qof smbind <networkId> | /ae2qof wildcard [矿辞前缀]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
                return;
            }
            Config.reload();
            RecipeNameUtil.reloadMappings();
            RecipeMapNameConfig.reload();
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GREEN + "[AE2QoL] Reloaded settings.json + recipe_names.json"));
            return;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            Config.ensureFresh();
            sender.addChatMessage(
                new ChatComponentText(
                    "[AE2QoL] io_port_rate=" + Config.exIOPortTransferContentsRate
                        + ", smart_doubling_max_rounds="
                        + Config.smartDoublingMaxRounds
                        + ", smart_doubling_push_cap="
                        + Config.smartDoublingPushCap
                        + ", nei_overlay_enabled="
                        + Config.neiOverlayEnabled
                        + ", recipe_mappings="
                        + RecipeNameUtil.getMappingsView()
                            .size()));
            return;
        }
        if ("wildcard".equalsIgnoreCase(args[0])) {
            // 3.22.0 M1 自测入口：把手里的（已用 NEI 编码好模板配方的）智能通配样板写成一条矿辞规则，
            // 立刻展开并回报计数。M2 的“NEI 加号自动推导”会复用同一条写入路径（writeAndBumpRevision + clearCache）。
            if (!(sender instanceof EntityPlayer)) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Player only command"));
                return;
            }
            EntityPlayer player = (EntityPlayer) sender;
            ItemStack held = player.getCurrentEquippedItem();
            if (held == null || !(held.getItem() instanceof com.wztwzt.ae2_qof.wildcard.ItemSmartWildcardPattern)) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "[AE2QoL] 请手持一张（已编码模板配方的）智能通配样板"));
                return;
            }
            String prefix = args.length > 1 ? args[1] : "ingot";
            com.wztwzt.ae2_qof.wildcard.SmartWildcardState state = com.wztwzt.ae2_qof.wildcard.SmartWildcardState
                .of(held);
            if (state == null) state = new com.wztwzt.ae2_qof.wildcard.SmartWildcardState();

            // 数量取模板第一个输入槽的数量（模板就是物品自己的原生 in 标签）
            long amount = 1L;
            try {
                net.minecraft.nbt.NBTTagCompound tag = held.getTagCompound();
                if (tag != null) {
                    net.minecraft.nbt.NBTTagList in = tag
                        .getTagList("in", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
                    if (in.tagCount() > 0) {
                        net.minecraft.nbt.NBTTagCompound slot = in.getCompoundTagAt(0);
                        amount = slot.hasKey("Cnt") ? Math.max(1L, slot.getLong("Cnt"))
                            : Math.max(1L, slot.getInteger("Count"));
                    }
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] /ae2qof wildcard 读取模板数量失败，按 1 处理", t);
            }

            state.rules.clear();
            state.rules.add(new com.wztwzt.ae2_qof.wildcard.SmartWildcardState.Rule(0, true, prefix + "*", amount));
            state.writeAndBumpRevision(held);
            com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander.clearCache();
            com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander.Result result = com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander
                .expand(held, player.worldObj);

            sender.addChatMessage(
                new ChatComponentText(
                    (result.isEmpty() ? EnumChatFormatting.RED : EnumChatFormatting.GREEN)
                        + "[AE2QoL] 规则 #0 ore:"
                        + prefix
                        + "* x"
                        + amount
                        + " → "
                        + result.describe()));
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GRAY + "若客户端显示未更新，把样板丢出再捡起即可"));
            MyMod.LOG.info(
                "[AE2QoL] /ae2qof wildcard: player={} prefix={} amount={} → {}",
                player.getCommandSenderName(),
                prefix,
                amount,
                result.describe());
            return;
        }
        if ("smbind".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof EntityPlayer)) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Player only command"));
                return;
            }
            if (args.length < 2) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /ae2qof smbind <networkId>"));
                return;
            }
            EntityPlayer player = (EntityPlayer) sender;
            ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemStockMonitorCover)) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "Hold a Stock Monitor Cover in your hand"));
                return;
            }
            try {
                UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Invalid UUID: " + args[1]));
                return;
            }
            if (held.stackTagCompound == null) held.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
            held.stackTagCompound.setString(StockMonitorCoverData.NBT_NETWORK_ID, args[1]);
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GREEN + "Bound to network: " + args[1]));
            return;
        }
        sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> list = new ArrayList<String>();
        if (args.length == 1) {
            list.add("reload");
            list.add("status");
            list.add("smbind");
        }
        return list;
    }
}
