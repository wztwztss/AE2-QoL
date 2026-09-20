package com.wztwzt.ae2_qof.util;

import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable exact item identity; quantity is deliberately not part of the key. */
public final class ItemIdentity {
    private final Item item;
    private final int damage;
    private final NBTTagCompound tag;
    private final int hash;

    private ItemIdentity(ItemStack stack) {
        item = stack.getItem();
        damage = stack.getItemDamage();
        tag = stack.getTagCompound() == null ? null : (NBTTagCompound) stack.getTagCompound().copy();
        hash = 31 * (31 * System.identityHashCode(item) + damage) + Objects.hashCode(tag);
    }

    public static ItemIdentity of(ItemStack stack) {
        return stack == null || stack.getItem() == null ? null : new ItemIdentity(stack);
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemIdentity)) return false;
        ItemIdentity key = (ItemIdentity) other;
        return item == key.item && damage == key.damage && Objects.equals(tag, key.tag);
    }

    @Override
    public int hashCode() { return hash; }
}
