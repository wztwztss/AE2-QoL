package cn.dancingsnow.aeinfinitycell.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

public final class NbtKey {

    public static final NbtKey NONE = new NbtKey(new NBTTagCompound(), "{}");

    private final NBTTagCompound tag;
    private final String canonical;

    /**
     * 3.35.0-fix15（审查 P2-8）：NbtKey **驻留缓存**。
     * <p>
     * 每次物品操作都会走 {@code ItemStackKey.from → NbtKey.of}，而旧实现固定付两次全树遍历：
     * {@code canonicalizeCompound}（深拷贝整棵 NBT 树 + 排序）+ {@code canonicalString}（再次遍历建串）。
     * 同一物品（编码样板、带 NBT 的工具/机器）在一个会话里会被查询成千上万次 ⇒ 这份代价被反复支付。
     * <p>
     * canonical 串是**内容的纯函数**（键排序、列表保序、叶子值不参与规范化）⇒ 用它当驻留键，
     * 命中即完全跳过深拷贝，只剩一次建串遍历。
     * <p>
     * ⚠️ 语义严格不变：{@code NbtKey} 不可变（{@code tag} 只在构造期写入、{@code copyTag()} 返回副本），
     * 驻留只是让内容相等的 key **共享实例**；equals/hashCode 仍按 canonical 串比较 ⇒ 存档身份不变
     * （这也正是不能改 canonical 规则的原因：改了老存档的条目会匹配不上 ⇒ 重复记账）。
     * 缓存有界：超过上限整体清空（驻留只是加速手段，清空无正确性影响）。
     */
    private static final int INTERN_MAX = 256;
    private static final java.util.concurrent.ConcurrentHashMap<String, NbtKey> INTERN =
        new java.util.concurrent.ConcurrentHashMap<String, NbtKey>();

    private NbtKey(NBTTagCompound tag, String canonical) {
        this.tag = tag;
        this.canonical = canonical;
    }

    public static NbtKey of(NBTTagCompound tag) {
        if (tag == null) {
            return NONE;
        }
        // 单次遍历先算规范串（不深拷贝）：命中驻留即省掉整棵树的拷贝
        String canonical = canonicalString(tag);
        NbtKey cached = INTERN.get(canonical);
        if (cached != null) {
            return cached;
        }
        NbtKey fresh = new NbtKey(canonicalizeCompound(tag), canonical);
        if (INTERN.size() >= INTERN_MAX) {
            INTERN.clear();
        }
        INTERN.putIfAbsent(canonical, fresh);
        return fresh;
    }

    public NBTTagCompound copyTag() {
        return (NBTTagCompound) tag.copy();
    }

    public boolean isEmpty() {
        return this == NONE || tag.func_150296_c()
            .isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NbtKey)) {
            return false;
        }
        NbtKey nbtKey = (NbtKey) o;
        return Objects.equals(canonical, nbtKey.canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    @Override
    public String toString() {
        return canonical;
    }

    private static NBTTagCompound canonicalizeCompound(NBTTagCompound source) {
        NBTTagCompound result = new NBTTagCompound();
        List<String> keys = sortedKeys(source);
        for (String key : keys) {
            result.setTag(key, canonicalizeTag(source.getTag(key)));
        }
        return result;
    }

    private static NBTBase canonicalizeTag(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            return canonicalizeCompound((NBTTagCompound) tag);
        }
        if (tag instanceof NBTTagList) {
            NBTTagList result = new NBTTagList();
            NBTTagList source = (NBTTagList) tag.copy();
            while (source.tagCount() > 0) {
                result.appendTag(canonicalizeTag(source.removeTag(0)));
            }
            return result;
        }
        return tag.copy();
    }

    private static String canonicalString(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            StringBuilder builder = new StringBuilder("{");
            List<String> keys = sortedKeys(compound);
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                String key = keys.get(i);
                builder.append(key.length())
                    .append(':')
                    .append(key)
                    .append('=')
                    .append(canonicalString(compound.getTag(key)));
            }
            return builder.append('}')
                .toString();
        }

        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag.copy();
            StringBuilder builder = new StringBuilder("[");
            int index = 0;
            while (list.tagCount() > 0) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalString(list.removeTag(0)));
                index++;
            }
            return builder.append(']')
                .toString();
        }

        if (tag instanceof NBTTagByteArray) {
            return tag.getId() + ":" + Arrays.toString(((NBTTagByteArray) tag).func_150292_c());
        }

        if (tag instanceof NBTTagIntArray) {
            return tag.getId() + ":" + Arrays.toString(((NBTTagIntArray) tag).func_150302_c());
        }

        return tag.getId() + ":" + tag.toString();
    }

    private static List<String> sortedKeys(NBTTagCompound compound) {
        List<String> keys = new ArrayList<String>(compound.func_150296_c());
        Collections.sort(keys);
        return keys;
    }
}
