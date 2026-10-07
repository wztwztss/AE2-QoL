package com.wztwzt.ae2_qof.mixin.gtng;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.ph.PhToolkitGate;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;

import com.xyp.gtnotgood.ae2thing.quickterminal.RecipeTransferPayload;

/**
 * 3.25.0-fix24：**在"写样板那一刻"给 GT-Not-Good 的快速编码终端补上 PH 编程器电路**
 * （玩家**身上带着** PH 编程样板工具箱即生效；**不需要**打开他们界面上的"保留不消耗物品"开关）。
 *
 * <h2>为什么挂这里（而不是他们的 NEI 覆盖处理器）</h2>
 * fix22/23 挂的是 {@code ae2thing.nei.QuickTerminalRecipeTransferHandler.collectStacks}，但日志实证：
 * 该类在玩家点 NEI"+"的路径上**从未被加载**（连 {@code Mixing} 行都没有）⇒ 那个 GUI 的"+"被别的
 * {@code IOverlayHandler}（日志显示第三方 Wtct 在接管）接走了。于是改挂**所有客户端来源的汇聚点**：
 * <pre>
 * ContainerQuickEncodingTerminal.requestRecipeTransfer(RecipeTransferPayload)   // 客户端：设置 sync 值并 send 动作
 *   → （服务端）private void applyRecipeTransfer(RecipeTransferPayload)
 * </pre>
 * {@code requestRecipeTransfer} 的方法体在操作 {@code *SyncHandler.setLocalValue} ⇒ **只有客户端才有意义**，
 * 因此这里是客户端唯一汇聚点；挂它就能覆盖 NEI"+"、他们界面上的按钮、以及任何未来来源。
 *
 * <h2>判据与内容</h2>
 * <ol>
 * <li>判据用 {@link PhToolkitGate#holding()}（用户口径：**带着就行**，见该类的背包扫描兜底）；</li>
 * <li>把载荷 inputs 里 **{@code stackSize == 0} 的 GT 电路/铸模**换成 {@code ItemProgrammingCircuit.wrap(...)}；</li>
 * <li>一块都没找到且工具箱处于兜底模式（{@code mode == 2}）⇒ 往第一个空槽补一块**归零电路**；</li>
 * <li>首次注入打一条 INFO；异常 ⇒ WARN 且原样放行（不静默）。</li>
 * </ol>
 *
 * <p>GT-Not-Good 为运行时软依赖（compileOnly + {@code required:false}）；本混入只登记在 mixin 配置的
 * {@code client} 段，专用服务端不会加载（这条路径依赖客户端玩家实例）。
 */
@Mixin(targets = "com.xyp.gtnotgood.ae2thing.quickterminal.ContainerQuickEncodingTerminal", remap = false)
public abstract class MixinContainerQuickEncodingTerminal {

    /** 只记一次，避免刷屏。 */
    private static boolean ae2qol$loggedOnce;

    @Inject(method = "requestRecipeTransfer", at = @At("HEAD"), remap = false)
    private void ae2qol$injectProgrammingCircuits(RecipeTransferPayload payload, CallbackInfo ci) {
        try {
            if (payload == null) return;
            if (!PhToolkitGate.holding()) return; // 身上没带工具箱 ⇒ 与 PH 自家终端一致：什么都不做

            // 注意：混入接口的强转必须先经过 (Object)，否则 javac 会拒绝（"无法转换类型"）
            IAEStack<?>[] inputs = ((MixinRecipeTransferPayload) (Object) payload).ae2qol$inputs();
            if (inputs == null || inputs.length == 0) return;

            int wrapped = 0;
            for (int i = 0; i < inputs.length; i++) {
                ItemStack raw = ae2qol$zeroSizedItem(inputs[i]);
                if (raw == null) continue;
                ItemStack circuit = PhToolkitGate.wrapAsProgrammingCircuit(raw);
                if (circuit == null) continue;
                IAEStack<?> ae = AEItemStack.create(circuit);
                if (ae == null) continue;
                inputs[i] = ae;
                wrapped++;
            }
            if (wrapped == 0 && PhToolkitGate.addEmptyProgCircuit()) {
                for (int i = 0; i < inputs.length; i++) {
                    if (inputs[i] != null) continue;
                    ItemStack zero = PhToolkitGate.wrapAsProgrammingCircuit(null);
                    IAEStack<?> ae = zero == null ? null : AEItemStack.create(zero);
                    if (ae != null) {
                        inputs[i] = ae;
                        wrapped++;
                    }
                    break;
                }
            }
            if (wrapped > 0 && !ae2qol$loggedOnce) {
                ae2qol$loggedOnce = true;
                MyMod.LOG.info(
                    "[AE2QoL] 已按 PH 编程样板工具箱在写样板时注入编程器电路（本张 {} 块）——"
                        + "GT-Not-Good 终端无需打开其「保留不消耗物品」开关；此后同类注入不再重复记录",
                    wrapped);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 写样板时注入 PH 编程器电路失败（本次跳过，不影响样板生成）", t);
        }
    }

    /** 该 AE 栈是否是"零尺寸的 GT 电路/铸模"（NEI 里这类不消耗物以 stackSize==0 出现）⇒ 返回其物品形态。 */
    private static ItemStack ae2qol$zeroSizedItem(IAEStack<?> stack) {
        try {
            if (!(stack instanceof AEItemStack aeItem)) return null;
            if (aeItem.getStackSize() != 0L) return null;
            return aeItem.getItemStack();
        } catch (Throwable t) {
            return null;
        }
    }
}
