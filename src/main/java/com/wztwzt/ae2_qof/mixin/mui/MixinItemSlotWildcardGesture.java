package com.wztwzt.ae2_qof.mixin.mui;

import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
 */
@Mixin(value = ItemSlot.class, remap = false)
public abstract class MixinItemSlotWildcardGesture {

    @Inject(method = "onMousePressed", at = @At("HEAD"), remap = false)
    private void ae2qol$openCircuitPicker(int button, CallbackInfo ci) {
        try {
            if (button != 2) return;
            if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) return;
            if (SmartWildcardClientState.machineX == Integer.MIN_VALUE) return; // 不在机器界面里

            ItemSlot self = (ItemSlot) (Object) this;
            ModularSlot slot = self.getSlot();
            if (slot == null) return;
            ItemStack stack = slot.getStack();
            if (stack == null || !(stack.getItem() instanceof ItemSmartWildcardPattern)) return;

            net.minecraft.client.Minecraft.getMinecraft()
                .displayGuiScreen(
                    new GuiSlotCircuitPicker(
                        SmartWildcardClientState.machineX,
                        SmartWildcardClientState.machineY,
                        SmartWildcardClientState.machineZ,
                        slot.getSlotIndex(),
                        String.valueOf(
                            stack.getItem()
                                .getItemStackDisplayName(stack))));
            MyMod.LOG.info(
                "[AE2QoL] 样板槽手势：打开电路选择屏 slot={} pattern={}",
                slot.getSlotIndex(),
                stack.getItem()
                    .getItemStackDisplayName(stack));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板槽手势处理失败", t);
        }
    }
}
