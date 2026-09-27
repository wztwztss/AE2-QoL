/*
 * 本文件属于本模组自有改造（3.22.0-fix46）：与搬运的 AE2PatternGen 界面同目录，但**不是**搬运代码。
 * 作用：给 GTNH-ModularUI（MUI1）的文本框加上"机器名片段 → Tab 循环候选"的能力。
 */
package com.wztwzt.ae2_qof.apgport.gui;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.generator.RecipeMapNames;

/**
 * 「RecipeMap 关键字」文本框（MUI1 版，3.22.0-fix46）：**输机器名片段 → 按 Tab 循环候选**。
 *
 * <h2>为什么又写一个（和 fix44 那个什么关系）</h2>
 * fix44 把 Tab 补全做在了 {@code generator/GeneratorPanel}（MUI2 面板），但批量样板生成器物品
 * **实际打开的是搬运进来的 AE2PatternGen 界面**（{@code apgport/gui/GuiPatternGen}，GTNH-MUI/MUI1）
 * ⇒ 那个面板根本打不开（死代码）。本类就是把这套能力**补到真正在用的 MUI1 界面上**，
 * 匹配数据仍复用 {@link RecipeMapNames}（中文名/英文名/关键字 三者任一，不重复实现）。
 *
 * <h2>关键点（与 fix44 同一处理）</h2>
 * Tab 填充后框里是**关键字**；若拿它当新片段继续匹配，候选会越切越窄 ⇒ 本类单独记住
 * {@code lockedFragment}：第一次按 Tab 锁定，之后循环都用它；**只有玩家手动改动文本**才解锁。
 * 候选为 0 时记 WARN 并把引导写进提示行（不静默）。
 */
public class RecipeMapTextFieldWidget extends TextFieldWidget {

    private final Consumer<String> hintSink;

    private String lockedFragment;
    private List<String> candidates = Collections.emptyList();
    private int index = -1;

    public RecipeMapTextFieldWidget(Consumer<String> hintSink) {
        this.hintSink = hintSink;
    }

    @Override
    public boolean onKeyPressed(char typedChar, int keyCode) {
        try {
            if (keyCode == Keyboard.KEY_TAB) {
                cycle();
                return true; // 吞掉 Tab，避免焦点被移走
            }
            this.lockedFragment = null;
            this.candidates = Collections.emptyList();
            this.index = -1;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] RecipeMap 关键字框（MUI1）按键处理失败（按普通输入处理）", t);
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    /** 由对照表选行后调用：直接填入该关键字。 */
    public void pickFromTable(String recipeMapId) {
        this.lockedFragment = null;
        this.candidates = Collections.emptyList();
        this.index = -1;
        setText(recipeMapId == null ? "" : recipeMapId);
        markForUpdate();
        hint("已从对照表填入：" + RecipeMapNames.chineseName(recipeMapId) + " · " + recipeMapId);
    }

    private void cycle() {
        if (this.lockedFragment == null) {
            String text = getText() == null ? "" : getText()
                .trim();
            this.lockedFragment = text;
            this.candidates = RecipeMapNames.matchIds(text);
            this.index = -1;
            MyMod.LOG.info(
                "[AE2QoL] RecipeMap 补全（MUI1 生成器）：片段='{}' 候选={} 个（匹配 关键字/中文名/英文名 任一）",
                text,
                this.candidates.size());
            if (this.candidates.isEmpty()) {
                hint("没有匹配的机器：'" + text + "'（换个片段，或用 [对照表]）");
                MyMod.LOG.warn("[AE2QoL] RecipeMap 补全：片段 '{}' 无候选", text);
                return;
            }
        }
        if (this.candidates.isEmpty()) return;
        this.index = (this.index + 1) % this.candidates.size();
        String id = this.candidates.get(this.index);
        setText(id);
        markForUpdate();
        hint(
            "当前候选 " + (this.index + 1)
                + "/"
                + this.candidates.size()
                + "："
                + RecipeMapNames.chineseName(id)
                + " · "
                + RecipeMapNames.englishName(id)
                + " · "
                + id);
    }

    private void hint(String text) {
        if (this.hintSink != null) this.hintSink.accept(text);
    }
}
