package cn.dancingsnow.aeinfinitycell.storage;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ItemStackKey {

    private static final String KEY_ITEM = "item";
    private static final String KEY_DAMAGE = "damage";
    private static final String KEY_TAG = "tag";
    private static final String KEY_AMOUNT = "amount";

    private final String itemName;
    private final int damage;
    private final NbtKey tag;

    // ===== 3.25.0-fix18（性能）：把"每次调用都重算"的东西缓存下来 =====
    // 3.35.0-fix1（审查 P1-10）：所有缓存字段加 volatile —— key 会被两个线程共享
    // （服务端 tick 写账本、客户端 NEI 读），旧的非 volatile 记忆化在跨线程下可读到
    // "hashCached=true 但 cachedHash 仍为 0"的撕裂状态（代码保证算出的 hash 永不为 0，
    // 所以返回 0 即撕裂自证）⇒ 同一物品在账本里记成两笔、"东西在但拿不出"。
    // uid/item 缓存同理（toStack 返回 null → handler 把 null 哨兵永久写入 ⇒ 物品从
    // ME 网络列表消失）。volatile 保证"先写值后置标志"的顺序对读线程可见。

    /** `itemName` 拆分结果（`new GameRegistry.UniqueIdentifier` 内部会 String.split ⇒ 不能每次 toStack 都做）。 */
    private volatile boolean uidResolved;
    private volatile String uidModId;
    private volatile String uidName;

    /** 解析出的 Item 引用（`GameRegistry.findItem` 是注册表反查，不能每次 toStack 都做）。 */
    private volatile boolean itemResolved;
    private volatile net.minecraft.item.Item cachedItem;

    /** 缓存的 hashCode（原 `Objects.hash` 每次装箱 3 个值 + 分配 Object[]）。 */
    private volatile int cachedHash;
    private volatile boolean hashCached;

    /** `Item → "modid:name"` 的进程内缓存（入站方向 `from()` 每次都会 `findUniqueIdentifierFor` + `toString`）。 */
    private static final java.util.Map<net.minecraft.item.Item, String> NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean resolveUid() {
        if (uidResolved) {
            return uidModId != null;
        }
        uidResolved = true;
        try {
            GameRegistry.UniqueIdentifier id = new GameRegistry.UniqueIdentifier(itemName);
            uidModId = id.modId;
            uidName = id.name;
        } catch (RuntimeException ignored) {
            uidModId = null;
            uidName = null;
        }
        return uidModId != null;
    }

    private net.minecraft.item.Item item() {
        if (!itemResolved) {
            itemResolved = true;
            cachedItem = resolveUid() ? GameRegistry.findItem(uidModId, uidName) : null;
        }
        return cachedItem;
    }

    private ItemStackKey(String itemName, int damage, NbtKey tag) {
        this.itemName = itemName;
        this.damage = damage;
        this.tag = tag;
        // 3.35.0-fix1（审查 P1-10）：hashCode 在构造期一次算完并先于发布写出 ——
        // key 一旦进入任何 map/跨线程，hash 已是确定值；volatile 字段保证可见性。
        // 这让"撕裂读 0"在构造期就封死（本类保证 hash != 0，见 hashCode）。
        int h = itemName.hashCode();
        h = 31 * h + damage;
        h = 31 * h + tag.hashCode();
        if (h == 0) {
            h = 1; // 避免与"未计算"混淆（用 hashCached 标记，这里只是保守处理）
        }
        this.cachedHash = h;
        this.hashCached = true;
    }

    public static ItemStackKey from(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException("stack");
        }

        // 3.25.0-fix18：入站方向每次 AE 操作都会走这里 ⇒ `modid:name` 字符串按 Item 缓存，别再每次 toString()
        String name = NAME_CACHE.get(stack.getItem());
        if (name == null) {
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            if (id == null) {
                throw new IllegalArgumentException("unregistered item " + stack.getItem());
            }
            name = id.toString();
            NAME_CACHE.put(stack.getItem(), name);
        }
        return new ItemStackKey(name, stack.getItemDamage(), NbtKey.of(stack.stackTagCompound));
    }

    public static ItemStackKey readFromNBT(NBTTagCompound serialized) {
        String itemName = serialized.getString(KEY_ITEM);
        int damage = serialized.getInteger(KEY_DAMAGE);
        NbtKey tag = serialized.hasKey(KEY_TAG, 10) ? NbtKey.of(serialized.getCompoundTag(KEY_TAG)) : NbtKey.NONE;
        return new ItemStackKey(itemName, damage, tag);
    }

    public NBTTagCompound writeToNBT(long amount) {
        NBTTagCompound serialized = new NBTTagCompound();
        serialized.setString(KEY_ITEM, itemName);
        serialized.setInteger(KEY_DAMAGE, damage);
        if (!tag.isEmpty()) {
            serialized.setTag(KEY_TAG, tag.copyTag());
        }
        serialized.setString(KEY_AMOUNT, Long.toString(amount));
        return serialized;
    }

    public ItemStack toStack(long amount) {
        // 3.25.0-fix18：拆名与注册表反查都只做一次（原来是每次调用都做）
        net.minecraft.item.Item item = item();
        if (item == null) {
            return null;
        }
        ItemStack stack = new ItemStack(item, saturatedInt(amount), damage);
        if (!tag.isEmpty()) {
            stack.stackTagCompound = tag.copyTag();
        }
        return stack;
    }

    public String getItemName() {
        return itemName;
    }

    public int getDamage() {
        return damage;
    }

    public NbtKey getTag() {
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemStackKey)) {
            return false;
        }
        ItemStackKey that = (ItemStackKey) o;
        return damage == that.damage && Objects.equals(itemName, that.itemName) && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        // 3.25.0-fix18：缓存 + 手写（原 Objects.hash 每次装箱 3 个值并分配 Object[]；
        // 这个 hashCode 在每次 map 查找/枚举里都会被调用）
        int h = cachedHash;
        if (!hashCached) {
            h = itemName.hashCode();
            h = 31 * h + damage;
            h = 31 * h + tag.hashCode();
            if (h == 0) {
                h = 1; // 避免与"未计算"混淆（用 hashCached 标记，这里只是保守处理）
            }
            cachedHash = h;
            hashCached = true;
        }
        return h;
    }

    @Override
    public String toString() {
        return "ItemStackKey{" + itemName + ':' + damage + ", tag=" + tag + '}';
    }

    private static int saturatedInt(long amount) {
        if (amount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (amount < 0L) {
            return 0;
        }
        return (int) amount;
    }
}
