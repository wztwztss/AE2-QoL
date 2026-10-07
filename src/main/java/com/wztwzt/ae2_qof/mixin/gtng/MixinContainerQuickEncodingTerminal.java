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
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < inputs.length; i++) {
                ItemStack raw = ae2qol$zeroSizedItem(inputs[i]);
                if (raw == null) continue;
                ItemStack circuit = PhToolkitGate.wrapAsProgrammingCircuit(raw);
                if (circuit == null) continue;
                IAEStack<?> ae = AEItemStack.create(circuit);
                if (ae == null) continue;
                // ★ 3.25.0-fix25 **关键修法**：PH 的 wrap 产出可能是"0 数量"的标记物品，
                // 若不强制成 1，就会被他们管道里那道"零尺寸过滤"**再丢一次**
                // —— 这正是"客户端注入成功、最终样板里却没有电路"的最可能根因。
                ae.setStackSize(1);
                inputs[i] = ae;
                if (detail.length() < 400) {
                    detail.append('[')
                        .append(i)
                        .append("] ")
                        .append(raw.getUnlocalizedName())
                        .append(" x")
                        .append(raw.stackSize)
                        .append(" → ")
                        .append(circuit.getUnlocalizedName())
                        .append(" x")
                        .append(circuit.stackSize)
                        .append("  ");
                }
                wrapped++;
            }
            if (wrapped == 0 && PhToolkitGate.addEmptyProgCircuit()) {
                for (int i = 0; i < inputs.length; i++) {
                    if (inputs[i] != null) continue;
                    ItemStack zero = PhToolkitGate.wrapAsProgrammingCircuit(null);
                    IAEStack<?> ae = zero == null ? null : AEItemStack.create(zero);
                    if (ae != null) {
                        ae.setStackSize(1); // 同上：归零电路也必须是 1 数量，否则同样会被零尺寸过滤丢掉
                        inputs[i] = ae;
                        detail.append("[空槽")
                            .append(i)
                            .append("] → 归零编程器电路 x1  ");
                        wrapped++;
                    }
                    break;
                }
            }
            if (wrapped > 0) {
                // 3.25.0-fix25：改成**每次都记明细**（原"只记一次"掩盖了后续注入是否真的发生）
                MyMod.LOG.info(
                    "[AE2QoL] 写样板时注入 PH 编程器电路：本张 {} 块（明细：{}）",
                    wrapped,
                    detail.length() == 0 ? "-" : detail.toString());
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 写样板时注入 PH 编程器电路失败（本次跳过，不影响样板生成）", t);
        }
    }

    /**
     * 3.25.0-fix25 探针：他们**服务端**应用载荷的前后各打一条"收到了什么"的摘要。
     * <p>用途：判定"我们注入的编程器电路到底有没有过服务器这一关"（用户实测：客户端注入成功、
     * 最终样板里却没有电路 ⇒ 必须看服务端收到的是什么）。
     */
    @Inject(method = "applyRecipeTransfer", at = @At("HEAD"), remap = false)
    private void ae2qol$probeApplyHead(RecipeTransferPayload payload, CallbackInfo ci) {
        ae2qol$logPayload("服务端收到", payload);
    }

    @Inject(method = "applyRecipeTransfer", at = @At("RETURN"), remap = false)
    private void ae2qol$probeApplyReturn(RecipeTransferPayload payload, CallbackInfo ci) {
        ae2qol$logPayload("服务端应用后", payload);
    }

    private static void ae2qol$logPayload(String where, RecipeTransferPayload payload) {
        try {
            IAEStack<?>[] inputs = ((MixinRecipeTransferPayload) (Object) payload).ae2qol$inputs();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputs.length; i++) {
                IAEStack<?> s = inputs[i];
                if (s == null) continue;
                String nm = s.getClass()
                    .getSimpleName();
                if (s instanceof AEItemStack ais && ais.getItemStack() != null) {
                    nm = ais.getItemStack()
                        .getUnlocalizedName();
                }
                sb.append('[')
                    .append(i)
                    .append("] ")
                    .append(nm)
                    .append(" x")
                    .append(s.getStackSize())
                    .append("  ");
            }
            MyMod.LOG.info("[AE2QoL] 转写探针（{}）：{}", where, sb.length() == 0 ? "(inputs 全空)" : sb.toString());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 转写探针失败", t);
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
