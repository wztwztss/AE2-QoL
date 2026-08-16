package com.wztwzt.ae2_qof;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
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
        return "/ae2qof reload | /ae2qof status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(this.getRequiredPermissionLevel(), this.getCommandName());
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            Config.reload();
            RecipeNameUtil.reloadMappings();
            RecipeMapNameConfig.reload();
            sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GREEN + "[AE2QoL] 已热重载 settings.json + recipe_names.json"));
            return;
        }
        Config.ensureFresh();
        sender.addChatMessage(new ChatComponentText(
            "[AE2QoL] io_port_rate=" + Config.exIOPortTransferContentsRate
                + ", smart_doubling_max_rounds=" + Config.smartDoublingMaxRounds
                + ", nei_overlay_enabled=" + Config.neiOverlayEnabled
                + ", recipe_mappings=" + RecipeNameUtil.getMappingsView()
                    .size()));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> list = new ArrayList<String>();
        if (args.length == 1) {
            list.add("reload");
            list.add("status");
        }
        return list;
    }
}