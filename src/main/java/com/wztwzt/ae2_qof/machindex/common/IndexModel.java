package com.wztwzt.ae2_qof.machindex.common;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * machindex 的数据模型（机器功能—电压等级索引）。
 *
 * <p>
 * <b>隔离纪律</b>：本类只依赖 {@code net.minecraft.*}，<b>不 import {@code codechicken.nei.*}
 * 也不 import {@code betterquesting.*}</b>。原因：NEI 类型只存在于客户端，BQ 类型只存在于服务端，
 * 而这份模型要跨客户端→服务端的边界（扫描在客户端、写库在服务端），
 * 一旦这里出现任一方的类型，专用服务器就会在类加载阶段炸掉。
 *
 * <p>
 * 模型里的 {@code displayStackAll} 只在前端/内存里用；落缓存与走网络时一律经
 * {@link #toNbt()} / {@link #fromNbt(NBTTagCompound)} 转成 NBT，
 * 因此 {@code ItemStack} 只以 NBT 形态跨越边界（避免 ItemStack 直接序列化的 NPE 与体积问题）。
 */
public final class IndexModel {

    private IndexModel() {}

    /** 电压档位常量：0=ULV … 14=MAX，-1=未知（与 {@code GTValues.VN} 下标一致，多出 UNKNOWN）。 */
    public static final int TIER_UNKNOWN = -1;

    /** 一个配方/功能类型（NEI 视角）。 */
    public static final class RecipeTypeIndex {

        /** {@code RecipeCatalysts.getRecipeID(handler)} 的结果，如 {@code gt.recipe.orewasher}。 */
        public String typeId = "";
        /** 本地化显示名；取不到时回落 typeId 原串（不丢条目）。 */
        public String displayName = "";
        /** 关联的 handler id 列表（诊断用，一个类型可能有多个 handler）。 */
        public final List<String> handlerIds = new ArrayList<>();
        /** 首个 handler 的类全名（诊断用）。 */
        public String handlerClass = "";
        /** 该类型是否只在 handler 索引里出现（催化剂表里没有）。 */
        public boolean noCatalyst = false;

        public NBTTagCompound toNbt() {
            NBTTagCompound n = new NBTTagCompound();
            n.setString("typeId", typeId);
            n.setString("displayName", displayName);
            n.setString("handlerClass", handlerClass);
            n.setBoolean("noCatalyst", noCatalyst);
            NBTTagList ids = new NBTTagList();
            for (String id : handlerIds) {
                NBTTagCompound e = new NBTTagCompound();
                e.setString("v", id);
                ids.appendTag(e);
            }
            n.setTag("handlerIds", ids);
            return n;
        }

        public static RecipeTypeIndex fromNbt(NBTTagCompound n) {
            RecipeTypeIndex r = new RecipeTypeIndex();
            r.typeId = n.getString("typeId");
            r.displayName = n.getString("displayName");
            r.handlerClass = n.getString("handlerClass");
            r.noCatalyst = n.getBoolean("noCatalyst");
            NBTTagList ids = n.getTagList("handlerIds", 10);
            for (int i = 0; i < ids.tagCount(); i++) {
                r.handlerIds.add(
                    ids.getCompoundTagAt(i)
                        .getString("v"));
            }
            return r;
        }
    }

    /**
     * 一台去重后的机器主机。
     *
     * <p>
     * 身份三元组 = (registryName, meta, nbtHash)；带 NBT 的催化剂在 meta 之后追加 nbtHash 参与 ID 派生。
     */
    public static final class MachineEntry {

        public String registryName = "";
        public int meta = 0;
        public String nbtHash = "";
        /** 原始催化剂堆（带 NBT），**仅内存**，不进缓存、不走网络。 */
        public ItemStack displayStack;
        public String modId = "";
        public String modName = "";
        /** 该机器服务的全部类型 id。 */
        public final List<String> typeIds = new ArrayList<>();
        /** 主分组档位（见 VoltageResolver 的口径）；{@link #TIER_UNKNOWN} = 未知。 */
        public int mainTier = TIER_UNKNOWN;
        /** 全部解析出的档位（跨来源，诊断用）。 */
        public final List<Integer> allTiers = new ArrayList<>();
        /** machine_tier / crafting_recipe / recipe_voltage / unknown。 */
        public String voltageSource = "";
        public boolean isMultiblock = false;
        /** 来源/特例备注。 */
        public String note = "";

        /** 展示名（物品显示名），进任务标题与缓存用。 */
        public String displayName = "";

        public String identityKey() {
            return registryName + "@" + meta + "#" + nbtHash;
        }

        public NBTTagCompound toNbt() {
            NBTTagCompound n = new NBTTagCompound();
            n.setString("registryName", registryName);
            n.setInteger("meta", meta);
            n.setString("nbtHash", nbtHash);
            n.setString("modId", modId);
            n.setString("modName", modName);
            n.setInteger("mainTier", mainTier);
            n.setString("voltageSource", voltageSource);
            n.setBoolean("isMultiblock", isMultiblock);
            n.setString("note", note);
            n.setString("displayName", displayName);
            if (displayStack != null) {
                NBTTagCompound st = new NBTTagCompound();
                displayStack.writeToNBT(st);
                n.setTag("stack", st);
            }
            NBTTagList t = new NBTTagList();
            for (String id : typeIds) {
                NBTTagCompound e = new NBTTagCompound();
                e.setString("v", id);
                t.appendTag(e);
            }
            n.setTag("typeIds", t);
            NBTTagList tiers = new NBTTagList();
            for (Integer tier : allTiers) {
                NBTTagCompound e = new NBTTagCompound();
                e.setInteger("v", tier);
                tiers.appendTag(e);
            }
            n.setTag("allTiers", tiers);
            return n;
        }

        public static MachineEntry fromNbt(NBTTagCompound n) {
            MachineEntry m = new MachineEntry();
            m.registryName = n.getString("registryName");
            m.meta = n.getInteger("meta");
            m.nbtHash = n.getString("nbtHash");
            m.modId = n.getString("modId");
            m.modName = n.getString("modName");
            m.mainTier = n.getInteger("mainTier");
            m.voltageSource = n.getString("voltageSource");
            m.isMultiblock = n.getBoolean("isMultiblock");
            m.note = n.getString("note");
            m.displayName = n.getString("displayName");
            if (n.hasKey("stack")) {
                m.displayStack = ItemStack.loadItemStackFromNBT(n.getCompoundTag("stack"));
            }
            NBTTagList t = n.getTagList("typeIds", 10);
            for (int i = 0; i < t.tagCount(); i++) {
                m.typeIds.add(
                    t.getCompoundTagAt(i)
                        .getString("v"));
            }
            NBTTagList tiers = n.getTagList("allTiers", 10);
            for (int i = 0; i < tiers.tagCount(); i++) {
                m.allTiers.add(
                    tiers.getCompoundTagAt(i)
                        .getInteger("v"));
            }
            return m;
        }
    }

    /**
     * 一个待写入的 BQ 任务（合并后：同一 recipeType + 同一主电压档 = 一个任务，
     * 任务内列出该组合下的所有机器）。
     */
    public static final class QuestDefinition {

        /** 章节标签（「加工」/「生产」），BqQuestWriter 据此分卷。 */
        public String groupKey = "";
        public String typeId = "";
        public String typeDisplayName = "";
        /** 二级类别（研磨/粉碎、发电…），用于章内分组与文字目录。 */
        public String categoryLabel = "";
        public int mainTier = TIER_UNKNOWN;

        public int x;
        public int y;

        public String title = "";
        public String desc = "";

        /** 图标：组内第一台机器（确定性排序后的首台）。 */
        public ItemStack icon;

        /**
         * 本组内的全部机器（任务内逐台列出）。每个元素都是完整 MachineEntry，
         * 便于写 TaskOptionalRetrieval 的 requiredItems 与生成 DESC。
         */
        public final List<MachineEntry> machines = new ArrayList<>();

        /** 内容哈希：title + desc + 图标身份 + 机器身份集 + 坐标 → 变更检测用。 */
        public String signature = "";

        /** 任务 UUID 的派生键（不含坐标，避免布局变化导致 ID 变化）。 */
        public String stableKey = "";

        public NBTTagCompound toNbt() {
            NBTTagCompound n = new NBTTagCompound();
            n.setString("groupKey", groupKey);
            n.setString("typeId", typeId);
            n.setString("typeDisplayName", typeDisplayName);
            n.setString("categoryLabel", categoryLabel);
            n.setInteger("mainTier", mainTier);
            n.setInteger("x", x);
            n.setInteger("y", y);
            n.setString("title", title);
            n.setString("desc", desc);
            n.setString("signature", signature);
            n.setString("stableKey", stableKey);
            if (icon != null) {
                NBTTagCompound st = new NBTTagCompound();
                icon.writeToNBT(st);
                n.setTag("icon", st);
            }
            NBTTagList ms = new NBTTagList();
            for (MachineEntry m : machines) {
                ms.appendTag(m.toNbt());
            }
            n.setTag("machines", ms);
            return n;
        }

        public static QuestDefinition fromNbt(NBTTagCompound n) {
            QuestDefinition q = new QuestDefinition();
            q.groupKey = n.getString("groupKey");
            q.typeId = n.getString("typeId");
            q.typeDisplayName = n.getString("typeDisplayName");
            q.categoryLabel = n.getString("categoryLabel");
            q.mainTier = n.getInteger("mainTier");
            q.x = n.getInteger("x");
            q.y = n.getInteger("y");
            q.title = n.getString("title");
            q.desc = n.getString("desc");
            q.signature = n.getString("signature");
            q.stableKey = n.getString("stableKey");
            if (n.hasKey("icon")) {
                q.icon = ItemStack.loadItemStackFromNBT(n.getCompoundTag("icon"));
            }
            NBTTagList ms = n.getTagList("machines", 10);
            for (int i = 0; i < ms.tagCount(); i++) {
                q.machines.add(MachineEntry.fromNbt(ms.getCompoundTagAt(i)));
            }
            return q;
        }
    }

    /** 一次扫描的完整结果（= 缓存内容）。 */
    public static final class ScanResult {

        public final List<RecipeTypeIndex> types = new ArrayList<>();
        public final List<MachineEntry> machines = new ArrayList<>();
        public final List<QuestDefinition> quests = new ArrayList<>();
        public final List<String> stats = new ArrayList<>();

        public NBTTagCompound toNbt() {
            NBTTagCompound n = new NBTTagCompound();
            NBTTagList ts = new NBTTagList();
            for (RecipeTypeIndex t : types) {
                ts.appendTag(t.toNbt());
            }
            n.setTag("types", ts);

            NBTTagList ms = new NBTTagList();
            for (MachineEntry m : machines) {
                ms.appendTag(m.toNbt());
            }
            n.setTag("machines", ms);

            NBTTagList qs = new NBTTagList();
            for (QuestDefinition q : quests) {
                qs.appendTag(q.toNbt());
            }
            n.setTag("quests", qs);

            NBTTagList ss = new NBTTagList();
            for (String s : stats) {
                NBTTagCompound e = new NBTTagCompound();
                e.setString("v", s);
                ss.appendTag(e);
            }
            n.setTag("stats", ss);
            return n;
        }

        public static ScanResult fromNbt(NBTTagCompound n) {
            ScanResult r = new ScanResult();
            NBTTagList ts = n.getTagList("types", 10);
            for (int i = 0; i < ts.tagCount(); i++) {
                r.types.add(RecipeTypeIndex.fromNbt(ts.getCompoundTagAt(i)));
            }
            NBTTagList ms = n.getTagList("machines", 10);
            for (int i = 0; i < ms.tagCount(); i++) {
                r.machines.add(MachineEntry.fromNbt(ms.getCompoundTagAt(i)));
            }
            NBTTagList qs = n.getTagList("quests", 10);
            for (int i = 0; i < qs.tagCount(); i++) {
                r.quests.add(QuestDefinition.fromNbt(qs.getCompoundTagAt(i)));
            }
            NBTTagList ss = n.getTagList("stats", 10);
            for (int i = 0; i < ss.tagCount(); i++) {
                r.stats.add(
                    ss.getCompoundTagAt(i)
                        .getString("v"));
            }
            return r;
        }
    }
}
