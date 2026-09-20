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

    public synchronized void clear() {
        if (!dirty.isEmpty()) {
            AEInfinityCell.LOG.error("Retaining {} unsaved infinity cells for {}. Fix disk access before shutdown!",
                dirty.size(), directory);
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
