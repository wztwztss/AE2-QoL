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
        return "/ae2qof reload | /ae2qof status | /ae2qof smbind <networkId> | /ae2qof wildcard [矿辞前缀] "
            + "| /ae2qof cell list [页] | /ae2qof cell info|archive|restore <uuid>";
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
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[AE2QoL] Reloaded settings.json + recipe_names.json"));
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
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[AE2QoL] 请手持一张（已编码模板配方的）智能通配样板"));
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
                    (result.isEmpty() ? EnumChatFormatting.RED : EnumChatFormatting.GREEN) + "[AE2QoL] 规则 #0 ore:"
                        + prefix
                        + "* x"
                        + amount
                        + " → "
                        + result.describe()));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "若客户端显示未更新，把样板丢出再捡起即可"));
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
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "Usage: /ae2qof smbind <networkId>"));
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
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Bound to network: " + args[1]));
            return;
        }
        if ("cell".equalsIgnoreCase(args[0])) {
            // 3.35.0-fix21（审查 P2-10）：无限元件数据文件的**管理命令组**——
            // 拆掉的元件数据"只涨不减"，自动孤儿判定不可行（需世界级物品扫描，元件可能在
            // 未加载区块里，误删不可逆），因此回收 = 管理员用 list/info 取证后 archive（移动
            // 到 archive/ 目录，不删除），必要时 restore 找回。全部操作要求 OP 等级 2。
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
                return;
            }
            handleCell(sender, args);
            return;
        }
        sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }

    /**
     * 3.35.0-fix21（审查 P2-10）：{@code /ae2qof cell list [页] | info <uuid> | archive <uuid> | restore <uuid>}。
     * 语义见 {@link cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage} 的管理面方法
     * （listCellFiles / inspectCell / archiveCell / restoreCell）。
     */
    private void handleCell(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.YELLOW
                        + "Usage: /ae2qof cell list [页] | info <uuid> | archive <uuid> | restore <uuid>"));
            return;
        }
        String sub = args[1].toLowerCase();
        if ("list".equals(sub)) {
            java.util.List<cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage.CellFileInfo> all = cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage
                .getInstance()
                .listCellFiles();
            if (all.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("[AE2QoL] 本存档没有任何无限元件数据文件"));
                return;
            }
            int perPage = 10;
            int totalPages = Math.max(1, (all.size() + perPage - 1) / perPage);
            int page;
            try {
                page = args.length > 2 ? Math.max(1, Integer.parseInt(args[2])) : 1;
            } catch (NumberFormatException bad) {
                page = 1;
            }
            page = Math.min(page, totalPages);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "[AE2QoL] 无限元件数据文件：共 "
                        + all.size()
                        + " 个，第 "
                        + page
                        + "/"
                        + totalPages
                        + " 页（按最后修改排序）——已拆掉元件的孤儿文件可人工比对后归档"));
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            int start = (page - 1) * perPage;
            for (int i = start; i < Math.min(start + perPage, all.size()); i++) {
                cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage.CellFileInfo f = all.get(i);
                sender.addChatMessage(
                    new ChatComponentText(
                        String.format(
                            "  %6d B  %s  %s",
                            f.sizeBytes,
                            fmt.format(new java.util.Date(f.lastModifiedMs)),
                            f.id)));
            }
            return;
        }
        if (args.length < 3) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Usage: /ae2qof cell " + sub + " <uuid>"));
            return;
        }
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Invalid UUID: " + args[2]));
            return;
        }
        cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage storage = cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage
            .getInstance();
        if ("info".equals(sub)) {
            cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord record = storage.inspectCell(id);
            if (record == null) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "数据文件不存在或已损坏：" + id));
                return;
            }
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[AE2QoL] 元件 " + id + "："));
            sender.addChatMessage(
                new ChatComponentText(
                    "  物品   " + record.getUsedItemTypes() + " 类 / " + record.getStoredItemUnits() + " 件"));
            sender.addChatMessage(
                new ChatComponentText(
                    "  流体   " + record.getUsedFluidTypes() + " 类 / " + record.getStoredFluidUnits() + " 毫升"));
            sender.addChatMessage(
                new ChatComponentText(
                    "  源质   " + record.getUsedEssentiaTypes() + " 类 / " + record.getStoredEssentiaUnits() + " 单位"));
            sender.addChatMessage(
                new ChatComponentText(
                    "  电力   " + record.getUsedEUTypes() + " 类 / " + record.getStoredEUUnits() + " EU"));
            return;
        }
        if ("archive".equals(sub)) {
            String result = storage.archiveCell(id);
            sender.addChatMessage(
                new ChatComponentText(
                    (result.startsWith("已归档") ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + "[AE2QoL] "
                        + result));
            return;
        }
        if ("restore".equals(sub)) {
            String result = storage.restoreCell(id);
            sender.addChatMessage(
                new ChatComponentText(
                    (result.startsWith("已恢复") ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + "[AE2QoL] "
                        + result));
            return;
        }
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW
                    + "Usage: /ae2qof cell list [页] | info <uuid> | archive <uuid> | restore <uuid>"));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> list = new ArrayList<String>();
        if (args.length == 1) {
            list.add("reload");
            list.add("status");
            list.add("smbind");
            list.add("wildcard");
            list.add("cell");
        }
        if (args.length == 2 && "cell".equalsIgnoreCase(args[0])) {
            list.add("list");
            list.add("info");
            list.add("archive");
            list.add("restore");
        }
        return list;
    }
}
