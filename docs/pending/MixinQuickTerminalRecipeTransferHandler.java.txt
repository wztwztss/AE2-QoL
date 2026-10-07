package com.wztwzt.ae2_qof.mixin.gtng;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.ph.PhCircuitWrap;
import com.wztwzt.ae2_qof.ph.PhToolkitGate;

import appeng.api.storage.data.IAEStack;

import codechicken.nei.recipe.IRecipeHandler;

/**
 * 3.25.0-fix22：让 **GT-Not-Good 的 FC Ultra Terminal（三合一/快速编码终端）** 在 NEI 转写时
 * 具备与我们二合一终端相同的 **PH 编程工具箱**能力 —— 玩家激活工具箱时，**在写样板那一刻**就把
 * 编程器电路注入进去（**不需要**打开对方界面上的"保留不消耗物品"开关）。
 *
 * <h2>为什么挂他们的 {@code collectStacks}</h2>
 * 他们的转写管道（{@code QuickTerminalRecipeTransferHandler.overlayRecipe}）：
 * <pre>
 * L73  transferInputs = (!crafting &amp;&amp; terminal.shouldKeepNonConsumables()) ? retainSupportedNonConsumables(namedInputs) : namedInputs;
 * L83  NEIUtils.clearNull(...)
 * L95  IAEStack[] inputs  = collectStacks(transferInputs);      // ← 输入（零尺寸电路/催化剂就在这里）
 * L103 IAEStack[] outputs = collectStacks(transferOutputs);     // ← 输出
 * L109 terminal.transferRecipe(new RecipeTransferPayload(... inputs, outputs ...), interfaceSearch);
 * L180 private static IAEStack&lt;?&gt;[] collectStacks(List&lt;OrderStack&lt;?&gt;&gt; ordered) { … result[slot] = toAEStack(order.getStack()); … }
 * </pre>
 * 零尺寸的电路幻影在开关关闭时会被后面丢掉 ⇒ 选在 {@code collectStacks} 上：
 * <ul>
 * <li>**返回纯 AE2 类型** {@code IAEStack<?>[]}，入参是 {@code List} ⇒ 我们**不需要引用他们的任何类**（软依赖、不怕对方改内部结构）；</li>
 * <li>用"进入 {@code overlayRecipe} 后第几次调用"区分**输入/输出**（第一次=输入），
 * 比按 INVOKE 指令匹配描述符稳（对方重排代码也不会错）。</li>
 * </ul>
 *
 * <h2>判据与内容（与 PH 自家终端 / 我们 3.6.0 的一致）</h2>
 * <ol>
 * <li>只在 {@link PhToolkitGate#holding()}（工具箱**被激活**，PH 的 10 tick 语义）为真时注入；</li>
 * <li>把列表里 {@code stackSize == 0} 的条目（GT 电路、铸模/模头等不消耗物）换成
 * {@code ItemProgrammingCircuit.wrap(目标)} 的 AE 栈，写回**它原来的槽位**；</li>
 * <li>一块都没找到且 {@link PhToolkitGate#addEmptyProgCircuit()}（兜底模式）为真 ⇒ 往第一个空槽补一块归零电路。</li>
 * </ol>
 *
 * <p>GT-Not-Good 为运行时可选依赖：本混入的 target 是**字符串**且登记在 mixin 配置的 {@code client} 段，
 * 未装该模组 / 专用服务端上都不会被应用。
 */
@Mixin(targets = "com.xyp.gtnotgood.ae2thing.nei.QuickTerminalRecipeTransferHandler", remap = false)
public abstract class MixinQuickTerminalRecipeTransferHandler {

    /** 本次 {@code overlayRecipe} 里 {@code collectStacks} 被调用的次数（0=输入，1=输出）。 */
    @Unique
    private static int ae2qol$collectStacksCall;

    /** 最近一次 {@code collectStacks} 的入参（HEAD 存下，RETURN 用）。 */
    @Unique
    private static List<?> ae2qol$lastOrdered;

    /** 本次调用是否"输入"（第一次调用）。 */
    @Unique
    private static boolean ae2qol$isInputCall;

