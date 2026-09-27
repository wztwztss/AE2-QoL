package com.wztwzt.ae2_qof.wildcard;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 机器侧的"按样板格设置"容器（4.0.0 新增）：{@code 槽位下标 → }{@link SlotSettings}。
 *
 * <h2>存哪、怎么持久化</h2>
 * 由 {@code mixin/**&#47;MixinSlotSettings*Holder} 在每个宿主机器里挂一个 {@code @Unique} 字段，
 * 并注入该机器自己的 {@code saveNBTData/loadNBTData}（GT / GTNL / PH 三族都声明了这两个方法，签名一致）
 * 存到机器 NBT 的 {@code ae2qolSlotMeta} 键下 ⇒ 重启与区块重载后保持。
 *
 * <h2>为什么按下标对齐、稀疏存</h2>
 * GTNL 会自动整理样板、玩家也会移动样板 ⇒ 设置按**槽位下标**对齐；整理/移动后按下标重排。
 * 只有**非空**的格才写进 NBT（144 格 × 9 催化位若全存会把机器 NBT 撑爆）。
 *
 * <h2>口径（用户 2026-09-27 定）</h2>
 * <ul>
 * <li><b>电路</b>：样板自带电路**插入时自动填入本格**（{@link #autoFillCircuitFromPattern}），
 * 玩家可在弹窗手改覆盖（{@code circuitExplicit=true}）；**我们不再写机器那个全局电路槽**。</li>
 * <li><b>催化剂</b>：玩家放进本格 9 格；该格 push 时随该格输入推进总线；合成完收回本格。</li>
 * </ul>
 */
public final class SlotSettingsStore {

    /** 机器 NBT 里的键名。 */
    public static final String NBT_KEY = "ae2qolSlotMeta";

    private final Map<Integer, SlotSettings> bySlot = new HashMap<>();

    /** 取某格设置；{@code create=true} 时不存在就建一个空壳。 */
    public SlotSettings get(int slot, boolean create) {
        if (slot < 0) return null;
        SlotSettings settings = bySlot.get(slot);
        if (settings == null && create) {
            settings = new SlotSettings();
            bySlot.put(slot, settings);
        }
        return settings;
    }

    /** 清除某格设置（"清除本格设置"按钮）。 */
    public void clear(int slot) {
        bySlot.remove(slot);
    }

    /**
     * 样板自带电路 → **自动填入本格**（用户口径：自动识别到本格，而不是去改全局电路槽）。
     * 玩家手改过（{@code circuitExplicit}）的格**不覆盖**。
     */
    public void autoFillCircuitFromPattern(int slot, int patternCircuit) {
        if (patternCircuit < 0) return;
        SlotSettings settings = get(slot, true);
        if (settings == null || settings.circuitExplicit) return;
        if (settings.circuit != patternCircuit) {
            settings.circuit = patternCircuit;
        }
    }

    /**
     * 该格**最终生效**的电路号：手改/自动填入的本格值优先；本格没有则回落到样板自带；再没有返回 -1（交给整机）。
     */
    public int effectiveCircuit(int slot, int patternCircuit) {
        SlotSettings settings = get(slot, false);
        if (settings != null && settings.circuit >= 0) return settings.circuit;
        return patternCircuit;
    }

    public NBTTagCompound save() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, SlotSettings> entry : bySlot.entrySet()) {
            SlotSettings settings = entry.getValue();
            if (settings == null || settings.isEmpty()) continue;
            NBTTagCompound item = settings.write();
            item.setInteger("Slot", entry.getKey());
            list.appendTag(item);
        }
        root.setTag("Slots", list);
        return root;
    }

    public void load(NBTTagCompound root) {
        bySlot.clear();
        if (root == null) return;
        NBTTagList list = root.getTagList("Slots", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            int slot = item.getInteger("Slot");
            if (slot < 0) continue;
            SlotSettings settings = new SlotSettings();
            settings.read(item);
            bySlot.put(slot, settings);
        }
    }
}
