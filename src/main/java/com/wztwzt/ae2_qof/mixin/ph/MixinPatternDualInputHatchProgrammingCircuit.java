package com.wztwzt.ae2_qof.mixin.ph;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.ph.PhCircuitWrap;

import reobf.proghatches.gt.metatileentity.DualInputHatch;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 3.25.0-fix20：**样板"被放进 PH 仓"那一刻**，把输入里的 GT 编程电路改写为 PH:编程器电路。
 *
 * <h2>为什么挂这里（而不是改别的模组的终端）</h2>
 * 用户要的是"任何终端写进来的样板都能自动适配 PH 编程工具箱"（例如 GT-Not-Good 的 FC Ultra Terminal
 * 三合一终端，它不会自己做这个转换）。挂在**宿主侧**有两个好处：
 * <ol>
 * <li>**与终端无关**：不管样板是哪个模组/哪个终端放进来的，都在这里统一处理；</li>
 * <li>**不依赖对方内部结构**：不需要反射改他们的报文/物品类，对方升级也不会把我们的实现搞坏。</li>
 * </ol>
 *
 * <h2>3.33.0-fix5：注入目标从 PatternDualInputHatch 改为它的父类 DualInputHatch（修"从未生效"）</h2>
 * fix20/fix21 一直把 {@code @Mixin} 目标钉在 {@code PatternDualInputHatch} 上，但
 * {@code func_70299_a}（SRG 名的 {@code setInventorySlotContents}）**声明在父类
 * {@code DualInputHatch}**（实例 PH 0.2.0p24 javap 实证：子类与中间层 BufferedDualInputHatch
 * 都不声明它），而 Mixin 的 {@code @Inject(method=...)} 只匹配**目标类自己声明**的方法 ——
 * 两个注入器因此从未命中（实例日志零 handler 注册行），编程电路转写自交付日起静默失效。
 * <p>
 * 现在目标改为 {@code DualInputHatch}，方法真实存在 ⇒ 注入必然命中；回调里用
 * {@code instanceof PatternDualInputHatch} 收窄，父类的其它子类
 * （StockingDualInputHatchME / DualInputHatchSlave 系列等）一律原样放行。
 *
 * <h2>为什么注入在 RETURN 而不是 HEAD + cancel</h2>
 * 让 PH 的原实现（以及它可能做的记账）先照常跑完，我们再**把落在数组里的那个引用换成改写副本**，
 * 不打断它的任何行为 —— 比 cancel 更保守。
 *
 * <h2>为什么只处理新插入的</h2>
 * 用户口径："只要新插入的，不动旧样板"。因此**不做读档期扫描**：已经躺在仓里的旧样板保持原样，
 * 需要时玩家重新放一次即可（或自行用 PH 编程工具箱转写）。
 *
 * <p>
 * PH 为运行时可选依赖（compileOnly + {@code required:false}）：未装 PH 时本混入不会被应用。
 */
@Mixin(value = DualInputHatch.class, remap = false)
public abstract class MixinPatternDualInputHatchProgrammingCircuit {

    /**
     * 3.25.0-fix21：**同一个钩子挂两个名字**。
     *
     * <p>
     * fix20 只写了 MCP 名 {@code setInventorySlotContents} + {@code remap = false}，而该方法在 PH 的
     * 运行期 jar 里是 **SRG 名**（{@code func_70299_a}，因为它是 MC {@code IInventory} 接口方法）
     * ⇒ 当年即使目标类正确也只会命中一个名字。项目坑位 #1。
     *
     * <p>
     * 两个名字各挂一个注入器、都设 {@code require = 0}（对不上不报错、不崩）：
     * 哪份 PH/整合包用哪个名字都能生效；**两个都命中也不会双重包装** —— 第二次进来时样板里已经没有
     * GT 电路（只剩编程器电路），{@link PhCircuitWrap#wrapPlainPattern} 会原样返回。
     */
    @Inject(method = "func_70299_a", at = @At("RETURN"), remap = false, require = 0)
    private void ae2qol$wrapOnInsertSrg(int index, ItemStack stack, CallbackInfo ci) {
        ae2qol$wrapOnInsert(index, stack);
    }

    /** MCP 名版本（若某天 PH 直接以 MCP 名编译/被打包，这一条会生效）。 */
    @Inject(method = "setInventorySlotContents", at = @At("RETURN"), remap = false, require = 0)
    private void ae2qol$wrapOnInsertMcp(int index, ItemStack stack, CallbackInfo ci) {
        ae2qol$wrapOnInsert(index, stack);
    }

    private void ae2qol$wrapOnInsert(int index, ItemStack stack) {
        try {
            // 3.33.0-fix5：注入目标已是父类 DualInputHatch —— 只有真正的样板仓
            // （PatternDualInputHatch 及其子类）才需要电路转写；父类的其它子类
            // （StockingDualInputHatchME、DualInputHatchSlave 系列、各类输入总线等）原样放行。
            if (!((Object) this instanceof PatternDualInputHatch)) return;
            ItemStack wrapped = PhCircuitWrap.wrapPlainPattern(stack);
            if (wrapped == stack) return; // 无需改写（非样板/通配样板/没有 GT 电路）
            MixinPatternDualInputHatchAccess access = (MixinPatternDualInputHatchAccess) (Object) this;
            ItemStack[] slots = access.getAe2qolPattern();
            if (slots == null) return;
            if (index < 0 || index >= slots.length) return;
            slots[index] = wrapped;
        } catch (Throwable t) {
            // 不静默：改写失败不影响样板正常放入
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 放入 PH 仓时的编程器电路改写失败（该张保持原样）", t);
        }
    }
}
