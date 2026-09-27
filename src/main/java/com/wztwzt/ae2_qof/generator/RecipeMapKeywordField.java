package com.wztwzt.ae2_qof.generator;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;

/**
 * 「RecipeMap 关键字」输入框（3.20.0-fix44）：**输入机器名片段 → 按 Tab 循环候选**。
 *
 * <h2>为什么这样做</h2>
 * 以前必须手打技术关键字（{@code gt.recipe.rolling}）才能选机器，用户反馈"输入机器 id 编码太麻烦了"。
 * 现在：随便打**中文名 / 英文名 / 关键字**的一部分（例如"滚"、"rolling"、"roll"），按 Tab 就在候选间循环，
 * 每次把完整关键字填进框里，并把"当前候选 x/y：中文 · 英文 · 关键字"交给提示行显示。
 *
 * <h2>最容易做错的地方（已处理）</h2>
 * Tab 填充后框里已经是**关键字**；若把它当成新片段继续匹配，候选集会越切越窄甚至空掉。
 * 因此本类**单独记住"用户输入的片段"**（{@code lockedFragment}）：第一次按 Tab 时锁定，
 * 之后循环都基于它；**只有玩家手动改动文本**（任何非 Tab 按键）才解锁，下次 Tab 重新取片段。
 *
 * <h2>其它</h2>
 * 候选为 0 时记 WARN（不静默）；Tab 返回 {@code SUCCESS} 吞掉按键，避免焦点被移走。
 */
public class RecipeMapKeywordField extends TextFieldWidget {

    /** 提示行回调：把"当前候选 x/y：中文 · 英文 · 关键字"交给界面显示。 */
    private final Consumer<String> hintSink;

    private String lockedFragment;
    private List<String> candidates = Collections.emptyList();
    private int index = -1;

    public RecipeMapKeywordField(Consumer<String> hintSink) {
        this.hintSink = hintSink;
        setMaxLength(64);
    }

    @Override
    public Interactable.Result onKeyPressed(char typedChar, int keyCode) {
        try {
            if (keyCode == Keyboard.KEY_TAB) {
                cycle();
                return Interactable.Result.SUCCESS;
            }
            // 任何非 Tab 的按键都视为"玩家在手动改文本" ⇒ 解锁片段，下次 Tab 重新取
            this.lockedFragment = null;
            this.candidates = Collections.emptyList();
            this.index = -1;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] RecipeMap 关键字框按键处理失败（该次按键按普通输入处理）", t);
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    /** 由对照表页选行后调用：直接填入该关键字。 */
    public void pickFromTable(String recipeMapId) {
        this.lockedFragment = null;
        this.candidates = Collections.emptyList();
        this.index = -1;
        setText(recipeMapId == null ? "" : recipeMapId);
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
                "[AE2QoL] RecipeMap 补全：片段='{}' 候选={} 个（匹配 关键字/中文名/英文名 任一）",
                text,
                this.candidates.size());
            if (this.candidates.isEmpty()) {
                hint("没有匹配的机器：'" + text + "'（换个片段，或点右侧 [对照表] 查）");
                MyMod.LOG.warn("[AE2QoL] RecipeMap 补全：片段 '{}' 无候选", text);
                return;
            }
        }
        if (this.candidates.isEmpty()) return;
        this.index = (this.index + 1) % this.candidates.size();
        String id = this.candidates.get(this.index);
        setText(id);
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
