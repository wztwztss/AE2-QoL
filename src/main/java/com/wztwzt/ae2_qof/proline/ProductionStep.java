package com.wztwzt.ae2_qof.proline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 产线步骤（设计 §2.1 ProductionStep）。
 *
 * <p>
 * <b>A1 拍板的落点</b>：一切"步骤引用"只允许用稳定 id（{@link #id}，UUID 短格式），
 * 禁止用列表下标——绑卡 NBT、暂停位集合、采样指针全部挂 id；步骤删除/排序都不改 id。
 * machineType 是<b>机器稳定键</b>（GT MTE 内部注册名/类型标识，A2 拍板），
 * 本地化名只出现在展示层（服务端语言表不可靠，坑 #A2）。
 */
public final class ProductionStep {

    public String id;
    public String name;
    /** 机器稳定键。空串 = 尚未指定（步骤可先建后补）。 */
    public String machineType;
    public final List<ItemRow> inputs = new ArrayList<>();
    public final List<ItemRow> outputs = new ArrayList<>();
    /** 绑定的实体机器（坐标身份，见 {@link MachineRef}）。 */
    public final List<MachineRef> machines = new ArrayList<>();
    /**
     * wiki branch 标签（步骤级 chip）。空串 = 无标签（NEI/手工建的步骤默认无标签）。
     */
    public String branch = "";
    /** wiki hierarchy 分段标题。空串 = 归入"主线"显示段。 */
    public String hierarchy = "";
    /** 软暂停（步骤级）——只是状态位，绝不触碰 GT 机器（设计 §七铁则）。 */
    public boolean softPaused;

    public ProductionStep() {
        this.id = newId();
    }

    public ProductionStep(String forcedId) {
        this.id = forcedId;
    }

    public static String newId() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12);
    }

    /** 链扣假设判断：本步骤 outputs 是否提供 key（供自动连线与汇总口径使用）。 */
    public boolean produces(String key) {
        for (ItemRow o : outputs) if (o.key.equals(key)) return true;
        return false;
    }

    /** 主产物 = outputs 第一条（agents 排序的直觉口径，设计 §4.2 字段 4）。 */
    public ItemRow mainOutput() {
        return outputs.isEmpty() ? null : outputs.get(0);
    }

    public NBTTagCompound writeNbt(NBTTagCompound tag) {
        tag.setString("Id", id);
        tag.setString("Name", name == null ? "" : name);
        tag.setString("Type", machineType == null ? "" : machineType);
        tag.setString("Branch", branch == null ? "" : branch);
        tag.setString("Hier", hierarchy == null ? "" : hierarchy);
        tag.setBoolean("Paused", softPaused);
        writeRows(tag, "In", inputs);
        writeRows(tag, "Out", outputs);
        NBTTagList ml = new NBTTagList();
        for (MachineRef m : machines) ml.appendTag(m.writeNbt(new NBTTagCompound()));
        tag.setTag("Machines", ml);
        return tag;
    }

    public static ProductionStep readNbt(NBTTagCompound tag) {
        ProductionStep s = new ProductionStep(tag.getString("Id"));
        s.name = tag.getString("Name");
        s.machineType = tag.getString("Type");
        s.branch = tag.getString("Branch");
        s.hierarchy = tag.getString("Hier");
        s.softPaused = tag.getBoolean("Paused");
        readRows(tag, "In", s.inputs);
        readRows(tag, "Out", s.outputs);
        s.machines.clear();
        NBTTagList ml = tag.getTagList("Machines", 10);
        for (int i = 0; i < ml.tagCount(); i++) {
            NBTTagCompound mt = ml.getCompoundTagAt(i);
            if (mt.hasKey("X")) s.machines.add(MachineRef.readNbt(mt));
        }
        return s;
    }

    private static void writeRows(NBTTagCompound tag, String key, List<ItemRow> rows) {
        NBTTagList l = new NBTTagList();
        for (ItemRow r : rows) l.appendTag(r.writeNbt(new NBTTagCompound()));
        tag.setTag(key, l);
    }

    private static void readRows(NBTTagCompound tag, String key, List<ItemRow> out) {
        out.clear();
        NBTTagList l = tag.getTagList(key, 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound rt = l.getCompoundTagAt(i);
            if (rt.hasKey("Key")) out.add(ItemRow.readNbt(rt));
        }
    }

    /** 只读视图：避免外部直接改 List（编辑一律走 Manager 的原子操作）。 */
    public List<ItemRow> inputsView() {
        return Collections.unmodifiableList(inputs);
    }

    public List<ItemRow> outputsView() {
        return Collections.unmodifiableList(outputs);
    }

    public List<MachineRef> machinesView() {
        return Collections.unmodifiableList(machines);
    }
}
