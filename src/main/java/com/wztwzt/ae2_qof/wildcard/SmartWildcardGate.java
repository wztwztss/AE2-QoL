package com.wztwzt.ae2_qof.wildcard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.CommonProxy;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge;

/**
 * 机器侧四个入口（GT 样板仓 / GTNL 超级总成 / PH 家族 / AE2 ME 接口）的**统一判据 + 懒同步**（3.34.0）。
 *
 * <h2>为什么必须独立成类</h2>
 * 3.32.0 曾把"懒同步"写在 {@link SmartWildcardExpander#expand} 的入口处，意图是救"玩家只在 Wild 界面里
 * 配过、我们的 NBT 子树还没写"的样板。但四处入口的判据都是 {@link SmartWildcardState#isSmartWildcard}，
 * 而 {@code expand()} **只有判据通过后才会被调用** ⇒ 那段兜底恰好对自己要救的场景**永远不可达**
 * （实测日志：{@code 展开前懒同步} 命中 0 次，{@code pullFromWild} 全场只有 2 次且都是"规则 0 条"）。
 * 结果就是：玩家在界面上配好的通配样板，被四处入口一律当成**普通样板**静默处理，机器不动、日志无痕。
 *
 * <h2>判据链（顺序即语义）</h2>
 * <ol>
 * <li><b>物品实例</b>是我们的通配样板（{@link #isOurs}）—— 不看 NBT，全新样板也算；</li>
 * <li>缺我们的 NBT 子树时**先懒同步**（{@link WildcardBridge#pullFromWild}，只读 Wild 键、不联网）；</li>
 * <li>同步后我们的子树存在**且至少配了一条规则**（{@link SmartWildcardState#isConfigured}）才按通配样板处理。</li>
 * </ol>
 * 任何一条不满足都必须**留痕**（本项目原则：不许静默降级）——否则"没配""同步失败""物品不是我们的"
 * 三种完全不同的情况在日志里无法区分，排查会被引向机器/AE2/渲染等错误方向。
 *
 * <h2>为什么"未配置"要按普通样板处理</h2>
 * 我们的样板物品继承 AE2 的编码样板，未配置时它本身就是一张**合法普通样板**（原生 {@code in}/{@code out}
 * 就是模板）。若把未配置的样板也拉进通配分支，展开结果为空 ⇒ 该槽位注册 0 条 ⇒ 原本可用的模板配方反而
 * 失效。这是 3.34.0 修复必须守住的不变量。
 */
public final class SmartWildcardGate {

    /** 已打过"未按通配样板处理"日志的 key，避免样板仓每轮重建索引时刷屏。 */
    private static final Set<String> LOGGED = Collections.synchronizedSet(new HashSet<String>());

    private SmartWildcardGate() {}

    /** 物品是不是本模组的通配样板（**只看物品实例，不看 NBT**）。 */
    public static boolean isOurs(ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.getItem() == CommonProxy.smartWildcardPattern;
    }

    /**
     * 该槽位/物品是否应按"**已配置的通配样板**"处理。
     *
     * @param stack 槽位里的样板物品
     * @param where 日志前缀（哪个入口），如 {@code "GT 样板仓"}
     * @return true = 走展开路径；false = 交回原版/普通样板路径（并已按需留痕）
     */
    public static boolean isConfiguredWildcard(ItemStack stack, String where) {
        if (!isOurs(stack)) return false;

        if (!SmartWildcardState.isSmartWildcard(stack)) {
            boolean pulled = false;
            try {
                pulled = WildcardBridge.pullFromWild(stack);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] {}：通配样板懒同步异常（按普通样板处理）", where, t);
                return false;
            }
            if (!pulled) {
                // 情况一：全新样板（界面上还没配过）—— 正常，但必须留一行，否则与"同步失败"无法区分
                logOnce(stack, where, "是我们的通配样板但没有任何配置（我们的 NBT 与 Wild 键都没有）⇒ 按普通样板处理（模板那一张仍可用）");
                return false;
            }
            MyMod.LOG.info("[AE2QoL] {}：通配样板懒同步成功 —— 已从 Wild 界面的键拉回我们的配置", where);
        }

        SmartWildcardState state = SmartWildcardState.of(stack);
        if (state == null || !state.isConfigured()) {
            // 情况二：有我们的子树但一条规则都没有（只设了电路/不消耗物品）—— 同样按普通样板处理
            logOnce(stack, where, "通配样板尚未配置任何规则（只有电路/不消耗物品等设置）⇒ 按普通样板处理（模板那一张仍可用）");
            return false;
        }
        return true;
    }

    /** 同一物品 + 同一入口只打一次（样板仓的 provideCrafting 在网格重建热路径上）。 */
    private static void logOnce(ItemStack stack, String where, String message) {
        String key = where + '|' + System.identityHashCode(stack);
        if (!LOGGED.add(key)) return;
        if (LOGGED.size() > 512) LOGGED.clear();
        MyMod.LOG.warn("[AE2QoL] {}：{}", where, message);
    }
}