    /** 注入统计（只报一次，避免刷屏）。 */
    @Unique
    private static boolean ae2qol$loggedOnce;

    @Inject(method = "overlayRecipe", at = @At("HEAD"), remap = false)
    private void ae2qol$resetCallCounter(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, boolean shift,
        CallbackInfo ci) {
        ae2qol$collectStacksCall = 0;
    }

    @Inject(method = "collectStacks", at = @At("HEAD"), remap = false)
    private void ae2qol$captureOrdered(List<?> ordered, CallbackInfoReturnable<IAEStack<?>[]> cir) {
        ae2qol$lastOrdered = ordered;
        ae2qol$isInputCall = (ae2qol$collectStacksCall++ == 0);
    }

    @Inject(method = "collectStacks", at = @At("RETURN"), remap = false)
    private void ae2qol$injectProgrammingCircuits(List<?> ordered, CallbackInfoReturnable<IAEStack<?>[]> cir) {
        try {
            if (!ae2qol$isInputCall) return; // 只在"输入"那一次动手
            if (!PhToolkitGate.holding()) return; // 工具箱没被激活 ⇒ 与 PH 自家终端行为一致：什么都不做
            IAEStack<?>[] array = cir.getReturnValue();
            if (array == null || array.length == 0) return;

            int wrapped = 0;
            for (Object order : ordered) {
                if (order == null) continue;
                ItemStack raw = ae2qol$stackOf(order);
                if (raw == null || raw.stackSize != 0) continue; // 只处理"零尺寸"（NEI 里的电路/不消耗物幻影）
                int slot = ae2qol$indexOf(order);
                if (slot < 0 || slot >= array.length) slot = ae2qol$firstFreeSlot(array);
                if (slot < 0) break;
                ItemStack circuit = PhToolkitGate.wrapAsProgrammingCircuit(raw);
                if (circuit == null) continue;
                IAEStack<?> ae = appeng.util.item.AEItemStack.create(circuit);
                if (ae == null) continue;
                array[slot] = ae;
                wrapped++;
            }
            if (wrapped == 0 && PhToolkitGate.addEmptyProgCircuit()) {
                int slot = ae2qol$firstFreeSlot(array);
                if (slot >= 0) {
                    ItemStack zero = PhToolkitGate.wrapAsProgrammingCircuit(null); // 归零电路（PH 兜底用法）
                    IAEStack<?> ae = zero == null ? null : appeng.util.item.AEItemStack.create(zero);
                    if (ae != null) {
                        array[slot] = ae;
                        wrapped++;
                    }
                }
            }
            if (wrapped > 0 && !ae2qol$loggedOnce) {
                ae2qol$loggedOnce = true;
                MyMod.LOG.info(
                    "[AE2QoL] 已按 PH 编程样板工具箱在转写时注入编程器电路（本张 {} 块）——"
                        + "GT-Not-Good 终端无需打开其「保留不消耗物品」开关；此后同类注入不再重复记录",
                    wrapped);
            }
        } catch (Throwable t) {
            // 不静默：注入失败不影响他们的转写（原样放行）
            MyMod.LOG.warn("[AE2QoL] 转写时注入 PH 编程器电路失败（本次跳过，不影响样板生成）", t);
        }
    }

    /** 反射取 {@code OrderStack.getStack()}（他们自己的类，软依赖 ⇒ 不引用类型）。 */
    @Unique
    private static ItemStack ae2qol$stackOf(Object order) {
        try {
            Object stack = order.getClass()
                .getMethod("getStack")
                .invoke(order);
            return stack instanceof ItemStack item ? item : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 反射取 {@code OrderStack.getIndex()}。 */
    @Unique
    private static int ae2qol$indexOf(Object order) {
        try {
            Object idx = order.getClass()
                .getMethod("getIndex")
                .invoke(order);
            return idx instanceof Integer i ? i : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Unique
    private static int ae2qol$firstFreeSlot(IAEStack<?>[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null) return i;
        }
        return -1;
    }
}
