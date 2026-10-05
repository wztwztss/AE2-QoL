package com.wztwzt.ae2_qof.ph;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchAccess;

import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 3.24.0：给我们的 MK.III / MK.IV 补上「样板 + 倍率 + 伴生状态」的**存读档**。
 *
 * <h2>为什么需要（修的是既有 bug，不是新功能）</h2>
 * PH 只在自己的内部类 {@code PatternDualInputHatch$Inst} 里实现 {@code saveNBTData/loadNBTData}；
 * 而我们的真实方块实体是 {@code MTEPatternCraftingBufferMKIII/MKIV$Inst}，**直接继承
 * {@code PatternDualInputHatch}**，不在那条链上 ⇒ 基类链（{@code BufferedDualInputHatch} /
 * {@code DualInputHatch} / GT {@code MTEHatch}）只保存 {@code BUFFER_*} 等缓冲，**从不保存
 * {@code pattern} / {@code multiplier}**。后果：重进世界或重载区块后，机器里的样板、每格倍率、
 * 优化开关、自定义名与 AE 代理状态**全部丢失**（表现为"样板不见了"）。
 *
 * <h2>键名与语义完全照 PH（两个模组的存档可互读）</h2>
 * <ul>
 * <li>{@code patternSlots}：复合标签，键 {@code i0..iN}，每个是 {@code ItemStack.writeToNBT} 的结果（只写非空槽）；</li>
 * <li>{@code multiplier}：int 数组，**最小值 1**（PH 约定，0/负数一律抬到 1）；</li>
 * <li>{@code customName} / {@code additionalConnection} / {@code restrictToInt} / {@code allowopt} /
 * {@code normalopt} / {@code saved}：PH 的伴生设置与寿命统计；</li>
 * <li>AE 代理状态：{@code getProxy().writeToNBT/readFromNBT}，读完再
 * {@code updateValidGridProxySides()}（与 PH 的 {@code Inst} 一致）。</li>
 * </ul>
 *
 * <p>槽位数由调用方传入（MK.III=144、MK.IV=360）：读档时**按目标槽位重建数组**，
 * 因此"144 槽的旧档"也能被 360 槽的新机器安全读入（前 144 张原位、其余留空、倍率补 1）；
 * 反向（360 档 → 144 机器）则只取前 144 张（超出部分读档时被自然截断，调用方需自行提示）。
 */
public final class PatternSlotPersistence {

    private PatternSlotPersistence() {}

    /** 写档：样板 + 倍率 + 伴生状态 + AE 代理。调用方随后仍要调 {@code super.saveNBTData}。 */
    public static void save(PatternDualInputHatch mte, NBTTagCompound aNBT) {
        MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) mte;

        NBTTagCompound tag = new NBTTagCompound();
        ItemStack[] patterns = acc.getAe2qolPattern();
        if (patterns != null) {
            for (int i = 0; i < patterns.length; i++) {
                if (patterns[i] != null) {
                    tag.setTag("i" + i, patterns[i].writeToNBT(new NBTTagCompound()));
                }
            }
        }
        aNBT.setTag("patternSlots", tag);

        int[] multiplier = acc.getAe2qolMultiplier();
        aNBT.setIntArray("multiplier", multiplier != null ? multiplier : new int[0]);

        String customName = acc.getAe2qolCustomName();
        if (customName != null) {
            aNBT.setString("customName", customName);
        }
        aNBT.setBoolean("additionalConnection", acc.getAe2qolAdditionalConnection());
        aNBT.setBoolean("restrictToInt", mte.restrictToInt);
        aNBT.setBoolean("allowopt", acc.getAe2qolAllowOpt());
        aNBT.setBoolean("normalopt", acc.getAe2qolNormalOpt());
        aNBT.setLong("saved", acc.getAe2qolSaved());

        mte.getProxy()
            .writeToNBT(aNBT);
    }

    /**
     * 读档：把 {@code slots} 个槽位全部重建（缺的留 null / 倍率 1）。
     * 调用方随后应调自己的 {@code ensureSlots()} 做长度自愈（本方法已按 slots 建好，通常是无操作）。
     */
    public static void load(PatternDualInputHatch mte, int slots, NBTTagCompound aNBT) {
        MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) mte;

        ItemStack[] patterns = new ItemStack[slots];
        NBTTagCompound tag = aNBT.getCompoundTag("patternSlots");
        if (tag != null) {
            for (int i = 0; i < slots; i++) {
                if (tag.hasKey("i" + i)) {
                    patterns[i] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("i" + i));
                }
            }
        }
        acc.setAe2qolPattern(patterns);

        int[] src = aNBT.getIntArray("multiplier");
        int[] multiplier = new int[slots];
        for (int i = 0; i < slots; i++) {
            multiplier[i] = 1;
        }
        if (src != null) {
            for (int i = 0; i < Math.min(src.length, slots); i++) {
                multiplier[i] = Math.max(src[i], 1);
            }
        }
        acc.setAe2qolMultiplier(multiplier);

        acc.setAe2qolAdditionalConnection(aNBT.getBoolean("additionalConnection"));
        if (aNBT.hasKey("customName")) {
            acc.setAe2qolCustomName(aNBT.getString("customName"));
        }
        mte.restrictToInt = aNBT.getBoolean("restrictToInt");
        // allowopt 在 PH 的字段初始值是 true；老档没有这个键时不能把它误判成 false
        acc.setAe2qolAllowOpt(aNBT.hasKey("allowopt") ? aNBT.getBoolean("allowopt") : true);
        acc.setAe2qolNormalOpt(aNBT.getBoolean("normalopt"));
        acc.setAe2qolSaved(aNBT.getLong("saved"));

        mte.getProxy()
            .readFromNBT(aNBT);
        acc.invokeAe2qolUpdateValidGridProxySides();
    }
}
