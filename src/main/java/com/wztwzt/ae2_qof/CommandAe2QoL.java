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
        return "/ae2qof reload | /ae2qof status | /ae2qof smbind <networkId>";
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
