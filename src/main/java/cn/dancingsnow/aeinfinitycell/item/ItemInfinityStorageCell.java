package cn.dancingsnow.aeinfinitycell.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cn.dancingsnow.aeinfinitycell.AEInfinityCell;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellDataAccess;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import fox.spiteful.avaritia.render.IHaloRenderItem;

public final class ItemInfinityStorageCell extends Item implements IHaloRenderItem {

    private static final String TAG_ROOT = AEInfinityCell.MODID;
    private static final String TAG_STORAGE_ID = "storageId";

    public IIcon halo;

    public ItemInfinityStorageCell() {
        setUnlocalizedName(AEInfinityCell.MODID + ".infinity_storage_cell");
        setTextureName(AEInfinityCell.MODID + ":infinity_storage_cell");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        super.registerIcons(register);

        halo = register.registerIcon("avaritia:halo");
    }

    @Override
    public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, List<String> tooltip,
        boolean advanced) {
        UUID id = getStorageId(stack);
        tooltip.add(StatCollector.translateToLocal("tooltip.aeinfinitycell.infinity_storage_cell"));
        if (id != null && advanced) {
            tooltip.add(id.toString());
        }
        if (id != null && player != null && player.worldObj != null && player.worldObj.isRemote) {
            appendLiveStats(tooltip, id);
        }
    }

    /**
     * 悬停统计（3.13.0，仅客户端）：先查缓存，未命中则节流请求服务端汇总
     * （内容存世界存档，客户端无法直接读取）。布局顺序：总计 → 物品 → 流体 → 源质/EU；
     * 默认字母单位（12.34M），按住 Ctrl 切换科学计数法。
     */
    @SideOnly(Side.CLIENT)
    private static void appendLiveStats(List<String> tooltip, UUID id) {
        long[] s = com.wztwzt.ae2_qof.client.InfinityCellTooltipCache.get(id);
        if (s == null) {
            if (com.wztwzt.ae2_qof.client.InfinityCellTooltipCache.shouldRequest(id)) {
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL
                    .sendToServer(new com.wztwzt.ae2_qof.network.InfinityCellStatsPacket(id));
            }
            tooltip.add(
                net.minecraft.util.EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("tooltip.aeinfinitycell.stats.waiting"));
            return;
        }
        boolean noData = s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0 && s[5] == 0;
        if (noData) {
            tooltip.add(
                net.minecraft.util.EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("tooltip.aeinfinitycell.stats.empty"));
            return;
        }
        boolean sci = com.wztwzt.ae2_qof.util.BigNumFormatter.isCtrlDown();
        java.math.BigInteger itemUnits = java.math.BigInteger.valueOf(s[1]);
        java.math.BigInteger fluidUnits = java.math.BigInteger.valueOf(s[3]);
        java.math.BigInteger essentiaUnits = java.math.BigInteger.valueOf(s[5]);
        java.math.BigInteger euUnits = java.math.BigInteger.valueOf(s[7]);
        long totalTypes = s[0] + s[2] + s[4]
            + (s[7] > 0 ? Math.min(1, s[6]) : 0);
        java.math.BigInteger totalUnits = itemUnits.add(fluidUnits).add(essentiaUnits)
            .add(euUnits);

        String total = sci
            ? com.wztwzt.ae2_qof.util.BigNumFormatter.formatSci(totalUnits)
            : com.wztwzt.ae2_qof.util.BigNumFormatter.format(totalUnits);

        // 总计行：∞ Bytes | 共 N 类 / M 件 ≈N KB
        tooltip.add(
            net.minecraft.util.EnumChatFormatting.WHITE + "\u221E Bytes " + net.minecraft.util.EnumChatFormatting.GRAY
                + "| "
                + StatCollector.translateToLocal("tooltip.aeinfinitycell.stats.total")
                + ": "
                + totalTypes
                + " / "
                + total
                + " ("
                + com.wztwzt.ae2_qof.util.BigNumFormatter.format(itemBytes(s[0], s[1])
                    .add(fluidBytes(s[2], s[3]))
                    .add(essentiaBytes(s[4], s[5])))
                + " B)");

        if (s[0] > 0 || s[1] > 0) {
            tooltip.add(row("stats.items", s[0], itemUnits, itemBytes(s[0], s[1]), sci));
        }
        if (s[2] > 0 || s[3] > 0) {
            tooltip.add(row("stats.fluids", s[2], fluidUnits, fluidBytes(s[2], s[3]), sci));
        }
        if (s[4] > 0 || s[5] > 0) {
            tooltip.add(row("stats.essentia", s[4], essentiaUnits, essentiaBytes(s[4], s[5]), sci));
        }
        if (s[7] > 0) {
            String v = sci
                ? com.wztwzt.ae2_qof.util.BigNumFormatter.formatSci(euUnits)
                : com.wztwzt.ae2_qof.util.BigNumFormatter.format(euUnits);
            tooltip.add(
                net.minecraft.util.EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("tooltip.aeinfinitycell.stats.eu")
                    + ": "
                    + v);
        }
        tooltip.add(
            net.minecraft.util.EnumChatFormatting.DARK_GRAY
                + StatCollector.translateToLocal("tooltip.aeinfinitycell.stats.hint"));
    }

    @SideOnly(Side.CLIENT)
    private static String row(String key, long types, java.math.BigInteger units, java.math.BigInteger bytes,
        boolean sci) {
        String amount = sci
            ? com.wztwzt.ae2_qof.util.BigNumFormatter.formatSci(units)
            : com.wztwzt.ae2_qof.util.BigNumFormatter.format(units);
        return net.minecraft.util.EnumChatFormatting.GRAY
            + StatCollector.translateToLocal("tooltip.aeinfinitycell." + key)
            + ": "
            + types
            + " / "
            + amount
            + " ("
            + com.wztwzt.ae2_qof.util.BigNumFormatter.format(bytes)
            + " B)";
    }

    // AE2 CellInventory 字节公式：每类型 8B + 每 8 单位 1B（流体/源质按 AE2 惯例 125mB/点 = 1B 折算）
    private static java.math.BigInteger itemBytes(long types, long units) {
        return java.math.BigInteger.valueOf(types * 8L + units / 8L);
    }

    private static java.math.BigInteger fluidBytes(long types, long units) {
        return java.math.BigInteger.valueOf(types * 8L + units / 125L);
    }

    private static java.math.BigInteger essentiaBytes(long types, long units) {
        return java.math.BigInteger.valueOf(types * 8L + units / 8L);
    }

    public static UUID getStorageId(ItemStack stack) {
        NBTTagCompound root = getRootTag(stack, false);
        if (root == null || !root.hasKey(TAG_STORAGE_ID)) {
            return null;
        }
        try {
            return UUID.fromString(root.getString(TAG_STORAGE_ID));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static UUID getOrCreateStorageId(ItemStack stack) {
        UUID existing = getStorageId(stack);
        if (existing != null) {
            return existing;
        }

        UUID created = UUID.randomUUID();
        getRootTag(stack, true).setString(TAG_STORAGE_ID, created.toString());
        return created;
    }

    public static InfinityCellRecord getRecord(ItemStack stack, World world) {
        if (stack == null || !(stack.getItem() instanceof ItemInfinityStorageCell)) {
            return null;
        }

        UUID id = getOrCreateStorageId(stack);
        return InfinityCellDataAccess.getOrCreate(id, world);
    }

    public static void markDirty(ItemStack stack, World world) {
        UUID id = getStorageId(stack);
        if (id == null) {
            return;
        }
        InfinityCellDataAccess.markDirty(id, world);
    }

    private static NBTTagCompound getRootTag(ItemStack stack, boolean create) {
        if (stack == null) {
            return null;
        }
        if (stack.stackTagCompound == null) {
            if (!create) {
                return null;
            }
            stack.stackTagCompound = new NBTTagCompound();
        }
        if (!stack.stackTagCompound.hasKey(TAG_ROOT, 10)) {
            if (!create) {
                return null;
            }
            stack.stackTagCompound.setTag(TAG_ROOT, new NBTTagCompound());
        }
        return stack.stackTagCompound.getCompoundTag(TAG_ROOT);
    }

    @Override
    public boolean drawHalo(ItemStack stack) {
        return true;
    }

    @Override
    public IIcon getHaloTexture(ItemStack stack) {
        return halo;
    }

    @Override
    public int getHaloSize(ItemStack stack) {
        return 10;
    }

    @Override
    public boolean drawPulseEffect(ItemStack stack) {
        return true;
    }

    @Override
    public int getHaloColour(ItemStack stack) {
        return 0xFF000000;
    }
}
