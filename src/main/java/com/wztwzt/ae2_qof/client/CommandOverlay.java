package com.wztwzt.ae2_qof.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.Config;

public class CommandOverlay extends CommandBase {

    @Override
    public String getCommandName() {
        return "apu-overlay";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/apu-overlay [status]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args != null && args.length > 0 && "status".equalsIgnoreCase(args[0])) {
            Config.reload();
            StringBuilder sb = new StringBuilder();
            sb.append(EnumChatFormatting.GREEN)
                .append("[APU] NEI Overlay: ")
                .append(Config.neiOverlayEnabled ? "ON" : "OFF")
                .append(EnumChatFormatting.GRAY)
                .append(" settings=")
                .append(Config.getSettingsPath());
            if (!Config.getLastReloadError()
                .isEmpty()) {
                sb.append(EnumChatFormatting.RED)
                    .append(" load_error=")
                    .append(Config.getLastReloadError());
            }
            if (!Config.getLastWriteError()
                .isEmpty()) {
                sb.append(EnumChatFormatting.RED)
                    .append(" write_error=")
                    .append(Config.getLastWriteError());
            }
            sender.addChatMessage(new ChatComponentText(sb.toString()));
            return;
        }
        boolean current = OverlayConfig.isEnabled();
        boolean ok = OverlayConfig.setEnabled(!current);
        boolean now = ok ? !current : current;
        String message = ok ? EnumChatFormatting.GREEN + "[APU] NEI Overlay: " + (now ? "ON" : "OFF")
            : EnumChatFormatting.RED + "[APU] NEI Overlay 写入失败: "
                + (Config.getLastWriteError()
                    .isEmpty() ? "settings.json 不可写" : Config.getLastWriteError());
        sender.addChatMessage(new ChatComponentText(message));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return new ArrayList<String>();
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
