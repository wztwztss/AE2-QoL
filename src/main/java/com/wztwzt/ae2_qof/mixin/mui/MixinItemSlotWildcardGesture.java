package com.wztwzt.ae2_qof.mixin.mui;

import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.client.SmartWildcardClientState;
import com.wztwzt.ae2_qof.client.gui.GuiSlotCircuitPicker;
import com.wztwzt.ae2_qof.wildcard.ItemSmartWildcardPattern;

/**
 * 「Shift+中键点样板槽 → 弹出电路选择屏」的手势（3.22.0 M3）。
 *
 * <h2>依据（都已 javap 核过）</h2>
 * <ul>
 * <li>GT 样板仓界面的样板槽控件是 Cleanroom MUI2 的 {@code ItemSlot}
 * （证据：{@code MTEHatchCraftingInputMEGui.lambda$createSlots$0(...)} 返回 {@code ItemSlot}）；</li>
 * <li>{@code ItemSlot.onMousePressed(int)} 可注入（HEAD + cancellable），
 * {@code getSlot()} 给出它包装的 {@code ModularSlot}，从那里能读到物品与槽位下标；
 * MUI2 的中键 = 2（0 左 / 1 右 / 2 中），与 {@code IGuiScreen} 的鼠标按钮口径一致。</li>
 * </ul>
 *
 * <h2>行为</h2>
 * 命中时打开 {@link GuiSlotCircuitPicker}（1~24 / 清除（继承） / 手持物品记为不消耗）。
 * **刻意不 setReturnValue / 不取消**：MUI2 对槽位的中键本来没有语义，让它继续走原逻辑最稳
 * （避免依赖 {@code Interactable.Result} 的枚举常量名，那是猜不得的）。
 *
 * <p>目标机器坐标来自三个机器 GUI mixin 在打开时记录到 {@link SmartWildcardClientState} 的值；
 * 记录为空（不是在机器界面里）时直接不介入。所有异常都记日志（本项目原则）。
 *
 * <p><b>3.34.0 修正（原实现静默失效）</b>：{@code ItemSlot.onMousePressed(int)} 的返回类型是
 * {@code Interactable.Result}（javap 实证：{@code public Interactable$Result onMousePressed(int)}），
 * 因此回调**必须**是 {@code CallbackInfoReturnable}；原先写成 {@code CallbackInfo} 会让 Mixin 抛
 * {@code InvalidInjectionException: CallbackInfoReturnable is required}，被 UniMixins 的 MixinErrorHandler
 * 吞成一条 WARN ⇒ 整个手势从未生效。这里仍不调用 {@code setReturnValue}：中键对槽位本无原版语义。
 */
@Mixin(value = ItemSlot.class, remap = false)
public abstract class MixinItemSlotWildcardGesture {

    @Inject(method = "onMousePressed", at = @At("HEAD"), remap = false)
    private void ae2qol$openCircuitPicker(
        int button,
        CallbackInfoReturnable<com.cleanroommc.modularui.api.widget.Interactable.Result> cir) {
        try {
            if (button != 2) return;
            // 4.0.0：按用户口径"**按鼠标中键打开**"——去掉原先的 Shift 要求（中键即打开本格设置）。
            // 旧行为（Shift+中键写样板自带电路）已被"本格电路槽"取代：样板自带电路会在插入时自动填入本格。
            if (SmartWildcardClientState.machineX == Integer.MIN_VALUE) return; // 不在机器界面里

            ItemSlot self = (ItemSlot) (Object) this;
            ModularSlot slot = self.getSlot();
            if (slot == null) return;
            ItemStack stack = slot.getStack();
            if (stack == null || !(stack.getItem() instanceof ItemSmartWildcardPattern)) return;

            net.minecraft.client.Minecraft.getMinecraft()
                .displayGuiScreen(
                    new com.wztwzt.ae2_qof.client.gui.GuiSlotSettings(
                        SmartWildcardClientState.machineX,
                        SmartWildcardClientState.machineY,
                        SmartWildcardClientState.machineZ,
                        slot.getSlotIndex(),
                        String.valueOf(
                            stack.getItem()
                                .getItemStackDisplayName(stack))));
            MyMod.LOG.info(
                "[AE2QoL] 样板槽手势：打开「格设置」 slot={} pattern={}",
                slot.getSlotIndex(),
                stack.getItem()
                    .getItemStackDisplayName(stack));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板槽手势处理失败", t);
        }
    }
}
