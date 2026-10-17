package com.wztwzt.ae2_qof.proline;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/**
 * 产线物品条目（设计 §2.1 ItemRow）。
 *
 * <p>
 * key 规则（v6 拍板安装在案）：物品 = {@code modid:name@meta}；流体 = {@code fluid:注册名}。
 * <b>v1 有意忽略 NBT</b>（item@meta 级聚合，NBT 变体会被合并——见设计 §八风险 5），
 * 所以这里只从 ItemStack 取注册名/材质 ID 组串，不抄 TagCompound。
 */
public final class ItemRow {

    public String key;
    /** wiki 原始名（未解析成注册键时保留，4.2.0 解析器据此回填 key）。 */
    public String raw;
    /** 每周期数量（理论配比，wiki 导入或手工录入）。 */
    public long amount;
    public boolean isFluid;

    public ItemRow() {}

    public ItemRow(String key, long amount, boolean isFluid) {
        this.key = key;
        this.amount = amount;
        this.isFluid = isFluid;
    }

    public ItemRow(String key, String raw, long amount, boolean isFluid) {
        this.key = key;
        this.raw = raw;
        this.amount = amount;
        this.isFluid = isFluid;
    }

    /** 统一串格式：物品取注册名@meta（无 meta 时省略），流体前缀 fluid:。注册名取法与终端 stackKey 同源。 */
    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        Object reg = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        String name = reg == null ? "?" : reg.toString();
        return name + (stack.getItemDamage() == 0 ? "" : "@" + stack.getItemDamage());
    }

    public static ItemRow itemOf(ItemStack stack, long amount) {
        if (stack != null && stack.getItem() != null && stack.stackSize > 0 && amount <= 0) {
            amount = stack.stackSize;
        }
        return new ItemRow(keyOf(stack), amount <= 0 ? 1 : amount, false);
    }

    public static ItemRow fluidOf(FluidStack fs, long amount) {
        if (fs != null && fs.getFluid() != null && amount <= 0) {
            amount = fs.amount;
        }
        return new ItemRow(
            "fluid:" + (fs == null || fs.getFluid() == null ? ""
                : fs.getFluid()
                    .getName()),
            amount <= 0 ? 1 : amount,
            true);
    }

    /** AE 采样查询用语（A3 拍板：每轮全表快照后按键匹配）。 */
    public boolean matchesStack(ItemStack stack) {
        return !isFluid && stack != null && key.equals(keyOf(stack));
    }

    public boolean matchesFluid(FluidStack fs) {
        return isFluid && fs != null
            && fs.getFluid() != null
            && key.equals(
                "fluid:" + fs.getFluid()
                    .getName());
    }

    public NBTTagCompound writeNbt(NBTTagCompound tag) {
        tag.setString("Key", key);
        if (raw != null && !raw.isEmpty()) tag.setString("Raw", raw);
        tag.setLong("Amt", amount);
        tag.setBoolean("Fluid", isFluid);
        return tag;
    }

    public static ItemRow readNbt(NBTTagCompound tag) {
        ItemRow r = new ItemRow();
        r.key = tag.getString("Key");
        r.raw = tag.hasKey("Raw") ? tag.getString("Raw") : "";
        r.amount = tag.getLong("Amt");
        r.isFluid = tag.getBoolean("Fluid");
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemRow)) return false;
        ItemRow r = (ItemRow) o;
        return amount == r.amount && isFluid == r.isFluid && key.equals(r.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 31 + (int) (amount ^ (amount >>> 32)) * 17 + (isFluid ? 5 : 0);
    }
}
