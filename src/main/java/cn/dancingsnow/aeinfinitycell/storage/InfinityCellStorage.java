package cn.dancingsnow.aeinfinitycell.storage;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.DimensionManager;

import cn.dancingsnow.aeinfinitycell.AEInfinityCell;

public final class InfinityCellStorage {

    private static final String DIR_NAME = "AEInfinityCell";
    // Failed writes must not be carried into another integrated-server world.
    private static final Map<File, InfinityCellStorage> STORES = new LinkedHashMap<>();
    private final File directory;

    private final Map<UUID, InfinityCellRecord> cache = new LinkedHashMap<UUID, InfinityCellRecord>();
    private final Set<UUID> dirty = new LinkedHashSet<UUID>();

    InfinityCellStorage(File directory) {
        this.directory = directory;
    }

    public static synchronized InfinityCellStorage getInstance() {
        File root = DimensionManager.getCurrentSaveRootDirectory();
        if (root == null) {
            throw new IllegalStateException("Infinity cell access without a server save directory");
        }
        File dir = new File(root, "data/" + DIR_NAME).getAbsoluteFile();
        return STORES.computeIfAbsent(dir, InfinityCellStorage::new);
    }

    public synchronized InfinityCellRecord getOrCreate(UUID id) {
        InfinityCellRecord cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        InfinityCellRecord record = loadFromDisk(id);
        // Read failures are unavailable, never new empty writable cells.
        if (record != null) cache.put(id, record);
        return record;
    }

    public synchronized void markDirty(UUID id) {
        if (cache.containsKey(id)) {
            dirty.add(id);
        }
    }

