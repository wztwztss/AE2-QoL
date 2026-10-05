package com.wztwzt.ae2_qof.item;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.AE2QoLCreativeTab;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchAccess;
import com.wztwzt.ae2_qof.ph.MTEPatternCraftingBufferMKIII;
import com.wztwzt.ae2_qof.ph.MTEPatternCraftingBufferMKIV;
import com.wztwzt.ae2_qof.ph.PatternClientSync;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 样板剪贴板（3.24.0）：把一台样板总成（MK.III / MK.IV）里的**全部样板 + 每格倍率**取出到玩家自己的
 * 剪贴板，再粘贴到另一台同族机器上。用途就是"144 → 360 搬家"与备份。
 *
 * <h2>用法</h2>
 * <ul>
 * <li><b>右键空中</b>：循环切换模式（复制 → 粘贴 → 剪切 → 复制…），聊天栏提示当前模式；</li>
 * <li><b>潜行 + 右键空中</b>：报告剪贴板状态（张数 / 来源 / 时间）；</li>
 * <li><b>右键机器</b>：按当前模式执行。</li>
 * </ul>
 *
 * <h2>为什么全部逻辑都在服务端、且不新增网络包</h2>
 * 1.7.10 里右键**方块**会走服务端的 {@code ItemInWorldManager.tryUseItem → onItemUse}，右键**空中**会走
 * {@code onItemRightClick}（两端都会调，所以用 {@code world.isRemote} 只让服务端改状态）。
 * 因此"点哪台机器"由服务端直接按坐标解析，**样板数据根本不经过网络** ⇒ 天然避开 1.7.10 的单包上限
 * （360 张样板的 NBT 有上百 KB，塞进一个包必炸），也不需要新增通道/包。
 *
 * <h2>剪贴板存哪</h2>
 * 玩家存档的 {@code PlayerPersisted}（{@code EntityPlayer.getEntityData()}）下的
 * {@value #CLIP_ROOT} 子键 ⇒ **跨维度、退出重进都还在**，可作为"临时备份"用。
 * 内容与 PH/我们的存读档同格式（{@code patternSlots} 的 {@code i0..iN} + {@code multiplier}），
 * 便于将来导出/导入。
 *
 * <h2>剪切的安全性</h2>
 * 剪切 = "读取 → 确认已写入剪贴板 → 再把源机器清空"；写剪贴板失败时**不动源机器**，并打 WARN。
 *
 * <h2>本类只在装了 ProgrammableHatches 时才会被加载</h2>
 * 注册入口在 {@code PhIntegration.register()}（PH 守卫之后），因此本类引用的 PH 类型不会在缺 PH 的整合包里
 * 被解析 —— 与 {@code MTEPatternCraftingBufferMKIII/MKIV} 同一套纪律。
 */
public class ItemPatternClipboard extends Item {

    /** 模式：复制（只读取源）。 */
    public static final int MODE_COPY = 0;
    /** 模式：粘贴（写入目标）。 */
    public static final int MODE_PASTE = 1;
    /** 模式：剪切（读取 + 清空源）。 */
    public static final int MODE_CUT = 2;

    private static final int MODE_COUNT = 3;

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

    private static final String LANG = "ae2_qof.clipboard.";

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
     * 取值依据：通配样板 = 绿 {@code 0x5CE65C}、批量生成器 = 青 {@code 0x5CE6E6}，
     * 本物品用**紫** {@code 0xC77DFF} 与之区分（否则三者与真样板图标无法一眼分开）。
     */
    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return 0xC77DFF;
    }

    // ===================== 模式切换 / 状态查询 =====================

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        if (player.isSneaking()) {
            reportStatus(player);
            return stack;
        }
        int mode = (getMode(stack) + 1) % MODE_COUNT;
        setMode(stack, mode);
        tell(player, EnumChatFormatting.AQUA + t(LANG + "mode") + EnumChatFormatting.WHITE + modeName(mode));
        return stack;
    }

    // ===================== 右键机器：执行当前模式 =====================

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true; // 只在服务端做事

        PatternDualInputHatch machine = resolveOurMachine(world, x, y, z);
        if (machine == null) {
            tell(
                player,
                EnumChatFormatting.RED + t(LANG + "not_ours"));
            return true;
        }

        int mode = getMode(stack);
        if (mode == MODE_PASTE) {
            doPaste(player, machine);
        } else if (mode == MODE_CUT) {
            doCopy(player, machine, x, y, z, true);
        } else if (mode == MODE_COPY) {
            doCopy(player, machine, x, y, z, false);
        } else {
            tell(player, EnumChatFormatting.RED + t(LANG + "bad_mode"));
        }
        return true;
    }

    /** 只接受"我们的"样板总成（MK.III / MK.IV），避免误伤 PH 自己的机器与其它模组。 */
    private static PatternDualInputHatch resolveOurMachine(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity greg)) return null;
        IMetaTileEntity mte = greg.getMetaTileEntity();
        if (!(mte instanceof PatternDualInputHatch pdih)) return null;
        // 用 instanceof 而不是类名字符串：编译期就能保证覆盖 MK.III/MK.IV
        if (mte instanceof MTEPatternCraftingBufferMKIII || mte instanceof MTEPatternCraftingBufferMKIV) {
            return pdih;
        }
        return null;
    }

    // ===================== 复制 / 剪切 =====================

    private static void doCopy(EntityPlayer player, PatternDualInputHatch machine, int x, int y, int z,
        boolean clearSource) {
        MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) machine;
        ItemStack[] patterns = acc.getAe2qolPattern();
        if (patterns == null) {
            MyMod.LOG.warn("[AE2QoL] 样板剪贴板：机器 {} 的样板数组为 null，取消本次操作", machine);
            tell(player, EnumChatFormatting.RED + t(LANG + "copy_failed"));
            return;
        }
        int[] multiplier = acc.getAe2qolMultiplier();

        NBTTagCompound clip = new NBTTagCompound();
        NBTTagCompound tag = new NBTTagCompound();
        int count = 0;
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] != null) {
                tag.setTag(
                    "i" + i,
                    patterns[i].writeToNBT(new NBTTagCompound()));
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
            clearMachine(acc, patterns.length);
            machine.refresh();
            // 3.24.0-fix2：把改动后的 TE 数据推给跟踪该区块的玩家。
            // 不做这一步，客户端那份 pattern[]（样板窗渲染用的就是它）会一直停在旧值，
            // 只有区块重载才会更新 —— 用户实测"剪切后格子不立刻消失"就是这个原因。
            PatternClientSync.notifyClients(machine);
            MyMod.LOG.info(
                "[AE2QoL] 样板剪贴板：剪切 {} 张样板（源 {} 已清空，玩家={}）",
                count,
                clip.getString(CLIP_SRC),
                player.getCommandSenderName());
        } else {
            // 复制只改剪贴板、不改机器 ⇒ 无需下发 TE 数据（机器状态本来就没变）
            MyMod.LOG.info(
                "[AE2QoL] 样板剪贴板：复制 {} 张样板（源 {}，玩家={}）",
                count,
                clip.getString(CLIP_SRC),
                player.getCommandSenderName());
        }
    }

    private static void clearMachine(MixinPatternDualInputHatchAccess acc, int slots) {
        acc.setAe2qolPattern(new ItemStack[slots]);
        int[] multiplier = new int[slots];
        Arrays.fill(multiplier, 1);
        acc.setAe2qolMultiplier(multiplier);
        acc.setAe2qolPatternItemCache(new ItemStack[slots]);
        acc.setAe2qolPatternDetailCache(new ICraftingPatternDetails[slots]);
        acc.invokeAe2qolOnPatternChange();
    }

    // ===================== 粘贴 =====================

    private static void doPaste(EntityPlayer player, PatternDualInputHatch machine) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        if (!clip.hasKey(CLIP_PATTERNS)) {
            tell(player, EnumChatFormatting.YELLOW + t(LANG + "empty"));
            return;
        }
        MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) machine;
        ItemStack[] current = acc.getAe2qolPattern();
        int target = current != null ? current.length : 0;
        if (target <= 0) {
            tell(player, EnumChatFormatting.RED + t(LANG + "paste_failed"));
            return;
        }

        NBTTagCompound tag = clip.getCompoundTag(CLIP_PATTERNS);
        ItemStack[] patterns = new ItemStack[target];
        int count = 0;
        for (int i = 0; i < target; i++) {
            if (tag.hasKey("i" + i)) {
                patterns[i] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("i" + i));
                if (patterns[i] != null) count++;
            }
        }
        int[] srcMultiplier = clip.getIntArray(CLIP_MULTIPLIER);
        int[] multiplier = new int[target];
        Arrays.fill(multiplier, 1);
        if (srcMultiplier != null) {
            for (int i = 0; i < Math.min(srcMultiplier.length, target); i++) {
                multiplier[i] = Math.max(srcMultiplier[i], 1);
            }
        }

        acc.setAe2qolPattern(patterns);
        acc.setAe2qolMultiplier(multiplier);
        acc.setAe2qolPatternItemCache(new ItemStack[target]);
        acc.setAe2qolPatternDetailCache(new ICraftingPatternDetails[target]);
        acc.invokeAe2qolOnPatternChange();
        machine.refresh();
        // 3.24.0-fix2：同上，把新的样板下发到目标机器所在区块的客户端
        PatternClientSync.notifyClients(machine);

        int clipSlots = clip.getInteger(CLIP_SLOTS);
        tell(player, EnumChatFormatting.GREEN + t(LANG + "pasted") + " " + format(count, target));
        if (clipSlots > target) {
            // 剪贴板比目标大（例如 360 → 144）：只搬得走前 target 张，必须显式告知，不能静默截断
            tell(
                player,
                EnumChatFormatting.YELLOW + StatCollector
                    .translateToLocalFormatted(LANG + "truncated", clip.getInteger(CLIP_COUNT), target));
        }
        MyMod.LOG.info(
            "[AE2QoL] 样板剪贴板：粘贴 {} 张样板到目标（目标槽位={}，剪贴板来源={}，玩家={}）",
            count,
            target,
            clip.getString(CLIP_SRC),
            player.getCommandSenderName());
    }

    // ===================== 状态 =====================

    private static void reportStatus(EntityPlayer player) {
        NBTTagCompound clip = clipboard(player).getCompoundTag(CLIP_ROOT);
        if (!clip.hasKey(CLIP_PATTERNS)) {
            tell(player, EnumChatFormatting.YELLOW + t(LANG + "empty"));
            return;
        }
        tell(
            player,
            EnumChatFormatting.AQUA + t(LANG + "status") + " "
                + format(clip.getInteger(CLIP_COUNT), clip.getInteger(CLIP_SLOTS))
                + EnumChatFormatting.GRAY
                + " | "
                + clip.getString(CLIP_SRC));
    }

    // ===================== 工具 =====================

    private static NBTTagCompound clipboard(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    private static int getMode(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) return MODE_COPY;
        int mode = stack.getTagCompound()
            .getInteger(NBT_MODE);
        return (mode >= 0 && mode < MODE_COUNT) ? mode : MODE_COPY;
    }

    private static void setMode(ItemStack stack, int mode) {
        if (stack == null) return;
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBT_MODE, mode);
    }

    private static String modeName(int mode) {
        if (mode == MODE_PASTE) return t(LANG + "mode.paste");
        if (mode == MODE_CUT) return t(LANG + "mode.cut");
        return t(LANG + "mode.copy");
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
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + t(LANG + "mode")
                + EnumChatFormatting.WHITE
                + modeName(getMode(stack)));
    }
}
