package com.gali.ae2_auto_pattern_upload.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class CommandOverlay extends CommandBase {

    @Override
    public String getCommandName() {
        return "apu-overlay";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/apu-overlay";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        boolean current = OverlayConfig.isEnabled();
        OverlayConfig.setEnabled(!current);
        boolean now = !current;
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GREEN + "[APU] NEI Overlay: " + (now ? "ON" : "OFF")));
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