    /** Returns false while any record still requires a successful durable write. */
    public synchronized boolean saveAll() {
        if (dirty.isEmpty()) return true;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            AEInfinityCell.LOG.error("Cannot create infinity cell directory: {}", directory);
            return false;
        }
        for (UUID id : new LinkedHashSet<>(dirty)) {
            InfinityCellRecord record = cache.get(id);
            if (record != null && saveToDisk(id, record, directory)) {
                dirty.remove(id);
            }
        }
        return dirty.isEmpty();
    }

    public boolean hasCellFile(UUID id) {
        File file = cellFile(id);
        return file != null && file.exists();
    }

    // ==================== 3.35.0-fix21（审查 P2-10）：管理命令存储面 ====================

    /** 数据文件索引条目（{@code /ae2qof cell list} 用）。 */
    public static final class CellFileInfo {

        public final UUID id;
        public final long sizeBytes;
        public final long lastModifiedMs;

        CellFileInfo(UUID id, long sizeBytes, long lastModifiedMs) {
            this.id = id;
            this.sizeBytes = sizeBytes;
            this.lastModifiedMs = lastModifiedMs;
        }
    }

    /**
     * 扫描本存档目录下全部元件数据文件（按最后修改时间降序）。供管理命令人工比对
     * "哪些是已拆掉元件的孤儿文件"——自动判定不可行（需要世界级物品扫描，且元件可能
     * 在未加载区块里，误删即不可逆），因此由管理员用证据决定。
     */
    public synchronized java.util.List<CellFileInfo> listCellFiles() {
        java.util.List<CellFileInfo> out = new java.util.ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return out;
        int invalidNames = 0;
        for (File f : files) {
            String base = f.getName();
            try {
                UUID id = UUID.fromString(base.substring(0, base.length() - ".dat".length()));
                out.add(new CellFileInfo(id, f.length(), f.lastModified()));
            } catch (RuntimeException badName) {
                invalidNames++;
            }
        }
        if (invalidNames > 0) {
            // 不许静默：目录里存在无法解析为 UUID 的 .dat（来路？）——管理员应当知道
            AEInfinityCell.LOG.warn("[AE2QoL] 无限元件目录发现 {} 个无法解析为 UUID 的数据文件（未列入清单）", invalidNames);
        }
        out.sort((a, b) -> Long.compare(b.lastModifiedMs, a.lastModifiedMs));
        return out;
    }

    /** 元件是否有未落盘修改（归档前必须为 false——先让正常保存落盘）。 */
    public synchronized boolean isDirtyCell(UUID id) {
        return dirty.contains(id);
    }

    /** 只读检视一个元件（不写入进程内缓存；文件缺失/损坏返回 null）。 */
    public synchronized InfinityCellRecord inspectCell(UUID id) {
        return loadFromDisk(id);
    }

    /**
     * **移动**（不删除）元件数据文件到 {@code data/AEInfinityCell/archive/}。
     * 移动后该 UUID 立即从读取路径消失——对应元件物品若重新出现将读成**空元件**
     * （内容保留在 archive，可用 {@link #restoreCell} 找回）。
     *
     * @return 人类可读的结果消息（成功/拒绝原因），由调用方转聊天
     */
    public synchronized String archiveCell(UUID id) {
        if (id == null) return "UUID 缺失";
        File live = cellFile(id);
        if (live == null || !live.exists()) return "数据文件不存在：" + id;
        if (dirty.contains(id)) {
            return "该元件有未落盘修改（刚被使用过）——先等正常保存完成再归档";
        }
        File archiveDir = new File(directory, "archive");
        if (!archiveDir.isDirectory() && !archiveDir.mkdirs()) {
            return "无法创建归档目录：" + archiveDir;
        }
        File target = new File(archiveDir, id.toString() + ".dat");
        if (target.exists()) {
            return "归档目录已存在同名文件：" + target.getName() + "（先处理它再重试）";
        }
        cache.remove(id); // 非 dirty 的缓存条目与磁盘一致，逐出安全
        try {
            java.nio.file.Files.move(live.toPath(), target.toPath());
        } catch (IOException e) {
            AEInfinityCell.LOG.error("Failed to archive cell {}", id, e);
            return "归档失败（文件未动）：见服务端日志";
        }
        AEInfinityCell.LOG.info("Archived infinity cell {} -> {}", live.getName(), target);
        return "已归档（未删除，可用 /ae2qof cell restore 找回）：" + id;
    }

    /** 从归档目录移回（活动文件已存在时拒绝——避免覆盖现网数据）。 */
    public synchronized String restoreCell(UUID id) {
        if (id == null) return "UUID 缺失";
        File archiveFile = new File(new File(directory, "archive"), id.toString() + ".dat");
        if (!archiveFile.exists()) return "归档目录没有该文件：" + id;
        File live = cellFile(id);
        if (live != null && live.exists()) {
            return "现网已存在同名数据文件，拒绝覆盖：" + id;
        }
        try {
            java.nio.file.Files.move(archiveFile.toPath(), live.toPath());
        } catch (IOException e) {
            AEInfinityCell.LOG.error("Failed to restore cell {}", id, e);
            return "恢复失败（文件未动）：见服务端日志";
        }
        AEInfinityCell.LOG.info("Restored infinity cell {} -> {}", archiveFile.getName(), live);
        return "已恢复到现网目录：" + id;
    }

    public synchronized void clear() {
        if (!dirty.isEmpty()) {
            AEInfinityCell.LOG.error(
                "Retaining {} unsaved infinity cells for {}. Fix disk access before shutdown!",
                dirty.size(),
                directory);
            return;
        }
        cache.clear();
    }

    private InfinityCellRecord loadFromDisk(UUID id) {
        InfinityCellRecord record = new InfinityCellRecord();
        File file = cellFile(id);
        if (file == null || !file.exists()) {
            return record;
        }
        try {
            NBTTagCompound tag = CompressedStreamTools.read(file);
            if (tag == null) throw new IOException("Missing root NBT in existing cell file");
            record.readFromNBT(tag);
        } catch (IOException | RuntimeException e) {
            AEInfinityCell.LOG.error("Infinity cell {} unavailable; original file left untouched: {}", id, file, e);
            return null;
        }
        return record;
    }

    private boolean saveToDisk(UUID id, InfinityCellRecord record, File dir) {
        File file = new File(dir, id.toString() + ".dat");
        try {
            CompressedStreamTools.safeWrite(record.writeToNBT(), file);
            return true;
        } catch (IOException | RuntimeException e) {
            AEInfinityCell.LOG.error("Failed to save cell {}; kept dirty for retry: {}", id, file, e);
            return false;
        }
    }

    private File cellFile(UUID id) {
        return new File(directory, id.toString() + ".dat");
    }
}
