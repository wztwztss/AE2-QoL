package com.wztwzt.ae2_qof.wireless;

import java.util.List;
import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkData;
import com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkManager;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.tile.networking.TileCableBus;

public class ItemWirelessConnector extends Item {

    public ItemWirelessConnector() {
        setUnlocalizedName("wireless_connect");
        setTextureName("ae2_qof:wireless_connect");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("item.wireless_connect.tooltip.1"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wireless_connect.tooltip.2"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wireless_connect.tooltip.3"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wireless_connect.tooltip.4"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wireless_connect.tooltip.5"));
        String bound = getBoundFrequency(stack);
        if (bound != null && !bound.isEmpty()) {
            list.add(
                EnumChatFormatting.GREEN
                    + StatCollector.translateToLocalFormatted("item.wireless_connect.tooltip.bound", bound));
        }
    }

    private boolean isBindingTarget(TileEntity te) {
        if (te instanceof TileWirelessTransceiver) return true;
        if (te instanceof TileCableBus) return true;
        if (te instanceof IGridHost) return true;
        return false;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);

        if (!isBindingTarget(te)) {
            return false;
        }

        if (world.isRemote) {
            return false;
        }

        if (player.isSneaking()) {
            if (te instanceof TileWirelessTransceiver) {
                TileWirelessTransceiver twt = (TileWirelessTransceiver) te;
                if (twt.isMode()) {
                    String freq = twt.getFrequency();
                    if (freq != null && !freq.isEmpty()) {
                        NBTTagCompound tag = stack.stackTagCompound;
                        if (tag == null) tag = new NBTTagCompound();
                        tag.setString("boundChannel", freq);
                        stack.stackTagCompound = tag;
                        player.addChatMessage(
                            new ChatComponentTranslation("ae2_qof.wireless.bind.success", freq)
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                    } else {
                        player.addChatMessage(
                            new ChatComponentTranslation("ae2_qof.wireless.bind.fail.no_channel")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    }
                } else {
                    player.addChatMessage(
                        new ChatComponentTranslation("ae2_qof.wireless.bind.fail.not_sender")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                }
            }
            return true;
        }

        String boundFreq = getBoundFrequency(stack);
        if (boundFreq == null || boundFreq.isEmpty()) {
            player.addChatMessage(
                new ChatComponentTranslation("ae2_qof.wireless.bind.fail.no_binding")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            return true;
        }

        if (te instanceof TileWirelessTransceiver) {
            TileWirelessTransceiver twt = (TileWirelessTransceiver) te;
            twt.setFrequency(boundFreq);
            twt.setMode(false);
            twt.savePlacer(player.getUniqueID());
            twt.markDirty();
            player.addChatMessage(
                new ChatComponentTranslation("ae2_qof.wireless.connect.success", boundFreq)
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
            return true;
        }

        if (te instanceof TileCableBus || te instanceof IGridHost) {
            // 防御性检查，实际不可达：玩家只能右键自己所在维度的方块，
            // te.getWorldObj() 与 player.worldObj 恒同维度；各维度分别绑定后由
            // WirelessBlockLinkManager.processAll 按 link.dimension 跨维度建链（#53 复核）
            if (te.getWorldObj().provider.dimensionId != player.worldObj.provider.dimensionId) {
                player.addChatMessage(
                    new ChatComponentTranslation("ae2_qof.wireless.bind.fail.cross_dimension")
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                return true;
            }

            IGridNode targetNode = getGridNodeFromTE(te, side);
            if (targetNode == null) {
                player.addChatMessage(
                    new ChatComponentTranslation("ae2_qof.wireless.bind.fail.no_grid")
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                return true;
            }

            String posKey = te.getWorldObj().provider.dimensionId + ":" + te.xCoord + ":" + te.yCoord + ":" + te.zCoord;

            for (WirelessBlockLinkData existing : WirelessBlockLinkManager.instance()
                .getLinks(boundFreq)) {
                if (posKey.equals(existing.getPositionKey())) {
                    WirelessBlockLinkManager.instance()
                        .unregister(boundFreq, posKey);
                    WirelessWorldData.get(te.getWorldObj())
                        .removeBlockLink(boundFreq, posKey);
                    player.addChatMessage(
                        new ChatComponentTranslation("ae2_qof.wireless.unlink.success", boundFreq)
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
                    return true;
                }
            }

            UUID ownerUuid = player.getUniqueID();
            WirelessBlockLinkData linkData = new WirelessBlockLinkData(
                te.xCoord,
                te.yCoord,
                te.zCoord,
                boundFreq,
                ownerUuid,
                te.getWorldObj().provider.dimensionId,
                side);

            WirelessBlockLinkManager.instance()
                .register(boundFreq, linkData);
            WirelessWorldData.get(te.getWorldObj())
                .addBlockLink(linkData);

            player.addChatMessage(
                new ChatComponentTranslation("ae2_qof.wireless.blocklink.success", boundFreq)
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
            return true;
        }

        player.addChatMessage(
            new ChatComponentTranslation("ae2_qof.wireless.bind.fail.invalid_target")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return true;
    }

    private IGridNode getGridNodeFromTE(TileEntity te, int side) {
        if (te instanceof TileCableBus) {
            ForgeDirection dir = ForgeDirection.getOrientation(side);
            IGridNode node = ((TileCableBus) te).getGridNode(dir);
            if (node != null) return node;
            IPart part = ((TileCableBus) te).getPart(dir);
            if (part != null) {
                node = part.getExternalFacingNode();
                if (node != null) return node;
            }
            return null;
        }

        if (te instanceof IGridHost) {
            IGridHost host = (IGridHost) te;
            IGridNode node = host.getGridNode(ForgeDirection.getOrientation(side));
            if (node != null) return node;
            node = host.getGridNode(ForgeDirection.UNKNOWN);
            if (node != null) return node;
            for (int i = 0; i < 6; i++) {
                node = host.getGridNode(ForgeDirection.getOrientation(i));
                if (node != null) return node;
            }
        }

        return null;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (stack.stackTagCompound != null) {
                stack.stackTagCompound.removeTag("boundChannel");
                if (stack.stackTagCompound.hasNoTags()) {
                    stack.stackTagCompound = null;
                }
                if (!world.isRemote) {
                    player.addChatMessage(
                        new ChatComponentTranslation("ae2_qof.wireless.bind.cleared")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
                }
            }
            return stack;
        }
        if (!world.isRemote) {
            String bound = getBoundFrequency(stack);
            if (bound != null && !bound.isEmpty()) {
                player.addChatMessage(
                    new ChatComponentTranslation("ae2_qof.wireless.hint.use_device", bound)
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
            } else {
                player.addChatMessage(
                    new ChatComponentTranslation("ae2_qof.wireless.hint.bind_first")
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
            }
        }
        return stack;
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return false;
    }

    public static String getBoundFrequency(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return null;
        return stack.stackTagCompound.getString("boundChannel");
    }
}
