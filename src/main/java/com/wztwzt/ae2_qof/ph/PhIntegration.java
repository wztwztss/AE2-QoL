package com.wztwzt.ae2_qof.ph;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

import appeng.core.Api;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;

/**
 * 「编程样板输入总成 MK.III」的可选依赖入口（Programmable-Hatches 专用）。
 *
 * <p><b>PH 类型只出现在本方法体内</b>：{@link MTEPatternCraftingBufferMKIII} 继承 PH 的
 * {@code PatternDualInputHatch}，一旦被加载就要求 PH 在场。方法体里的类引用是**惰性解析**的，
 * 只要 {@link Loader#isModLoaded(String)} 的守卫写在最前面并在 PH 缺失时 return，
 * 未装 PH 的整合包就永远不会加载这个类 —— 与 {@code client/GuideNHIntegration} 同一套做法。
 *
 * <p>三件事按顺序发生：①注册 MTE（GT 构造器自己登记进 {@code GregTechAPI.METATILEENTITIES}）；
 * ②加工作台配方（原总成 ×1 + 大师/高级电路）；
 * ③把内部类 {@code Inst.class} 注册进 AE2 的接口终端注册表 ——
 * AE2 的 {@code Grid.getMachines(Class)} 与 {@code InterfaceTerminalRegistry} 都是**按精确类名**查表，
 * PH 自己也是这么注册 {@code PatternDualInputHatch.Inst.class} 的；漏掉这一步的后果是
 * 「AE2 接口终端与本模组样板终端都看不见这台机器」（本模组的样板上传/撤回也走同一张表）。
 */
public final class PhIntegration {

    /**
     * 注册成功后的 MK.III 物品堆（供 AE2 QoL 创造标签页使用）。未安装 PH 或注册失败时保持 null。
     * 这样做而不是让创造页去查 {@code GregTechAPI.METATILEENTITIES[32108]}，可以避免越界/未注册的边界情况。
     */
    public static ItemStack mkiiiStack;

    private PhIntegration() {}

    public static void register() {
        if (!Loader.isModLoaded("proghatches")) {
            // 未安装 ProgrammableHatches：本功能整体不存在（物品既不会注册也不会出现在创造页）
            return;
        }
        try {
            MTEPatternCraftingBufferMKIII mte = new MTEPatternCraftingBufferMKIII(
                MTEPatternCraftingBufferMKIII.MTE_ID,
                MTEPatternCraftingBufferMKIII.MTE_NAME,
                "Programmable Crafting Input Buffer MK.III",
                MTEPatternCraftingBufferMKIII.TIER,
                true,
                MTEPatternCraftingBufferMKIII.BUFFER_NUM,
                true,
                MTEPatternCraftingBufferMKIII.INPUT_PAGE,
                MTEPatternCraftingBufferMKIII.defaultDescription());

            // 配方材料里的「原总成」用 PH 的公开常量拼 ID，不写死 22069：
            // 玩家可以在 PH 配置里改 metaTileEntityOffset，写死就会配错。
            ItemStack originalBuffer = new ItemStack(
                GregTechAPI.sBlockMachines,
                1,
                reobf.proghatches.main.Config.metaTileEntityOffset
                    + reobf.proghatches.main.registration.Registration.PatternOffset);

            GameRegistry.addShapedRecipe(
                mte.getStackForm(1L),
                "cCc",
                "CXC",
                "cCc",
                'X',
                originalBuffer,
                'C',
                ItemList.Circuit_Master.get(1),
                'c',
                ItemList.Circuit_Advanced.get(1));

            mkiiiStack = mte.getStackForm(1L);

            Api.INSTANCE.registries()
                .interfaceTerminal()
                .register(MTEPatternCraftingBufferMKIII.Inst.class);

            MyMod.LOG.info(
                "[AE2QoL] PH 编程样板输入总成 MK.III 已注册：id=" + MTEPatternCraftingBufferMKIII.MTE_ID
                    + "，样板槽="
                    + MTEPatternCraftingBufferMKIII.PATTERN_SLOTS
                    + "（"
                    + MTEPatternCraftingBufferMKIII.TOTAL_ROWS
                    + " 行 × "
                    + MTEPatternCraftingBufferMKIII.GRID_COLS
                    + " 列）");
        } catch (Throwable t) {
            // 注册失败不影响本模组其它功能；吞掉会让「物品不出现」变成无痕迹故障，所以必须记日志
            MyMod.LOG.error("[AE2QoL] 编程样板输入总成 MK.III 注册失败（已跳过该物品）", t);
        }
    }
}
