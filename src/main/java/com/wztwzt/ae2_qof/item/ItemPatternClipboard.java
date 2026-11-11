package com.wztwzt.ae2_qof.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.AE2QoLCreativeTab;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.ph.PatternClipboardGui;
import com.wztwzt.ae2_qof.ph.PatternHostAdapter;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 样板剪贴板（3.24.0；4.3.0 重做操作逻辑 + 全家族适配 + GUI）。
 *
 * <h2>4.3.0 新用法</h2>
 * <ul>
 * <li><b>右键空中</b>：打开管理 GUI（选模式 / 预览剪贴板内容 / 清空）；</li>
 * <li><b>潜行 + 右键空中</b>：聊天栏报告剪贴板状态（张数 / 来源 / 时间）；</li>
 * <li><b>右键样板总成</b>：按当前模式执行。四种模式：
 * 复制 / 粘贴（覆盖，目标非空或需截断时<b>要再点一次确认</b>）/ 剪切 / 追加粘贴（只填空槽，绝不覆盖）。</li>
 * </ul>
 *
 * <h2>适配范围（4.3.0 起）</h2>
 * 经 {@link PatternHostAdapter} 支持整个 PH 样板总成家族：PH 原版总成（含 MK.II / 仅物品变体）、
 * 超级样板总成（Super）、库存映射从属机（MappingSlave），以及本模组的 MK.III / MK.IV。
 * 旧版只认 MK.III / MK.IV 的行为已废弃。
 *
 * <h2>为什么全部逻辑都在服务端、且不新增网络包</h2>
 * 1.7.10 里右键**方块**走服务端 {@code onItemUse}，右键**空中**走 {@code onItemRightClick}。
 * "点哪台机器"由服务端按坐标解析，样板数据不经过网络 ⇒ 避开 1.7.10 单包上限
 * （360 张样板的 NBT 上百 KB），也不需要额外通道。GUI 走 MUI2（服务端权威，专用服可用）。
 *
 * <h2>剪贴板存哪</h2>
 * 玩家存档 {@code PlayerPersisted} 下的 {@value #CLIP_ROOT} 子键 ⇒ 跨维度、退出重进都在。
 * 内容与 PH 存档同格式（{@code patternSlots.i0..iN} + {@code multiplier}），键名沿用 3.24.0。
 *
 * <h2>覆盖粘贴的二次确认</h2>
 * 覆盖会清掉目标已有样板、截断会丢超出槽位的样板 —— 这两件事先黄字提示（含数量），
 * 同一台机器 10 秒内再点一次才真正执行；追加粘贴无破坏性，不需要确认。
 *
 * <h2>本类只在装了 ProgrammableHatches 时才会被加载</h2>
 * 注册入口在 {@code PhIntegration.register()}（PH 守卫之后），引用的 PH 类型不会在缺 PH 的环境被解析。
 */
public class ItemPatternClipboard extends Item {

    /** 模式：复制（只读取源）。 */
    public static final int MODE_COPY = 0;
    /** 模式：粘贴-覆盖（清空目标后写入；3.24.0 的旧粘贴语义，NBT 值兼容）。 */
    public static final int MODE_PASTE = 1;
    /** 模式：剪切（读取 + 清空源）。 */
    public static final int MODE_CUT = 2;
    /** 模式：粘贴-追加（只填目标的空槽，绝不覆盖已有样板；4.3.0 新增）。 */
    public static final int MODE_PASTE_APPEND = 3;

    public static final int MODE_COUNT = 4;

    /** 物品堆上记录当前模式的键。 */
    private static final String NBT_MODE = "ae2qolClipMode";

    /** 玩家存档里的剪贴板根键。 */
    public static final String CLIP_ROOT = "ae2qolPatternClipboard";
    private static final String CLIP_PATTERNS = "patternSlots";
    private static final String CLIP_MULTIPLIER = "multiplier";
    private static final String CLIP_COUNT = "count";
    private static final String CLIP_SLOTS = "slots";
    private static final String CLIP_TIME = "time";
    private static final String CLIP_SRC = "source";

    /** 覆盖粘贴的确认窗口（毫秒）。 */
    private static final long CONFIRM_WINDOW_MS = 10_000L;

    private static final String LANG = "ae2_qof.clipboard.";

    /** 覆盖粘贴的待确认状态：玩家 → (维度, x, y, z, 到期毫秒)。 */
    private static final Map<UUID, long[]> PENDING_OVERWRITE = new HashMap<>();

    public ItemPatternClipboard() {
        setUnlocalizedName("ae2_qof.pattern_clipboard");
        // 引用 AE2 自己的贴图（只按名引用、不复制素材），与本模组通配样板同一做法
        setTextureName("appliedenergistics2:ItemEncodedPattern");
        setCreativeTab(AE2QoLCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    public ItemPatternClipboard register() {
        GameRegistry.registerItem(this, "pattern_clipboard", MyMod.MODID);
        return this;
    }

    /**
     * 紫色染色（3.24.0-fix1）：贴图本体仍是 AE2 原版编码样板（运行时按名引用、不分发其素材），
     * 由 1.7.10 的 ItemRenderer 用这里返回的颜色相乘着色。
     * 通配样板 = 绿、批量生成器 = 青，本物品用**紫** {@code 0xC77DFF} 与之区分。
     */
    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return 0xC77DFF;
    }

    // ===================== 右键空中：GUI / 状态查询 =====================

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        if (player.isSneaking()) {
            reportStatus(player);
            return stack;
        }
        // 4.3.0：右键空中从「循环切模式」改为打开管理 GUI（模式在 GUI 里选，杜绝误循环）
        PatternClipboardGui.open(player, stack);
        return stack;
    }

    // ===================== 右键机器：执行当前模式 =====================

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return false; // 只在服务端做事；非样板总成也不拦截原版交互

        PatternHostAdapter.Host host = PatternHostAdapter.resolveHost(world.getTileEntity(x, y, z));
        if (host == null) {
            return false;
        }

        int mode = getMode(stack);
        if (mode == MODE_PASTE) {
            doPaste(player, host, x, y, z, true);
        } else if (mode == MODE_PASTE_APPEND) {
            doPaste(player, host, x, y, z, false);
        } else if (mode == MODE_CUT) {
            doCopy(player, host, x, y, z, true);
        } else if (mode == MODE_COPY) {
            doCopy(player, host, x, y, z, false);
        }
        return true;
    }

    // ===================== 复制 / 剪切 =====================

    /** 4.5.0-fix18：事件拦截层入口（PatternClipboardInterceptHandler 调用）。 */
    public static void doCopyIntercepted(EntityPlayer player, PatternHostAdapter.Host host, int x, int y, int z,
        boolean clearSource) {
        doCopy(player, host, x, y, z, clearSource);
    }

    /** 4.5.0-fix18：事件拦截层入口（PatternClipboardInterceptHandler 调用）。 */
    public static void doPasteIntercepted(EntityPlayer player, PatternHostAdapter.Host host, int x, int y, int z,
        boolean overwrite) {
        doPaste(player, host, x, y, z, overwrite);
    }

    private static void doCopy(EntityPlayer player, PatternHostAdapter.Host host, int x, int y, int z,
        boolean clearSource) {
        ItemStack[] patterns = host.readPatterns();
        if (patterns == null) {
            MyMod.LOG.warn("[AE2QoL] 样板剪贴板：机器 {} 的样板数组为 null，取消本次操作", host.hostName());
            tell(player, EnumChatFormatting.RED + t(LANG + "copy_failed"));
            return;
        }
        int[] multiplier = host.readMultiplier();

        NBTTagCompound clip = new NBTTagCompound();
        NBTTagCompound tag = new NBTTagCompound();
        int count = 0;
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] != null) {
                tag.setTag("i" + i, patterns[i].writeToNBT(new NBTTagCompound()));
                count++;
            }
        }
        clip.setTag(CLIP_PATTERNS, tag);
        clip.setIntArray(CLIP_MULTIPLIER, multiplier != null ? multiplier : new int[0]);
        clip.setInteger(CLIP_COUNT, count);
        clip.setInteger(CLIP_SLOTS, patterns.length);
        clip.setLong(CLIP_TIME, System.currentTimeMillis());
        clip.setString(CLIP_SRC, player.dimension + " @ " + x + "," + y + "," + z);

        // 先落盘到玩家存档，**成功之后**才允许清空源（剪切的唯一风险点）
        clipboard(player).setTag(CLIP_ROOT, clip);

        String mode = clearSource ? t(LANG + "cut") : t(LANG + "copied");
        tell(player, EnumChatFormatting.GREEN + mode + " " + format(count, patterns.length));

        if (clearSource) {
            int slots = patterns.length;
            int[] ones = new int[slots];
            java.util.Arrays.fill(ones, 1);
            host.writeAll(new ItemStack[slots], ones);
            MyMod.LOG.info(
                "[AE2QoL] 样板剪贴板：剪切 {} 张样板（源 {} 已清空，玩家={}）",
                count,
                clip.getString(CLIP_SRC),
                player.getCommandSenderName());
        } else {
            MyMod.LOG.info(
                "[AE2QoL] 样板剪贴板：复制 {} 张样板（源 {}，玩家={}）",
                count,
                clip.getString(CLIP_SRC),
                player.getCommandSenderName());
        }
    }

    // ===================== 粘贴（覆盖 / 追加） =====================

    private static void doPaste(EntityPlayer player, PatternHostAdapter.Host host, int x, int y, int z,
        boolean overwrite) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        if (!clip.hasKey(CLIP_PATTERNS)) {
            tell(player, EnumChatFormatting.YELLOW + t(LANG + "empty"));
            return;
        }
        ItemStack[] current = host.readPatterns();
        int target = current != null ? current.length : 0;
        if (target <= 0) {
            tell(player, EnumChatFormatting.RED + t(LANG + "paste_failed"));
            return;
        }

        NBTTagCompound tag = clip.getCompoundTag(CLIP_PATTERNS);
        int clipSlots = clip.getInteger(CLIP_SLOTS);
        int clipCount = clip.getInteger(CLIP_COUNT);
        int droppable = Math.max(0, clipSlots - target); // 超出目标槽位、必被丢弃的槽数

        if (overwrite) {
            int existing = 0;
            for (ItemStack s : current) {
                if (s != null) existing++;
            }
            // 破坏性操作二次确认：目标有货或要截断时，第一次只提示，同一台机 10 秒内再点才执行
            if ((existing > 0 || droppable > 0) && !consumeOverwriteConfirm(player, x, y, z)) {
                StringBuilder warn = new StringBuilder(EnumChatFormatting.YELLOW + t(LANG + "confirm_overwrite") + " ");
                if (existing > 0) {
                    warn.append(EnumChatFormatting.RED)
                        .append(StatCollector.translateToLocalFormatted(LANG + "confirm_will_clear", existing));
                }
                if (droppable > 0) {
                    warn.append(EnumChatFormatting.RED)
                        .append(" ")
                        .append(StatCollector.translateToLocalFormatted(LANG + "confirm_will_drop", droppable));
                }
                tell(player, warn.toString());
                return;
            }

            ItemStack[] patterns = new ItemStack[target];
            int count = 0;
            for (int i = 0; i < target; i++) {
                if (tag.hasKey("i" + i)) {
                    patterns[i] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("i" + i));
                    if (patterns[i] != null) count++;
                }
            }
            host.writeAll(patterns, buildMultiplier(clip, target));
            tell(player, EnumChatFormatting.GREEN + t(LANG + "pasted") + " " + format(count, target));
            MyMod.LOG.info(
                "[AE2QoL] 样板剪贴板：覆盖粘贴 {} 张到 {}（目标槽位={}，来源={}，玩家={}）",
                count,
                host.hostName(),
                target,
                clip.getString(CLIP_SRC),
                player.getCommandSenderName());
            return;
        }

        // 追加：按槽位号对齐，只填空槽；目标已有样板一格不动
        ItemStack[] merged = new ItemStack[target];
        int placed = 0, occupied = 0;
        for (int i = 0; i < target; i++) {
            merged[i] = current[i];
            if (tag.hasKey("i" + i)) {
                if (current[i] == null) {
                    merged[i] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("i" + i));
                    if (merged[i] != null) placed++;
                } else {
                    occupied++;
                }
            }
        }
        if (placed == 0) {
            tell(player, EnumChatFormatting.YELLOW + t(LANG + "append_none"));
            return;
        }
        int[] srcMul = clip.getIntArray(CLIP_MULTIPLIER);
        int[] mul = new int[target];
        for (int i = 0; i < target; i++) {
            mul[i] = current[i] == null && srcMul != null && i < srcMul.length && srcMul[i] > 0 ? srcMul[i] : 1;
        }
        host.writeAll(merged, mul);
        tell(
            player,
            EnumChatFormatting.GREEN
                + StatCollector.translateToLocalFormatted(LANG + "appended", placed, occupied, droppable));
        MyMod.LOG.info(
            "[AE2QoL] 样板剪贴板：追加粘贴 {} 张到 {}（跳过已占 {} 格，来源={}，玩家={}）",
            placed,
            host.hostName(),
            occupied,
            clip.getString(CLIP_SRC),
            player.getCommandSenderName());
    }

    /** 覆盖确认：首次点击记录目标并返回 false；10 秒内对**同一台机器**再点返回 true。 */
    private static boolean consumeOverwriteConfirm(EntityPlayer player, int x, int y, int z) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueID();
        long[] pending = PENDING_OVERWRITE.get(id);
        if (pending != null && now <= pending[4]
            && pending[0] == player.dimension
            && pending[1] == x
            && pending[2] == y
            && pending[3] == z) {
            PENDING_OVERWRITE.remove(id);
            return true;
        }
        PENDING_OVERWRITE.put(id, new long[] { player.dimension, x, y, z, now + CONFIRM_WINDOW_MS });
        // 顺手清掉过期项，map 有界于在线玩家数
        PENDING_OVERWRITE.values()
            .removeIf(p -> now > p[4]);
        return false;
    }

    /** 目标倍率数组：剪贴板值取有效下标，缺失/非法一律补 1。 */
    private static int[] buildMultiplier(NBTTagCompound clip, int target) {
        int[] srcMul = clip.getIntArray(CLIP_MULTIPLIER);
        int[] multiplier = new int[target];
        java.util.Arrays.fill(multiplier, 1);
        for (int i = 0; i < Math.min(srcMul.length, target); i++) {
            multiplier[i] = Math.max(srcMul[i], 1);
        }
        return multiplier;
    }

    // ===================== 状态 / GUI 支撑 =====================

    private static void reportStatus(EntityPlayer player) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        if (!clip.hasKey(CLIP_PATTERNS)) {
            tell(player, EnumChatFormatting.YELLOW + t(LANG + "empty"));
            return;
        }
        tell(
            player,
            EnumChatFormatting.AQUA + t(LANG + "status")
                + " "
                + format(clip.getInteger(CLIP_COUNT), clip.getInteger(CLIP_SLOTS))
                + EnumChatFormatting.GRAY
                + " | "
                + clip.getString(CLIP_SRC));
    }

    /** GUI 用：剪贴板概要行（无数据给出提示）。 */
    public static String clipboardSummary(EntityPlayer player) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        if (!clip.hasKey(CLIP_PATTERNS)) return t(LANG + "empty");
        return format(clip.getInteger(CLIP_COUNT), clip.getInteger(CLIP_SLOTS)) + EnumChatFormatting.GRAY
            + " | "
            + clip.getString(CLIP_SRC);
    }

    /** GUI 用：第 page 页（每页 {@code pageSize} 行）第 row 行的预览文本；无内容返回空串。 */
    public static String clipboardRow(EntityPlayer player, int page, int pageSize, int row) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        if (!clip.hasKey(CLIP_PATTERNS)) return "";
        NBTTagCompound tag = clip.getCompoundTag(CLIP_PATTERNS);
        int slots = clip.getInteger(CLIP_SLOTS);
        int index = page * pageSize + row;
        if (index >= slots) return "";
        if (!tag.hasKey("i" + index)) return EnumChatFormatting.DARK_GRAY + "#" + (index + 1);
        ItemStack stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("i" + index));
        if (stack == null) return EnumChatFormatting.DARK_GRAY + "#" + (index + 1);
        int[] mul = clip.getIntArray(CLIP_MULTIPLIER);
        int m = index < mul.length ? mul[index] : 1;
        return EnumChatFormatting.WHITE + "#"
            + (index + 1)
            + " "
            + stack.getDisplayName()
            + (m > 1 ? EnumChatFormatting.AQUA + " x" + m : "");
    }

    /** GUI 用：剪贴板总页数（至少 1，便于翻页按钮取模）。 */
    public static int clipboardPages(EntityPlayer player, int pageSize) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        int slots = clip.hasKey(CLIP_PATTERNS) ? clip.getInteger(CLIP_SLOTS) : 0;
        return Math.max(1, (slots + pageSize - 1) / pageSize);
    }

    /** GUI 用：清空剪贴板。 */
    public static void clearClipboard(EntityPlayer player) {
        clipboard(player).removeTag(CLIP_ROOT);
    }

    // ===================== 模式（GUI 与物品共用） =====================

    public static int getMode(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) return MODE_COPY;
        int mode = stack.getTagCompound()
            .getInteger(NBT_MODE);
        return (mode >= 0 && mode < MODE_COUNT) ? mode : MODE_COPY;
    }

    public static void setMode(ItemStack stack, int mode) {
        if (stack == null || mode < 0 || mode >= MODE_COUNT) return;
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBT_MODE, mode);
    }

    public static String modeName(int mode) {
        if (mode == MODE_PASTE) return t(LANG + "mode.paste");
        if (mode == MODE_CUT) return t(LANG + "mode.cut");
        if (mode == MODE_PASTE_APPEND) return t(LANG + "mode.append");
        return t(LANG + "mode.copy");
    }

    // ===================== 工具 =====================

    public static NBTTagCompound clipboard(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    private static String t(String key) {
        return StatCollector.translateToLocal(key);
    }

    /** 统一格式：「N 张 / 共 M 槽」。 */
    private static String format(int count, int slots) {
        return StatCollector.translateToLocalFormatted(LANG + "amount", count, slots);
    }

    private static void tell(EntityPlayer player, String message) {
        player.addChatMessage(new net.minecraft.util.ChatComponentText(message));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GRAY + t(LANG + "tooltip.0"));
        tooltip.add(EnumChatFormatting.GRAY + t(LANG + "tooltip.1"));
        tooltip.add(EnumChatFormatting.GRAY + t(LANG + "tooltip.2"));
        tooltip.add(EnumChatFormatting.GRAY + t(LANG + "tooltip.3"));
        tooltip
            .add(EnumChatFormatting.DARK_GRAY + t(LANG + "mode") + EnumChatFormatting.WHITE + modeName(getMode(stack)));
    }
}
