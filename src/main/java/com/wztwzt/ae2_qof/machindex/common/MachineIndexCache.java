package com.wztwzt.ae2_qof.machindex.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * 扫描结果的内部缓存（{@code config/ae2_qof/machindex/cache.dat}）。
 *
 * <p>
 * <b>这不是交付清单</b>（硬约束 10）：唯一用途是「启动校验」——缓存键命中就跳过本轮 NEI 扫描，
 * 只做任务结构对账；键不命中才重扫。
 *
 * <p>
 * <b>为什么用 NBT 而不是 JSON：</b>模型里含 {@code ItemStack}（机器图标），
 * 直接走 {@code ItemStack.writeToNBT} 最稳（不会因 tag 顺序/大数值失精度），也避免再写一套 ItemStack
 * 的 JSON 编解码。文件名仍是内部文件，玩家无需关心格式。
 */
public final class MachineIndexCache {

    private static final int SCHEMA = IndexConfig.CACHE_SCHEMA_VERSION;

    private MachineIndexCache() {}

    /** 缓存键：任一变化 → 缓存无效。 */
    public static final class CacheKey {

        public String mcVersion = "";
        public String modListHash = "";
        public String handlerListHash = "";
        public String configHash = "";
        public int schema = SCHEMA;

        public NBTTagCompound toNbt() {
            NBTTagCompound n = new NBTTagCompound();
            n.setString("mcVersion", mcVersion);
            n.setString("modListHash", modListHash);
            n.setString("handlerListHash", handlerListHash);
            n.setString("configHash", configHash);
            n.setInteger("schema", schema);
            return n;
        }

        public static CacheKey fromNbt(NBTTagCompound n) {
            CacheKey k = new CacheKey();
            k.mcVersion = n.getString("mcVersion");
            k.modListHash = n.getString("modListHash");
            k.handlerListHash = n.getString("handlerListHash");
            k.configHash = n.getString("configHash");
            k.schema = n.getInteger("schema");
            return k;
        }

        /** 与另一份键是否等价（schema 单独判定，便于报「口径变了」而不是笼统的 not match）。 */
        public boolean matches(CacheKey other) {
            if (other == null) return false;
            return schema == other.schema && mcVersion.equals(other.mcVersion)
                && modListHash.equals(other.modListHash)
                && handlerListHash.equals(other.handlerListHash)
                && configHash.equals(other.configHash);
        }

        public String describe() {
            return "schema=" + schema
                + ", mc="
                + mcVersion
                + ", mods="
                + shortHash(modListHash)
                + ", handlers="
                + shortHash(handlerListHash)
                + ", config="
                + shortHash(configHash);
        }
    }

    /** 缓存文件内容 = 键 + 扫描结果 + 生成时间 + 统计。 */
    public static final class CacheFile {

        public final CacheKey key = new CacheKey();
        public long generatedAt;
        public final IndexModel.ScanResult result = new IndexModel.ScanResult();
        public final List<String> stats = new ArrayList<>();

        public NBTTagCompound toNbt() {
            NBTTagCompound n = new NBTTagCompound();
            n.setTag("key", key.toNbt());
            n.setLong("generatedAt", generatedAt);
            n.setTag("result", result.toNbt());
            return n;
        }

        public static CacheFile fromNbt(NBTTagCompound n) {
            CacheFile c = new CacheFile();
            if (n.hasKey("key")) {
                NBTTagCompound k = n.getCompoundTag("key");
                c.key.mcVersion = k.getString("mcVersion");
                c.key.modListHash = k.getString("modListHash");
                c.key.handlerListHash = k.getString("handlerListHash");
                c.key.configHash = k.getString("configHash");
                c.key.schema = k.getInteger("schema");
            }
            c.generatedAt = n.getLong("generatedAt");
            if (n.hasKey("result")) {
                IndexModel.ScanResult r = IndexModel.ScanResult.fromNbt(n.getCompoundTag("result"));
                c.result.types.addAll(r.types);
                c.result.machines.addAll(r.machines);
                c.result.quests.addAll(r.quests);
                c.result.stats.addAll(r.stats);
            }
            return c;
        }
    }

    // ===================== 键计算 =====================

    /** 计算当前环境的缓存键（handler 列表哈希由客户端扫描器提供，服务端传空）。 */
    public static CacheKey buildKey(String handlerListHash) {
        CacheKey k = new CacheKey();
        k.mcVersion = "1.7.10/GTNH-2.9.0-RC-2";
        k.modListHash = hashModList();
        k.handlerListHash = handlerListHash == null ? "" : handlerListHash;
        k.configHash = hashConfig();
        return k;
    }

