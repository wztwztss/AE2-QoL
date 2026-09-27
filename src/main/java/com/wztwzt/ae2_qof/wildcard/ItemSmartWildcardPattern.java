package com.wztwzt.ae2_qof.wildcard;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.AE2QoLCreativeTab;
import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;

import appeng.items.misc.ItemEncodedPattern;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 智能通配样板物品（3.22.0）。
 *
 * <h2>为什么继承 {@code ItemEncodedPattern}</h2>
 * 参考实现已证明：AE2 与 GT 的宿主**只做 {@code instanceof ICraftingPatternItem} 类型判定**，
 * 没有物品白名单 ⇒ 继承 AE2 原版样板物品即自动被**所有样板总成/接口**接受，
 * 不需要改 AE2、也不需要注册新的样板类型。也正因为继承它，本物品在未配置规则时
 * 仍是一张**完全合法**的普通编码样板（原生 {@code in}/{@code out} 就是模板），不会出现
 * 参考实现“未配置时删掉 in/out、原生逻辑解析失败”的副作用。
 *
 * <h2>与参考实现的差异（本模组取舍）</h2>
 * <ol>
 * <li>规则数据放在独立子树 {@link SmartWildcardState#KEY_ROOT}，与原生 in/out 平级、互不干扰；</li>
 * <li>不使用 mixin 注入 AE2 的样板物品类，改为**覆写本类自己的方法**（少一个注入点、少一处静默失效风险）；</li>
 * <li>所有“没配置/解析失败”路径都**写日志**并给出可见提示，不做静默回退。</li>
 * </ol>
 */
public class ItemSmartWildcardPattern extends ItemEncodedPattern
    implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PlayerInventoryGuiData> {

    /** 本模组配置界面的 Gui ID（由 {@code MergedGuiHandler} 分支处理；x 参数传玩家背包槽位号）。 */
    public static final int GUI_ID = 130;

    public ItemSmartWildcardPattern() {
        setUnlocalizedName("ae2_qof.smart_wildcard_pattern");
        setMaxStackSize(1);
        setCreativeTab(AE2QoLCreativeTab.INSTANCE);
        // 3.23.0：图标**运行时引用** AE2 自己的编码样板贴图，而不是把它复制进本仓库。
        // 原因（版权）：AE2 是 LGPL-3.0，复制其素材会让被复制的文件受 LGPL 约束；
        // 仅按名字引用则本仓库不再分发该素材，而 AE2 本就是本模组的硬依赖（required-after）。
        setTextureName("appliedenergistics2:ItemEncodedPattern");
    }

    /**
     * 绿色染色（与参考模组同思路，它是紫色、我们用绿色）：贴图本体保持原版编码样板的形状与明暗，
     * 由 1.7.10 的 ItemRenderer 用这里返回的颜色相乘着色。
     */
    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return 0x5CE65C;
    }

    /**
     * MUI2 面板构建（3.23.0 界面重做）：服务端与客户端都会走这里 ⇒ 只能用两侧都存在的 MUI2 类。
     * 面板内容见 {@link WildcardEditorPanel}。
     */
    @Override
    public com.cleanroommc.modularui.screen.ModularPanel buildUI(
        com.cleanroommc.modularui.factory.PlayerInventoryGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings settings) {
        return WildcardEditorPanel.build(data, syncManager);
    }

    /**
     * 按 MUI2 的要求覆写 {@code createScreen} 并传入本模组 modid。
     *
     * <p>不覆写时 MUI2 会打警告「should be overridden to pass your own mod id … or else it will crash」——
     * 它用 owner 来区分同名面板，未来版本不传会直接崩，所以这里现在就给对。
     */
    @Override
    public com.cleanroommc.modularui.screen.ModularScreen createScreen(
        com.cleanroommc.modularui.factory.PlayerInventoryGuiData data,
        com.cleanroommc.modularui.screen.ModularPanel panel) {
        return new com.cleanroommc.modularui.screen.ModularScreen(MyMod.MODID, panel);
    }

    public ItemSmartWildcardPattern register() {
        GameRegistry.registerItem(this, "smart_wildcard_pattern", MyMod.MODID);
        // 3.22.0：合成配方 = 1 张 AE2 空白样板 → 1 张智能通配样板（QoL 便利品，与参考模组同思路）。
        // 注册失败必须留日志（本项目原则），并保证物品仍可从 AE2 QoL 创造标签取出。
        try {
            ItemStack blank = appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .maybeStack(1)
                .get();
            if (blank != null) {
                GameRegistry.addShapelessRecipe(new ItemStack(this), blank);
                MyMod.LOG.info("[AE2QoL] 智能通配样板配方已注册：AE2 空白样板 → 智能通配样板");
            } else {
                MyMod.LOG.warn("[AE2QoL] 智能通配样板配方注册失败：取不到 AE2 空白样板");
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 智能通配样板配方注册异常（物品仍可从创造标签取出）", t);
        }
        return this;
    }

    /**
     * 物品提示：把“这张样板覆盖什么、排除了多少、带什么电路”直接写清楚 ——
     * 用户明确要求“不要只能看到名字”，所以这里必须给出可判断的信息。
     */
    @Override
    public void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        try {
            SmartWildcardState state = SmartWildcardState.of(stack);
            if (state == null) {
                lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.wildcard.tip.unconfigured"));
                return;
            }
            if (!state.isConfigured()) {
                lines.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("ae2_qof.wildcard.tip.no_rules"));
            } else {
                lines.add(
                    EnumChatFormatting.AQUA
                        + StatCollector.translateToLocalFormatted("ae2_qof.wildcard.tip.rules", state.rules.size()));
                for (SmartWildcardState.Rule rule : state.rulesView()) {
                    if (rule == null || rule.matcher == null || rule.matcher.isEmpty()) continue;
                    lines.add(
                        EnumChatFormatting.WHITE + "  #"
                            + rule.slot
                            + " "
                            + (rule.oreDictMode ? "ore:" : "name:")
                            + rule.matcher
                            + (rule.amount > 1 ? " x" + rule.amount : ""));
                }
            }
            if (!state.blacklist.isEmpty()) {
                lines.add(
                    EnumChatFormatting.RED + StatCollector
                        .translateToLocalFormatted("ae2_qof.wildcard.tip.blacklist", state.blacklist.size()));
            }
            if (state.circuit >= 0) {
                lines.add(
                    EnumChatFormatting.GOLD
                        + StatCollector.translateToLocalFormatted("ae2_qof.wildcard.tip.circuit", state.circuit));
            }
            if (!state.nonConsumed.isEmpty()) {
                lines.add(
                    EnumChatFormatting.GOLD + StatCollector
                        .translateToLocalFormatted("ae2_qof.wildcard.tip.non_consumed", state.nonConsumed.size()));
            }
            lines.add(
                EnumChatFormatting.DARK_GRAY + StatCollector
                    .translateToLocalFormatted("ae2_qof.wildcard.tip.cap", Config.smartWildcardExpandCap));
        } catch (Throwable t) {
            // 提示渲染失败绝不静默：给可见提示 + 日志（本项目铁则）
            MyMod.LOG.warn("[AE2QoL] 智能通配样板 tooltip 渲染失败", t);
            lines.add(EnumChatFormatting.RED + "tooltip error: " + t);
        }
    }

    /**
     * M1 阶段的临时自证入口：右键在聊天栏打印当前配置摘要（服务端权威值）。
     * M2 会替换为可视化配置界面；在此之前它是“配好了没、规则对不对”的唯一可见途径。
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        ItemStack result = super.onItemRightClick(stack, world, player);
        // 3.23.0：改用 MUI2 的玩家背包 GUI 工厂打开（带容器屏由 MUI2 的 GuiContainerWrapper 处理，
        // 因此 NEI 的加号仍然认这个界面）；旧的 openGui(GUI_ID) 路径已被它取代。
        try {
            if (!world.isRemote && player != null) {
                com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory.INSTANCE.openFromMainHand(player);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 打开通配样板编辑界面失败", t);
        }
        // 3.23.1：移除 M1 时期的"右键聊天摘要"——用户反馈它每次打开界面都在聊天栏刷屏
        //（rules=… / expand: produced=…）。配置信息现在由 MUI2 编辑器与物品 tooltip 承载，诊断信息进日志。
        return result;
    }
}
