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
public class ItemSmartWildcardPattern extends ItemEncodedPattern {

    public ItemSmartWildcardPattern() {
        setUnlocalizedName("ae2_qof.smart_wildcard_pattern");
        setMaxStackSize(1);
        setCreativeTab(AE2QoLCreativeTab.INSTANCE);
    }

    public ItemSmartWildcardPattern register() {
        GameRegistry.registerItem(this, "smart_wildcard_pattern", MyMod.MODID);
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
        try {
            if (!world.isRemote && player != null) {
                SmartWildcardState state = SmartWildcardState.of(stack);
                if (state == null) {
                    player.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.GRAY + "[AE2QoL] "
                                + StatCollector.translateToLocal("ae2_qof.wildcard.tip.unconfigured")));
                } else {
                    // M1 自测入口：直接跑一次展开，把结果摘要打出来（M2 会换成可视化界面）。
                    SmartWildcardExpander.Result expansion = SmartWildcardExpander.expand(stack, world);
                    player.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.AQUA + "[AE2QoL] rules=" + state.rules.size()
                                + " blacklist="
                                + state.blacklist.size()
                                + " whitelist="
                                + state.whitelist.size()
                                + " circuit="
                                + state.circuit
                                + " nonConsumed="
                                + state.nonConsumed.size()
                                + " revision="
                                + state.revision
                                + " cap="
                                + Config.smartWildcardExpandCap));
                    player.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            (expansion.isEmpty() ? EnumChatFormatting.RED : EnumChatFormatting.GREEN)
                                + "[AE2QoL] expand: "
                                + expansion.describe()));
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 智能通配样板右键摘要失败", t);
        }
        return result;
    }
}