    /** 已加载模组列表的确定性哈希（sorted "modid@version"）。 */
    private static String hashModList() {
        try {
            List<String> entries = new ArrayList<>();
            for (ModContainer mc : Loader.instance()
                .getActiveModList()) {
                String id = mc.getModId();
                String version = mc.getVersion();
                entries.add(id + "@" + (version == null ? "?" : version));
            }
            Collections.sort(entries);
            return sha256(join(entries, "\n"));
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    /** 影响索引结果的配置的确定性哈希。 */
    private static String hashConfig() {
        // 只放「会影响扫描/聚合结果」的项；纯展示项（排序/显示名）不进键，避免改个颜色就重扫
        String s = IndexConfig.enableCraftingInference + "|"
            + IndexConfig.includeUnknown
            + "|"
            + IndexConfig.chapterMode
            + "|"
            + IndexConfig.maxQuestsPerChapter;
        return sha256(s);
    }

    // ===================== 读写 =====================

    public static File cacheFile(File configDir) {
        return new File(new File(configDir, "ae2_qof/machindex"), "cache.dat");
    }

    public static CacheFile load(File configDir) {
        File f = cacheFile(configDir);
        if (!f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            NBTTagCompound n = CompressedStreamTools.readCompressed(in);
            if (n == null) return null;
            CacheFile c = CacheFile.fromNbt(n);
            if (c.key.schema != SCHEMA) {
                MyMod.LOG.info("[AE2QoL] machindex 缓存 schema 不符（" + c.key.schema + " != " + SCHEMA + "），视为无效");
                return null;
            }
            return c;
        } catch (Throwable t) {
            // 损坏/不完整 → 视为无效并删除，下次重扫（不能静默：留一行日志说明为什么重扫）
            MyMod.LOG.warn("[AE2QoL] machindex 缓存读取失败（将重扫）：" + t);
            try {
                if (f.isFile() && !f.delete()) {
                    MyMod.LOG.warn("[AE2QoL] machindex 损坏缓存删除失败：" + f.getAbsolutePath());
                }
            } catch (Throwable ignored) {
                // 删除失败不影响主流程
            }
            return null;
        }
    }

    public static boolean save(File configDir, CacheFile data) {
        File f = cacheFile(configDir);
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            MyMod.LOG.warn("[AE2QoL] machindex 缓存目录创建失败：" + dir.getAbsolutePath());
            return false;
        }
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            CompressedStreamTools.writeCompressed(data.toNbt(), out);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 缓存写入失败：" + t);
            deleteQuietly(tmp);
            return false;
        }
        // 先写 tmp 再改名：写到一半崩溃不会留下半份缓存
        if (f.isFile() && !f.delete()) {
            MyMod.LOG.warn("[AE2QoL] machindex 旧缓存无法删除，尝试直接覆盖");
        }
        if (!tmp.renameTo(f)) {
            MyMod.LOG.warn("[AE2QoL] machindex 缓存改名失败：" + tmp.getAbsolutePath());
            deleteQuietly(tmp);
            return false;
        }
        return true;
    }

    public static boolean clear(File configDir) {
        File f = cacheFile(configDir);
        boolean ok = !f.isFile() || f.delete();
        if (!ok) MyMod.LOG.warn("[AE2QoL] machindex 缓存删除失败：" + f.getAbsolutePath());
        return ok;
    }

    // ===================== 工具 =====================

    private static void deleteQuietly(File f) {
        try {
            if (f != null && f.isFile()) f.delete();
        } catch (Throwable ignored) {
            // 清理失败无副作用
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public static String shortHash(String h) {
        if (h == null || h.isEmpty()) return "-";
        return h.length() <= 8 ? h : h.substring(0, 8);
    }

    public static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    /** 供调试用：把缓存键的明文写出来（不含物品数据）。 */
    public static void writeKeyDebug(File configDir, CacheKey key) {
        try {
            File f = new File(new File(configDir, "ae2_qof/machindex"), "cache_key.txt");
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(key.describe());
            }
        } catch (Throwable ignored) {
            // 纯调试辅助，失败不影响功能
        }
    }

    /** 供调试用：读回 cache_key.txt（只用于 /ae2qol machindex status 的补充显示）。 */
    public static String readKeyDebug(File configDir) {
        File f = new File(new File(configDir, "ae2_qof/machindex"), "cache_key.txt");
        if (!f.isFile()) return "";
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[256];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString()
                .trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
