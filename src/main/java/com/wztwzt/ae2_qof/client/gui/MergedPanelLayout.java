package com.wztwzt.ae2_qof.client.gui;

/**
 * 二合一终端面板的固定几何常量（AE 原生 4×4 样板面板布局，移植自 AE2Things）。
 * <p>
 * 面板画在 GUI 内 (209,0) 起（133 宽主区 + 40 宽条带 + 32 方块），
 * 槽位容器坐标为 AE 原生坐标，绘制时 y 方向 +68 重定位到面板上对应孔位。
 */
public final class MergedPanelLayout {

    public static final int PANEL_X = 209;
    public static final int PANEL_Y = 0;
    public static final int PANEL_W = 133;
    public static final int PANEL_H = 93;
    /** 面板总高：主区 93 + 条带 77 + 第三块 32 */
    public static final int PANEL_BOTTOM = 202;

    public static final int SLOT = 18;

    /** 槽位绘制时 y 方向相对容器坐标的偏移 */
    public static final int SLOT_Y_REPOSITION = 68;

    // ===== 原生槽位坐标（容器坐标） =====

    public static final int PATTERN_IN_X = 220;
    public static final int PATTERN_IN_Y = 31;
    public static final int PATTERN_OUT_X = 220;
    public static final int PATTERN_OUT_Y = 74;
    public static final int CRAFT_GRID_X = 224;
    public static final int CRAFT_GRID_Y = -50;
    public static final int EX_GRID_X = 224;
    public static final int EX_GRID_Y = -59;
    public static final int OUT_GRID_X = 321;
    public static final int CRAFT_RESULT_X = 316;
    public static final int CRAFT_RESULT_Y = -32;

    // ===== 原生按钮坐标（相对面板左上角） =====

    public static final int ENCODE_BTN_X = 220;
    public static final int ENCODE_BTN_Y = 118;
    public static final int TAB_BTN_X = 248;
    public static final int TAB_BTN_Y = 93;

    // ===== 编码按钮旁的额外按钮布局 =====
    // 上传(↑) 在编码左边 | 编码(Encode) | 召回(←) 在编码右边
    // 交换产物(⇄) 在处理模式的AE按钮区域

    /** 编码按钮左边的上传按钮 */
    public static final int UPLOAD_BTN_X = 206;
    public static final int UPLOAD_BTN_Y = 118;
    /** 编码按钮右边的召回按钮 */
    public static final int RECALL_BTN_X = 234;
    public static final int RECALL_BTN_Y = 118;

    /** 机器名绘制位置（面板右侧空白区） */
    public static final int MACHINE_NAME_X = 250;
    public static final int MACHINE_NAME_Y = 5;

    private MergedPanelLayout() {}

    public static boolean isInPanel(int x, int y) {
        return x >= PANEL_X && x <= PANEL_X + PANEL_W && y >= PANEL_Y && y <= PANEL_BOTTOM;
    }
}
