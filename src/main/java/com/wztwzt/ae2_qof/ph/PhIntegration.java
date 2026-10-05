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

    /**
     * 注册成功后的 **MK.IV**（360 样板槽，3.24.0 新增）物品堆，用途同 {@link #mkiiiStack}。
     * MK.IV 与 MK.III 并存：MK.III 保持 144 槽不动（已存在的存档/机器零风险），MK.IV 是其扩容版。
     */
    public static ItemStack mkivStack;

    /**
     * 样板剪贴板物品（3.24.0）。**只在装了 PH 时注册**：它的全部逻辑都作用在 PH 家族的样板总成上，
     * 类里引用了 PH 类型，因此必须与 MK.III/MK.IV 遵循同一套「守卫之后再加载」的纪律。
     */
    public static com.wztwzt.ae2_qof.item.ItemPatternClipboard patternClipboard;

    /**
     * ProgrammableHatches 的**真实 modid**。来源是它的 {@code @Mod(modid = MyMod.MODID)}：
     * {@code MyMod.MODID = "programmablehatches"}`，与 jar 内 `mcmod.info` 一致。
     *
     * <p><b>2026-09-26 实测教训</b>：本类最初写的是 `proghatches` —— 那是 PH 的**包名前缀**
     * （`reobf.proghatches.*`、coremod 类名也是这个），**不是 modid**。后果是守卫恒为 false：
     * 物品不注册、进不了 NEI/创造页，而且**连一条日志都没有**（现象就是「找不到这个物品」）。
     * 教训：可选依赖判定只能取对方 {@code @Mod}/`mcmod.info` 里的 modid，不能从包名或 jar 文件名猜；
     * 且守卫的「跳过」分支也必须留日志，否则这类失败完全静默。
     */
    private static final String PH_MODID = "programmablehatches";

    /**
     * PH 的关键类。真正决定「这个功能能不能跑」的是**类是否存在**而不是 modid，
     * 所以两道判据都用上：modid 走常规路径，类存在性兜住 modid 变更的情况。
     */
    private static final String PH_ANCHOR_CLASS = "reobf.proghatches.gt.metatileentity.PatternDualInputHatch";

    private PhIntegration() {}

    public static void register() {
        if (!ae2qol$programmableHatchesPresent()) {
            // 未安装 PH：本功能整体不存在（物品既不会注册也不会出现在创造页）。
            // 这一行日志是刻意保留的——没有它，「守卫判错」与「注册失败」都无法与「根本没调到」区分开。
            MyMod.LOG.info(
                "[AE2QoL] 未检测到 ProgrammableHatches（modid=" + PH_MODID + "），跳过「编程样板输入总成 MK.III」");
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

            // ===== 3.24.0：MK.IV（360 样板槽）=====
            // 与 MK.III 并存、互不影响：同一套 PH 基类/mixin/界面部件，只把样板槽从 144 提到 360。
            // 配方以 **MK.III 为基底**（cCc/CXC/cCc）⇒ 玩家的升级路径就是「拿 144 那台 + 大师/高级电路」。
            MTEPatternCraftingBufferMKIV mte4 = new MTEPatternCraftingBufferMKIV(
                MTEPatternCraftingBufferMKIV.MTE_ID,
                MTEPatternCraftingBufferMKIV.MTE_NAME,
                "Programmable Crafting Input Buffer MK.IV",
                MTEPatternCraftingBufferMKIV.TIER,
                true,
                MTEPatternCraftingBufferMKIV.BUFFER_NUM,
                true,
                MTEPatternCraftingBufferMKIV.INPUT_PAGE,
                MTEPatternCraftingBufferMKIV.defaultDescription());

            GameRegistry.addShapedRecipe(
                mte4.getStackForm(1L),
                "cCc",
                "CXC",
                "cCc",
                'X',
                mte.getStackForm(1L),
                'C',
                ItemList.Circuit_Master.get(1),
                'c',
                ItemList.Circuit_Advanced.get(1));

            mkivStack = mte4.getStackForm(1L);

            // 与 MK.III 同理：AE2 的接口终端注册表按**精确类名**查表，漏掉这一步 AE2 接口终端与
            // 本模组样板终端都会看不见 MK.IV。
            Api.INSTANCE.registries()
                .interfaceTerminal()
                .register(MTEPatternCraftingBufferMKIV.Inst.class);

            MyMod.LOG.info(
                "[AE2QoL] PH 编程样板输入总成 MK.IV 已注册：id=" + MTEPatternCraftingBufferMKIV.MTE_ID
                    + "，样板槽="
                    + MTEPatternCraftingBufferMKIV.PATTERN_SLOTS
                    + "（"
                    + MTEPatternCraftingBufferMKIV.TOTAL_ROWS
                    + " 行 × "
                    + MTEPatternCraftingBufferMKIV.GRID_COLS
                    + " 列）");

            // ===== 3.24.0：样板剪贴板（144 → 360 搬家 / 备份）=====
            // 物品在这里注册而不是 CommonProxy：它引用了 PH 家族类型，必须与上面两台机器同一套守卫纪律。
            patternClipboard = new com.wztwzt.ae2_qof.item.ItemPatternClipboard();
            patternClipboard.register();
            MyMod.LOG.info("[AE2QoL] 样板剪贴板已注册：pattern_clipboard（复制 / 粘贴 / 剪切 MK.III·MK.IV 的全部样板）");
        } catch (Throwable t) {
            // 注册失败不影响本模组其它功能；吞掉会让「物品不出现」变成无痕迹故障，所以必须记日志
            MyMod.LOG.error("[AE2QoL] 编程样板输入总成 MK.III 注册失败（已跳过该物品）", t);
        }
    }

    /** PH 是否可用：modid 命中 **且** 关键类能被加载（后者才是真正的能力判据）。 */
    private static boolean ae2qol$programmableHatchesPresent() {
        try {
            if (!Loader.isModLoaded(PH_MODID)) {
                return false;
            }
            // initialize=false：只确认类可解析，不触发它的静态初始化
            Class.forName(PH_ANCHOR_CLASS, false, PhIntegration.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
