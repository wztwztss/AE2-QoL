package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * **某一个样板格的独立设置**（4.0.0 新增）：该格自己的电路号 + 9 个催化剂位。
 *
 * <h2>为什么要有它</h2>
 * 在此之前，电路存在**样板自己**的 NBT 里，由 {@code SmartWildcardCircuit} 在索引期写进
 * **机器唯一**的虚拟电路槽 ⇒ 同一舱室放两张通配样板时**后索引的覆盖前一张**（用户实测 MK.III：
 * 第一张 1 号电路、第二张 2 号电路，后者覆盖前者），等于"一个舱室只能放一张通配样板"。
 * 把电路与催化剂下沉到"按格"，两张样板就不再争抢那一个全局槽。
 *
 * <h2>取值三级（自上而下，见 {@link SlotSettingsStore#effectiveCircuit}）</h2>
 * <ol>
 * <li>{@link #circuit}：玩家在本格弹窗里**手改**的值（最高）；</li>
 * <li>样板自带电路**插入时自动填入**本格（用户 2026-09-27 定的口径：自动识别到本格，而不是改全局槽）；</li>
 * <li>整机电路设置：我们**完全不碰**，由玩家自己使用。</li>
 * </ol>
 *
 * <h2>催化剂语义（用户原话）</h2>
 * 「每次我单独放，合成的时候被推进总线，合成完返回」⇒ 玩家放进这 9 格；
 * 该格 push 时把**非空**的那些随该格输入一起推进机器输入总线；配方按 GT notConsumed 语义不消耗；
 * 合成结束后**收回本格这 9 格**（不是回 AE）。
 */
public final class SlotSettings {

    /** 催化剂位数（用户指定：9 个）。 */
    public static final int CATALYST_SLOTS = 9;

    /** 该格电路：{@code -1} = 未设置（走样板自带自动填入值 → 再由上层决定是否用整机）。 */
    public int circuit = -1;

    /** 该格 9 个催化剂位（可含 null）。 */
    public final ItemStack[] catalysts = new ItemStack[CATALYST_SLOTS];

    /** 玩家是否手改过电路（手改过就不再被"样板自带自动填入"覆盖）。 */
    public boolean circuitExplicit;

    public boolean isEmpty() {
        if (circuit >= 0 || circuitExplicit) return false;
        for (ItemStack stack : catalysts) {
            if (stack != null && stack.getItem() != null) return false;
        }
        return true;
    }

    public boolean hasCatalysts() {
        for (ItemStack stack : catalysts) {
            if (stack != null && stack.getItem() != null && stack.stackSize > 0) return true;
        }
        return false;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Circuit", circuit);
        tag.setBoolean("CircuitExplicit", circuitExplicit);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < CATALYST_SLOTS; i++) {
            ItemStack stack = catalysts[i];
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            if (stack != null && stack.getItem() != null) {
                // 只存非空项（稀疏），避免 144 格 × 9 格把机器 NBT 撑爆
                stack.writeToNBT(entry);
            }
            list.appendTag(entry);
        }
        tag.setTag("Catalysts", list);
        return tag;
    }

    public void read(NBTTagCompound tag) {
        if (tag == null) return;
        this.circuit = tag.getInteger("Circuit");
        this.circuitExplicit = tag.getBoolean("CircuitExplicit");
        NBTTagList list = tag.getTagList("Catalysts", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < 0 || slot >= CATALYST_SLOTS) continue;
            catalysts[slot] = ItemStack.loadItemStackFromNBT(entry);
        }
    }
}
