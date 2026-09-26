package com.wztwzt.ae2_qof;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class AE2QoLCreativeTab extends CreativeTabs {

    public static final AE2QoLCreativeTab INSTANCE = new AE2QoLCreativeTab();

    private AE2QoLCreativeTab() {
        super("ae2_qof");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        return net.minecraft.init.Items.nether_star;
    }

    /**
     * 把「编程样板输入总成 MK.III」加进本标签页。
     * 它是 GT 机器（走 {@code sBlockMachines} 的 meta 值），{@code setCreativeTab} 管不到，
     * 只能在这里显式追加。未安装 ProgrammableHatches 或注册失败时 {@code mkiiiStack} 为 null，
     * 因此这里天然只在装了 PH 时才会出现该物品。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void displayAllReleventItems(List<ItemStack> list) {
        super.displayAllReleventItems(list);
        ItemStack mkiii = com.wztwzt.ae2_qof.ph.PhIntegration.mkiiiStack;
        if (mkiii != null) {
            list.add(mkiii.copy());
        }
    }

    @Override
    public String getTranslatedTabLabel() {
        return "AE2 QoL";
    }
}
