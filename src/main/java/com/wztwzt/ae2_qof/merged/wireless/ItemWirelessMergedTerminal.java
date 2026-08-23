package com.wztwzt.ae2_qof.merged.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.merged.BlockMergedTerminal;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;

/**
 * 手持无线二合一终端。
 * <p>
 * 绑定方式与原版无线终端一致：放入 ME 安全终端的编码槽，
 * 由 AE2 调 {@link #setEncryptionKey} 写入绑定密钥（NBT "encryptionKey"）。
 * <p>
 * 打开前经 AE2 注册表 performCheck 完整校验（未绑定 / 站点无法定位 /
 * 电量——本终端免电由 hasInfinityPower 跳过；范围/维度限制由
 * hasInfinityRange 跳过，即官方预留的跨维度钩子）。
 * 玩家对网络的存取权限仍由网络安全层（生物卡）强制，与本物品无关。
 */
public class ItemWirelessMergedTerminal extends Item implements IWirelessTermHandler {

    /** NBT 键名对齐 GTNH 惯例（安全终端编码槽识别 INetworkEncodable 后写入） */
    public static final String KEY_ENCRYPTION = "encryptionKey";

    public ItemWirelessMergedTerminal() {
        setUnlocalizedName("wireless_merged_terminal");
        setTextureName("appliedenergistics2:ToolWirelessTerminal");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    // ===== 注册 =====

    /** preInit 时调用：注册进 AE2 无线终端注册表（安全终端编码槽自动识别） */
    public void registerWirelessHandler() {
        AEApi.instance()
            .registries()
            .wireless()
            .registerWirelessHandler(this);
    }

    // ===== IWirelessTermHandler =====

    @Override
    public boolean canHandle(ItemStack is) {
        return is != null && is.getItem() == this;
    }

    @Override
    public boolean usePower(EntityPlayer player, double amount, ItemStack is) {
        return true; // 免电
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amount, ItemStack is) {
        return true; // 免电
    }

    @Override
    public boolean hasInfinityPower(ItemStack is) {
        return true; // 跳过注册表电量检查
    }

    @Override
    public boolean hasInfinityRange(ItemStack is) {
        return true; // 跨维度核心钩子：跳过距离与同维度检查，权限不受影响
    }

    @Override
    public appeng.api.util.IConfigManager getConfigManager(ItemStack is) {
        return null; // 不提供原版终端的配置面板
    }

    // ===== INetworkEncodable =====

    @Override
    public String getEncryptionKey(ItemStack item) {
        if (item == null || item.stackTagCompound == null) return "";
        return item.stackTagCompound.getString(KEY_ENCRYPTION);
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        if (item == null) return;
        if (item.stackTagCompound == null) item.stackTagCompound = new NBTTagCompound();
        item.stackTagCompound.setString(KEY_ENCRYPTION, encKey == null ? "" : encKey);
    }

    // ===== 交互 =====

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, java.util.List list, boolean advanced) {
        for (int i = 1; i <= 5; i++) {
            String key = "item.wireless_merged_terminal.tooltip." + i;
            String line = net.minecraft.util.StatCollector.translateToLocal(key);
            if (line != null && !line.isEmpty() && !line.equals(key)) {
                list.add(net.minecraft.util.EnumChatFormatting.GRAY + line);
            }
        }
        String key = getEncryptionKey(stack);
        if (key == null || key.isEmpty()) {
            list.add(
                net.minecraft.util.EnumChatFormatting.RED
                    + net.minecraft.util.StatCollector.translateToLocal("item.wireless_merged_terminal.unbound"));
        } else {
            list.add(
                net.minecraft.util.EnumChatFormatting.GREEN
                    + net.minecraft.util.StatCollector.translateToLocal("item.wireless_merged_terminal.bound"));
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World world, EntityPlayer player) {
        if (!world.isRemote) {
            // 完整校验链免费复用 AE2 注册表（含本地化错误消息）：
            // 未绑定 → DeviceNotLinked；站点拆除 → StationCanNotBeLocated；
            // 范围/电量两步被 hasInfinityRange/hasInfinityPower 跳过
            if (AEApi.instance()
                .registries()
                .wireless()
                .performCheck(is, player)) {
                final int slot = player.inventory.currentItem;
                FMLNetworkHandler
                    .openGui(player, MyMod.instance, BlockMergedTerminal.WIRELESS_GUI_ID, world, slot, -1, -1);
            }
        }
        return is;
    }
}
