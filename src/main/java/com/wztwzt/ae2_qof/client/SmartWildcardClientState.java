package com.wztwzt.ae2_qof.client;

import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

/**
 * 客户端侧的通配样板配置状态桥（3.22.0 M2-A）。
 *
 * <p>为什么需要它：NEI 的加号发生在 {@code GuiOverlayButton} 里（我们是 mixin 截胡），
 * 而"当前正在配置的通配界面"是另一个对象 ⇒ 用这里做唯一的交接点：
 * <ol>
 * <li>界面打开时把自己登记为 {@link #activeGui}（关闭时清空）；</li>
 * <li>加号被按下 → 从当前 NEI 配方推导出 {@link SmartWildcardState} → 存进 {@link #derived}；</li>
 * <li>界面读 {@link #derived} 显示"覆盖预览/规则"，用户确认后由界面发 C2S 包写回服务端。</li>
 * </ol>
 *
 * <p>只存引用与纯数据，不引用任何渲染类之外的客户端 API（服务器侧不会加载本类）。
 */
public final class SmartWildcardClientState {

    private static Object activeGui;
    /**
     * 当前打开 GUI 的机器坐标：由三个机器 GUI 的 mixin 在构造/建界面时写入，
     * 供「Shift+中键点样板槽」手势定位机器（写回包需要坐标）。未打开机器界面时为 {@code Integer.MIN_VALUE}。
     */
    public static int machineX = Integer.MIN_VALUE;
    public static int machineY;
    public static int machineZ;
    public static int machineDim;
    private static SmartWildcardState derived;
    private static String derivedSummary = "";
    private static java.util.List<net.minecraft.item.ItemStack> derivedTemplateIn;
    private static java.util.List<net.minecraft.item.ItemStack> derivedTemplateOut;

    private SmartWildcardClientState() {}

    public static void setActiveGui(Object gui) {
        activeGui = gui;
        if (gui == null) clearDerived();
    }

    public static Object activeGui() {
        return activeGui;
    }

    /** NEI 加号推导完成：存入待确认的规则 + 可读摘要（摘要用于界面提示与日志对账）。 */
    public static void setDerived(SmartWildcardState state, String summary) {
        derived = state;
        derivedSummary = summary == null ? "" : summary;
    }

    public static SmartWildcardState derived() {
        return derived;
    }

    public static String derivedSummary() {
        return derivedSummary;
    }

    public static void clearDerived() {
        derived = null;
        derivedSummary = "";
        derivedTemplateIn = null;
        derivedTemplateOut = null;
    }

    /**
     * NEI 加号推导完成（**含配方模板**）：状态 + 模板一起交给界面；界面点保存时原样发给服务端，
     * 由服务端一次写入「规则 + 模板 in/out」⇒ 用户点一下加号就等于完成了编码。
     */
    public static void setDerived(SmartWildcardRecipeDeriver.Result result) {
        if (result == null) {
            clearDerived();
            return;
        }
        derived = result.state;
        derivedSummary = result.summary;
        derivedTemplateIn = result.templateIn;
        derivedTemplateOut = result.templateOut;
    }

    public static java.util.List<net.minecraft.item.ItemStack> derivedTemplateIn() {
        return derivedTemplateIn;
    }

    public static java.util.List<net.minecraft.item.ItemStack> derivedTemplateOut() {
        return derivedTemplateOut;
    }
}
