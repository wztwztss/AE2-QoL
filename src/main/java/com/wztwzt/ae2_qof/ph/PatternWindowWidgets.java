package com.wztwzt.ae2_qof.ph;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * 「编程样板输入总成 MK.III」样板窗用的三个小部件，来源是 PH
 * {@code reobf.proghatches.gt.metatileentity.PatternDualInputHatch} 里同名的**包私有**内部类
 * （{@code PanelDragForwarder} / {@code DragTab} / {@code NonInteractiveText}）与其两个 private 按钮工厂。
 *
 * <p>为什么要复制而不是复用：它们在 PH 里是包私有/private，跨包既不能继承也不能调用；
 * 而本模组刻意**不**把类塞进 PH 的包里（避免跨 jar 拆包）。它们只用 MUI2 的公开 API，
 * 复制过来是安全的；PH 升级时这里需要跟着复核一次（见 docs/mixin_notes.md）。
 *
 * <p>已按**编译基线**（libs 里的 MUI2 2.3.88）逐项核对过所用 API 都存在：
 * {@code ScrollWidget}/{@code VerticalScrollData}、{@code IPositioned.leftRelAnchor/topRelAnchor}
 * （接口默认方法）、{@code Area extends java.awt.Rectangle}（所以 {@code width}/{@code height} 是公开字段）。
 */
public final class PatternWindowWidgets {

    private PatternWindowWidgets() {}

