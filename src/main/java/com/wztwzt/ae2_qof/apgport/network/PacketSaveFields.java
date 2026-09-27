/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.apgport.item.ItemPatternGenerator;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 -> 服务端: 保存 GUI 输入字段到手持物品的 NBT
 */
public class PacketSaveFields implements IMessage {

    private String recipeMap;
    private String outputOre;
    private String inputOre;
    private String ncItem;
    private String blacklistInput;
    private String blacklistOutput;
    private String replacements;
    private int targetTier;
    /** 3.22.0-fix47：保存后是否**重新打开生成器界面**（对照表选行后靠它回到主页）。 */
    private boolean reopenGui;

    public PacketSaveFields() {}

    public PacketSaveFields(String recipeMap, String outputOre, String inputOre, String ncItem, String blacklistInput,
        String blacklistOutput, String replacements, int targetTier) {
        this(recipeMap, outputOre, inputOre, ncItem, blacklistInput, blacklistOutput, replacements, targetTier, false);
    }

    public PacketSaveFields(String recipeMap, String outputOre, String inputOre, String ncItem, String blacklistInput,
        String blacklistOutput, String replacements, int targetTier, boolean reopenGui) {
        this.recipeMap = recipeMap;
        this.outputOre = outputOre;
        this.inputOre = inputOre;
        this.ncItem = ncItem;
        this.blacklistInput = blacklistInput;
        this.blacklistOutput = blacklistOutput;
        this.replacements = replacements;
        this.targetTier = targetTier;
        this.reopenGui = reopenGui;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        recipeMap = ByteBufUtils.readUTF8String(buf);
        outputOre = ByteBufUtils.readUTF8String(buf);
        inputOre = ByteBufUtils.readUTF8String(buf);
        ncItem = ByteBufUtils.readUTF8String(buf);
        blacklistInput = ByteBufUtils.readUTF8String(buf);
        blacklistOutput = ByteBufUtils.readUTF8String(buf);
        replacements = ByteBufUtils.readUTF8String(buf);
        targetTier = buf.readInt();
        reopenGui = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, recipeMap != null ? recipeMap : "");
        ByteBufUtils.writeUTF8String(buf, outputOre != null ? outputOre : "");
        ByteBufUtils.writeUTF8String(buf, inputOre != null ? inputOre : "");
        ByteBufUtils.writeUTF8String(buf, ncItem != null ? ncItem : "");
        ByteBufUtils.writeUTF8String(buf, blacklistInput != null ? blacklistInput : "");
        ByteBufUtils.writeUTF8String(buf, blacklistOutput != null ? blacklistOutput : "");
        ByteBufUtils.writeUTF8String(buf, replacements != null ? replacements : "");
        buf.writeInt(targetTier);
        buf.writeBoolean(reopenGui);
    }

    public static class Handler implements IMessageHandler<PacketSaveFields, IMessage> {

        @Override
        public IMessage onMessage(PacketSaveFields message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ItemStack held = player.getCurrentEquippedItem();

            // 3.22.0-fix47 **真 bug 修复**：这里原来只认移植过来的 ItemPatternGenerator，
            // 而实际注册使用的是本模组自己的 generator/ItemSmartPatternGenerator（extends Item）
            // ⇒ 判断恒为假、字段**从来没被保存过**（界面每次打开都是空的）。现在两者都认。
            boolean ours = held != null && held.getItem() instanceof com.wztwzt.ae2_qof.generator.ItemSmartPatternGenerator;
            if (held != null && (held.getItem() instanceof ItemPatternGenerator || ours)) {
                ItemPatternGenerator.saveAllFields(
                    held,
                    message.recipeMap,
                    message.outputOre,
                    message.inputOre,
                    message.ncItem,
                    message.blacklistInput,
                    message.blacklistOutput,
                    message.replacements,
                    message.targetTier);
                com.wztwzt.ae2_qof.MyMod.LOG.info(
                    "[AE2QoL] 生成器字段已保存到手持物品：map='{}' tier={} 来源={}",
                    message.recipeMap,
                    message.targetTier,
                    ours ? "本模组生成器物品" : "移植物品");
            } else if (held != null) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn(
                    "[AE2QoL] 拒绝保存生成器字段：手持物品不是生成器（{}）",
                    held.getItem()
                        .getClass()
                        .getName());
            }

            // 3.22.0-fix47：对照表选行后由服务端重新打开生成器界面（独立 GuiScreen 关掉后要能回到主页）
            if (message.reopenGui) {
                player.openGui(
                    com.wztwzt.ae2_qof.MyMod.instance,
                    ItemPatternGenerator.GUI_ID,
                    player.worldObj,
                    0,
                    0,
                    0);
            }

            return null;
        }
    }
}
