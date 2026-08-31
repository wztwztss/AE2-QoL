package com.wztwzt.ae2_qof;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

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

    @Override
    public String getTranslatedTabLabel() {
        return "AE2 QoL";
    }
}