    /**
     * 构造一个 16×16 的按钮，点击动作**在服务端执行**：MUI2 的 InteractionSyncHandler 会把点击同步到
     * 服务端，所以对 {@code multiplier[]} 这类服务端持久化状态的修改才会生效（照抄 PH 的做法）。
     */
    public static ButtonWidget<?> makeBatchButton(PanelSyncManager syncManager, String key, Runnable action) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(data -> action.run());
        syncManager.syncValue(key, handler);
        return (ButtonWidget<?>) new ButtonWidget<>().syncHandler(handler)
            .size(16, 16)
            .background(GTGuiTextures.BUTTON_STANDARD);
    }

    /**
     * 「退货」按钮：把内部缓冲里的物品/流体全部退回 AE。
     * <p>PH 原版在这里先置 {@code dirty = true} 再调 {@code refundAll()}；而 {@code refundAll()} 自己
     * 开头就做了 {@code markDirty(); dirty = true;}，所以本实现只调退款动作，不需要再碰包私有的
     * {@code dirty} 字段。
     */
    public static ButtonWidget<?> createRefundButton(PanelSyncManager syncManager, Runnable refundAction) {
        InteractionSyncHandler refund = new InteractionSyncHandler().setOnMousePressed(data -> refundAction.run());
        syncManager.syncValue("refund_all", refund);
        return (ButtonWidget<?>) new ButtonWidget<>().syncHandler(refund)
            .background(GTGuiTextures.BUTTON_STANDARD, GTGuiTextures.OVERLAY_BUTTON_EXPORT)
            .tooltip(t -> t.addLine(IKey.str("Return all internally stored items back to AE")))
            .size(16, 16);
    }

    // ===================== 拖拽支持（照抄 PH） =====================
    // 凸出到面板之外的拖动手柄不能用 MUI2 自带的 DragHandle / DraggablePanelWrapper：
    // ModularPanel.onMousePressed 会先套用被悬停控件自己的矩阵，再调用 onDragStart，
    // 于是 DraggablePanelWrapper.onDragStart 里的 context.transformX(0,0) 解析成**手柄**的原点而不是
    // 面板原点，抓取瞬间窗口会跳一个手柄偏移（约 WIDTH 像素），渲染矩阵也跟着偏。
    // 为绕开这一整套，下面的转发器不使用 movingArea / drawMovingState 渲染路径，
    // 而是在 onDrag 里用绝对鼠标坐标每帧直接重定位面板，并复用 MUI2 在拖拽结束时用的那套 resizer 数学。

    /** 把拖拽转发给给定面板：每帧按绝对鼠标坐标重定位（无矩阵偏移）。 */
    static final class PanelDragForwarder implements IDraggable {

        private final ModularPanel panel;
        private int grabX, grabY;
        private boolean moving;

        PanelDragForwarder(ModularPanel panel) {
            this.panel = panel;
        }

        @Override
        public boolean onDragStart(int button) {
            if (button != 0) {
                return false;
            }
            // 记录鼠标相对面板左上角的偏移（绝对坐标）
            Area a = this.panel.getArea();
            this.grabX = this.panel.getContext()
                .getAbsMouseX() - a.x;
            this.grabY = this.panel.getContext()
                .getAbsMouseY() - a.y;
            return true;
        }

        @Override
        public void onDrag(int mouseButton, long timeSinceLastClick) {
            reposition();
        }

        @Override
        public void onDragEnd(boolean successful) {
            // 拖拽过程中已经每帧重定位过，结束时不需再处理
        }

        private void reposition() {
            Area screen = this.panel.getScreen()
                .getScreenArea();
            Area pa = this.panel.getArea();
            int targetX = this.panel.getContext()
                .getAbsMouseX() - this.grabX;
            int targetY = this.panel.getContext()
                .getAbsMouseY() - this.grabY;
            // 把绝对目标左上角换算成 resizer 用的 0..1 相对锚点（与 MUI2 DraggablePanelWrapper#onDragEnd 同款）
            float relX = (targetX - screen.x) / (float) Math.max(1, screen.width - pa.width);
            float relY = (targetY - screen.y) / (float) Math.max(1, screen.height - pa.height);
            this.panel.resizer()
                .resetPosition();
            this.panel.resizer()
                .relativeToScreen();
            this.panel.resizer()
                .topRelAnchor(relY, relY)
                .leftRelAnchor(relX, relX);
            this.panel.scheduleResize();
        }

        @Override
        public void drawMovingState(ModularGuiContext context, float partialTicks) {
            // 面板在拖拽期间没有被禁用，会自己画在重定位后的位置，这里不需要额外绘制
        }

        @Override
        public Area getMovingArea() {
            return this.panel.getArea();
        }

        @Override
        public boolean isMoving() {
            return this.moving;
        }

        @Override
        public void setMoving(boolean moving) {
            // 刻意**不**禁用面板（不调 setEnabled(false)）：每帧重定位并让它正常渲染，
            // 这正是避免「拖动手柄偏移」的原因
            this.moving = moving;
        }

        @Override
        public void transform(IViewportStack stack) {
            // 空实现：面板按自己（已重定位）的区域绘制，不需要额外变换
        }
    }

    /** 一个把拖拽转发给所在 ModularPanel 的控件（通过 {@link PanelDragForwarder}）。 */
    public static final class DragTab extends Widget<DragTab> implements IDraggable, IViewport {

        private IDraggable forwarder;

        @Override
        public void onInit() {
            IWidget p = getParent();
            while (p != null && !(p instanceof ModularPanel)) {
                p = p.getParent();
            }
            if (p instanceof ModularPanel panel && panel.isDraggable()) {
                this.forwarder = new PanelDragForwarder(panel);
            }
        }

        @Override
        public boolean onDragStart(int button) {
            return this.forwarder != null && this.forwarder.onDragStart(button);
        }

        @Override
        public void onDragEnd(boolean successful) {
            if (this.forwarder != null) {
                this.forwarder.onDragEnd(successful);
            }
        }

        @Override
        public void onDrag(int mouseButton, long timeSinceLastClick) {
            if (this.forwarder != null) {
                this.forwarder.onDrag(mouseButton, timeSinceLastClick);
            }
        }

        @Override
        public void drawMovingState(ModularGuiContext context, float partialTicks) {
            if (this.forwarder != null) {
                this.forwarder.drawMovingState(context, partialTicks);
            }
        }

        @Override
        public Area getMovingArea() {
            return this.forwarder != null ? this.forwarder.getMovingArea() : null;
        }

        @Override
        public boolean isMoving() {
            return this.forwarder != null && this.forwarder.isMoving();
        }

        @Override
        public void setMoving(boolean moving) {
            if (this.forwarder != null) {
                this.forwarder.setMoving(moving);
            }
        }

        @Override
        public void transform(IViewportStack stack) {
            super.transform(stack);
        }
    }

    /**
     * 只用来画数字的文本控件：**永不参与命中测试**（isInside 恒 false）。
     * 它叠在样板槽上时不会进入 hovered 列表、也就不会吞掉槽位自己的点击/拖拽/释放——
     * 否则在槽位上点击会被当成「点到了槽外」，手里的物品会被扔出来。
     * 绘制本身由 canBeSeen（裁剪区）决定，不受 isInside 影响，所以在放出去之后仍然正常显示。
     */
    public static final class NonInteractiveText extends TextWidget<NonInteractiveText> {

        public NonInteractiveText(IKey key) {
            super(key);
        }

        @Override
        public boolean isInside(IViewportStack stack, int mx, int my, boolean absolute) {
            return false;
        }
    }
}
