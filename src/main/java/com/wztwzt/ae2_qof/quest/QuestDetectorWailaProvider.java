package com.wztwzt.ae2_qof.quest;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.tile.TileQuestDetector;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * 「ME 任务检测器」WAILA/JADE 显示（3.11.0）：绑定玩家、供电状态。
 * 注册经 IMC（见 CommonProxy），WAILA 未安装时零影响。
 */
public class QuestDetectorWailaProvider implements IWailaDataProvider {

    public static void register(IWailaRegistrar registrar) {
        QuestDetectorWailaProvider instance = new QuestDetectorWailaProvider();
        registrar.registerBodyProvider(instance, TileQuestDetector.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return accessor.getStack();
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity te = accessor.getTileEntity();
        if (!(te instanceof TileQuestDetector)) {
            return currenttip;
        }
        TileQuestDetector tile = (TileQuestDetector) te;
        String ownerName = tile.getOwnerNameForDisplay();
        if (ownerName != null && !ownerName.isEmpty()) {
            currenttip.add(
                StatCollector.translateToLocal("waila.ae2qol.quest_detector.owner") + ": " + ownerName);
        }
        boolean powered = tile.isPowered();
        currenttip.add(
            (powered ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + StatCollector.translateToLocal("waila.ae2qol.quest_detector.powered")
                + ": "
                + StatCollector.translateToLocal(
                    powered ? "waila.ae2qol.quest_detector.on" : "waila.ae2qol.quest_detector.off"));
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (te != null) {
            te.writeToNBT(tag);
        }
        return tag;
    }
}
