package com.wztwzt.ae2_qof.ph;

import java.util.Arrays;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchAccess;
import com.wztwzt.ae2_qof.util.CountFormatter;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.core.Api;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.util.PatternSlot;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 编程样板输入总成 MK.III —— Programmable-Hatches「编程样板输入总成」的**扩容克隆版**。
 *
 * <p>它只在安装了 Programmable-Hatches 时存在：本类的父类、以及所有 PH 类型都只被
 * {@link PhIntegration#register()} 的方法体引用，PH 缺失时该入口直接 return，本类不会被加载。
 *
 * <p>与原型的差别只有「样板槽数量」和随之而来的样板窗形状：
 * <ul>
 * <li>样板槽 **144**（原型 36 = PH 写死的数组长度），对应 AE2 接口终端 16 行 × 9 列；</li>
 * <li>样板窗改成 9 列 × 9 可见行的**可滚动网格**（滚动覆盖 16 行），并加了按屏幕裁剪的停靠逻辑；</li>
 * <li>输入结构与 MK.II 一致（{@code page() == 2}：每缓冲 32 物品 + 32 流体、24 个隔离缓冲）；</li>
 * <li>其余（AE 频道/收单/缓冲隔离/电路处理/倍率/退款/Waila/NBT）**全部继承 PH 原实现**。</li>
 * </ul>
 *
 * <p>容量替换的关键证据与坑位见 {@link MixinPatternDualInputHatchAccess} 与
 * {@link #ae2qol$ensureSlots()} / {@link #loadNBTData(NBTTagCompound)} 的注释。
 */
public class MTEPatternCraftingBufferMKIII extends PatternDualInputHatch {

    /** GT MetaTileEntity ID。32108 已在 reference_src_290b3 全表核对过未被占用（备选 32109）。 */
    public static final int MTE_ID = 32108;

    /** MTE 内部名：同时决定显示名 lang 键 {@code gt.blockmachines.<mName>.name}。 */
    public static final String MTE_NAME = "ae2qof.hatch.input.buffered.me.mkiii";

    /** 本机所有 lang 键的前缀（GT 的 {@code getLocalNameKey()} 用的是同一套前缀）。 */
    private static final String AE2QOL_LANG_PREFIX = "gt.blockmachines." + MTE_NAME + ".";

    /** 样板槽总数 = 36 的 4 倍。 */
    public static final int PATTERN_SLOTS = 144;

    /**
     * 样板窗网格列数。同时是 {@link #rowSize()}：
     * AE2 接口终端 {@code GuiInterfaceTerminal.VIEW_WIDTH = 174}，只能放下 9 列，超过会被横向裁掉。
     */
    public static final int GRID_COLS = 9;

    /** 样板窗同时可见的行数（其余靠滚动）。9 行 = 162px，在最保守的 480×270 逻辑分辨率下也放得下。 */
    public static final int VISIBLE_ROWS = 9;

    /** 样板窗滚动区总行数 = 144 / 9 = 16。 */
    public static final int TOTAL_ROWS = PATTERN_SLOTS / GRID_COLS;

    /** 每个缓冲区的输入槽组数量，与 MK.II 一致（page=2 → 32 物品 + 32 流体）。 */
    public static final int INPUT_PAGE = 2;

    public static final int BUFFER_NUM = 24;
    public static final int TIER = 10;

    /**
     * PH 的 {@code bufferNum} 是包私有字段，跨包读不到；两个构造器都收了这个参数，所以自己存一份，
     * {@link #newMetaEntity(IGregTechTileEntity)} 用它构造真实实例。
     */
    private final int ae2qol$bufferNum;

    // ===================== 构造器 =====================

    /** 注册用（模板实例）构造器，签名与 PH 的注册写法一致。 */
    public MTEPatternCraftingBufferMKIII(int id, String name, String nameRegional, int tier, boolean mMultiFluid,
        int bufferNum, boolean sf, int page, String... optional) {
        super(id, name, nameRegional, tier, mMultiFluid, bufferNum, sf, page, optional);
        this.ae2qol$bufferNum = bufferNum;
        ae2qol$ensureSlots();
    }

    /** 真实方块实体（{@link Inst}）用构造器，签名与 PH 的同名父类构造器一致。 */
    public MTEPatternCraftingBufferMKIII(String aName, byte aTier, int aSlots, String[] aDescription,
        ITexture[][][] aTextures, boolean aMultiFluid, int aBufferNum) {
        super(aName, aTier, aSlots, aDescription, aTextures, aMultiFluid, aBufferNum);
        this.ae2qol$bufferNum = aBufferNum;
        ae2qol$ensureSlots();
    }

    // ===================== 容量替换 =====================

    /**
     * 把 PH 写死 36 的 4 个数组换成 {@link #PATTERN_SLOTS}：
     * <ul>
     * <li>{@code pattern}：**只在长度不符时**重建并搬运已有样板（无条件重建会丢光已放的样板）；</li>
     * <li>{@code patternItemCache} / {@code patternDetailCache}：只按 {@code pattern.length} 索引
     * （{@code refresh()} / {@code postMEPatternChange()}），长度不足会直接越界；缓存内容本身可随时从
     * {@code pattern} 重建，因此不做搬运、直接换新；</li>
     * <li>{@code multiplier}：长度不符时重建并搬运，空位补 1（PH 的约定是最小 1）。</li>
     * </ul>
     */
    private void ae2qol$ensureSlots() {
        MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) this;

        ItemStack[] patterns = acc.getAe2qolPattern();
        if (patterns == null || patterns.length != PATTERN_SLOTS) {
            ItemStack[] resized = new ItemStack[PATTERN_SLOTS];
            if (patterns != null) {
                System.arraycopy(patterns, 0, resized, 0, Math.min(patterns.length, PATTERN_SLOTS));
            }
            acc.setAe2qolPattern(resized);
        }

        acc.setAe2qolPatternItemCache(new ItemStack[PATTERN_SLOTS]);
        acc.setAe2qolPatternDetailCache(new ICraftingPatternDetails[PATTERN_SLOTS]);

        int[] multiplier = acc.getAe2qolMultiplier();
        if (multiplier == null || multiplier.length != PATTERN_SLOTS) {
            int[] resized = new int[PATTERN_SLOTS];
            Arrays.fill(resized, 1);
            if (multiplier != null) {
                System.arraycopy(multiplier, 0, resized, 0, Math.min(multiplier.length, PATTERN_SLOTS));
            }
            acc.setAe2qolMultiplier(resized);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // PH 的 loadNBTData 里有 `if (multiplier.length < 36) multiplier = new int[36];`：它会用 NBT 里的
        // 倍率数组覆盖字段，长度不足 36 时重建成 36。对新机器而言首次读档必然走这条分支，
        // 于是 144 槽的倍率数组被缩回 36 —— 之后样板窗里第 37 格往后的倍率读写就会数组越界。
        // 所以在 super 之后必须重新补齐（pattern 本身不会被 PH 替换，只是顺手一起校验）。
        ae2qol$ensureSlots();
    }

    // ===================== 标识 / 容量对外表现 =====================

    /** 与 MK.II 一致的输入宽度：每缓冲 32 物品 + 32 流体。 */
    @Override
    public int page() {
        return INPUT_PAGE;
    }

    /** AE2 接口终端行数：16 行 × 9 列 = 144（AE2 条目按像素滚动，16 行能滚到底）。 */
    @Override
    public int rows() {
        return TOTAL_ROWS;
    }

    @Override
    public int rowSize() {
        return GRID_COLS;
    }

    /**
     * GT 默认实现走 {@code getBaseMetaTileEntity().getMetaTileID()}，而**模板实例**在 FML init 阶段
     * base 为 null（注册配方时就会 NPE），所以用固定 ID。与库存统计终端（32000 系列）踩过的是同一个坑。
     */
    @Override
    public ItemStack getStackForm(long aAmount) {
        return new ItemStack(GregTechAPI.sBlockMachines, (int) aAmount, MTE_ID);
    }

    /** NEI/创造页的机器图标：同样走固定 ID，避免模板实例上的 NPE。 */
    @Override
    public ItemStack getMachineCraftingIcon() {
        return getStackForm(1L);
    }

    /**
     * 懒翻译：构造发生在 FML init 阶段，那时 `StatCollector` 可能还没准备好语言文件。
     * 键名沿用本模组其它 GT 机器的约定（{@code gt.blockmachines.<mName>.desc*}，
     * 与「万能维护仓」完全一致），显示名键 {@code ...name} 由 GT 的
     * {@code IMetaTileEntity.getLocalName()} → {@code getLocalNameKey()} 自动拼出。
     */
    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal(AE2QOL_LANG_PREFIX + "desc"),
            StatCollector.translateToLocal(AE2QOL_LANG_PREFIX + "desc.0"),
            StatCollector.translateToLocal(AE2QOL_LANG_PREFIX + "desc.1"),
            StatCollector.translateToLocal(AE2QOL_LANG_PREFIX + "desc.2"),
            EnumChatFormatting.GRAY + "["
                + StatCollector.translateToLocal("ae2_qof.modname")
                + " + ProgrammableHatches]" };
    }

    /** 模板构造器用的描述（英文兜底，正常显示走 {@link #getDescription()} 的懒翻译）。 */
    public static String[] defaultDescription() {
        return new String[] { "AE-direct pattern buffer for multiblocks",
            "144 pattern slots (4x the original 36)", "32 item + 32 fluid inputs per buffer, 24 buffers",
            "[AE2 QoL + ProgrammableHatches]" };
    }

    // ===================== 真实实例 =====================

    /**
     * PH 的 {@code newMetaEntity} 返回 PH 自己的内部类 {@code PatternDualInputHatch.Inst}；
     * 如果照抄，真实方块实体就会退回 36 槽（构造的是 PH 的类而不是我们的）。
     * 所以这里返回我们自己的 {@link Inst}。
     */
    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new Inst(mName, mTier, 16 * page() + 1, mDescriptionArray, mTextures, mMultiFluid, ae2qol$bufferNum);
    }

    /** 照 PH 的写法把 {@code supportsFluids()} / {@code page()} 委托给外层模板实例。 */
    public class Inst extends MTEPatternCraftingBufferMKIII {

        public Inst(String aName, byte aTier, int aSlots, String[] aDescription, ITexture[][][] aTextures,
            boolean aMultiFluid, int aBufferNum) {
            super(aName, aTier, aSlots, aDescription, aTextures, aMultiFluid, aBufferNum);
        }

        @Override
        public boolean supportsFluids() {
            return MTEPatternCraftingBufferMKIII.this.supportsFluids();
        }

        @Override
        public int page() {
            return MTEPatternCraftingBufferMKIII.this.page();
        }
    }

    // ===================== 样板窗（MUI2） =====================

    /**
     * 覆写 PH 的样板窗：PH 原版是固定 4 列 × 9 行（36 格）的小窗，本实现改成
     * 9 列 × 9 可见行、滚动覆盖 16 行（144 格），并把停靠位置按屏幕裁剪。
     * 结构（三页 tab：样板 / 单独倍率 / 批量倍率 + 退款）与 PH 保持一致。
     */
    @Override
    protected ModularPanel createPatternWindow2(PanelSyncManager syncManager) {
        final MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) this;
        final int WIDTH = 18 * GRID_COLS + 6;
        final int HEIGHT = 18 * VISIBLE_ROWS + 6;
        final int PANEL_W = WIDTH;
        final int TAB_W = 32;
        final int SCROLL_H = 18 * TOTAL_ROWS;

        // 打开时才知道主面板与屏幕的实际尺寸，所以停靠位置放在 onOpen 里算（super 布好局之前）。
        ModularPanel builder = new ModularPanel("pattern_window") {

            @Override
            public void onOpen(ModularScreen screen) {
                Area main = screen.getMainPanel()
                    .getArea();
                Area screenArea = screen.getScreenArea();
                // 优先贴主面板右侧；本窗比 PH 原版宽（9 列 vs 4 列），屏幕不够宽时改为贴屏幕右缘，
                // 保证整窗含右侧标签条都在屏内——否则标签落在屏外就没法用拖动手柄把它拉回来。
                int x = Math.min(main.x() + main.w(), Math.max(0, screenArea.width - PANEL_W - TAB_W));
                int y = Math.max(0, Math.min(main.y(), screenArea.height - HEIGHT));
                this.left(x)
                    .top(y);
                super.onOpen(screen);
            }
        };
        builder.size(PANEL_W, HEIGHT);

        IDrawable tab1 = new ItemDrawable(
            Api.INSTANCE.definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .get()).asIcon()
                    .size(18, 18);
        IDrawable tab2 = GTGuiTextures.OVERLAY_BUTTON_BATCH_MODE_OFF.asIcon()
            .size(18, 18);
        IDrawable tab3 = GTGuiTextures.OVERLAY_BUTTON_BATCH_MODE_ON.asIcon()
            .size(18, 18);
        // 拖动手柄的图标沿用 PH 的选择：GT++ 的隔热手套（注册名里确实带 ".name"，是 GT++ 的怪癖，
        // 不是 lang 键后缀），找不到就退回加号图标。
        Item gloveItem = GameRegistry.findItem("miscutils", "GTPP.bauble.fireprotection.0.name");
        IDrawable dragIcon = gloveItem != null ? new ItemDrawable(new ItemStack(gloveItem, 1, 0)).asIcon()
            .size(18, 18) : GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE.asIcon()
                .size(18, 18);

        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ParentWidget<?> page1 = new ParentWidget<>().coverChildren()
            .name("patterns");
        ParentWidget<?> page2 = new ParentWidget<>().coverChildren()
            .name("individual_multiplier");
        ParentWidget<?> page3 = new ParentWidget<>().coverChildren()
            .name("batch_multiplier");

        // ---- 第 3 页：批量倍率 ----
        // 这些按钮会改 multiplier[]（写回 NBT），因此必须在服务端执行：点击经 InteractionSyncHandler
        // 同步到服务端后运行（照抄 PH 的做法）。
        page3.child(
            PatternWindowWidgets.makeBatchButton(syncManager, "batch_x2", () -> {
                int[] m = acc.getAe2qolMultiplier();
                for (int i = 0; i < m.length; i++) {
                    m[i] = Math.max(m[i] * 2, 1);
                }
                refresh();
            })
                .pos(3, 3)
                .tooltip(t -> t.addLine(IKey.str("x2"))));
        page3.child(
            IKey.str("x2")
                .asWidget()
                .pos(3 + 3, 3));
        page3.child(
            PatternWindowWidgets.makeBatchButton(syncManager, "batch_set1", () -> {
                int[] m = acc.getAe2qolMultiplier();
                for (int i = 0; i < m.length; i++) {
                    m[i] = 1;
                }
                refresh();
            })
                .pos(3 + 16, 3)
                .tooltip(t -> t.addLine(IKey.str("=1"))));
        page3.child(
            IKey.str("=1")
                .asWidget()
                .pos(3 + 3 + 16, 3));
        page3.child(
            PatternWindowWidgets.makeBatchButton(syncManager, "batch_xn", () -> {
                int[] m = acc.getAe2qolMultiplier();
                for (int i = 0; i < m.length; i++) {
                    m[i] = Math.max(m[i] * n, 1);
                }
                refresh();
            })
                .pos(3, 3 + 32)
                .tooltip(t -> t.addLine(IKey.str("xN"))));
        page3.child(
            IKey.dynamic(() -> "x" + n)
                .asWidget()
                .pos(3 + 3, 3 + 32));
        page3.child(
            PatternWindowWidgets.makeBatchButton(syncManager, "batch_setn", () -> {
                int[] m = acc.getAe2qolMultiplier();
                for (int i = 0; i < m.length; i++) {
                    m[i] = n;
                }
                refresh();
            })
                .pos(3 + 16, 3 + 32)
                .tooltip(t -> t.addLine(IKey.str("=N"))));
        page3.child(
            IKey.dynamic(() -> "=" + n)
                .asWidget()
                .pos(3 + 3 + 16, 3 + 32));

        IntSyncValue nValue = new IntSyncValue(() -> n, s -> {
            n = s;
            refresh();
        }).allowC2S();
        page3.child(
            new TextFieldWidget().value(nValue)
                .formatAsInteger(true)
                .numbersInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .setTextColor(Color.WHITE.main)
                .tooltip(t -> t.addLine(IKey.str("N=")))
                .size(60, 18)
                .pos(3, 3 + 32 + 18)
                .background(GTGuiTextures.BACKGROUND_TEXT_FIELD));

        // 退款按钮：沿用 PH 的摆放位置（批量页下方空白处）
        page3.child(
            PatternWindowWidgets.createRefundButton(syncManager, () -> {
                try {
                    acc.invokeAe2qolRefundAll();
                } catch (Exception ignored) {
                    // refundAll 内部已自行吞掉网络异常，这里只兜住 Invoker 的受检异常
                }
            })
                .pos(3, 3 + 32 + 18 + 18 + 4));
        page3.child(
            IKey.str("Refund")
                .asWidget()
                .pos(3 + 18, 3 + 32 + 18 + 18 + 4 + 4));

        // 样板槽与显示槽共用一个 handler：MUI2 的 ItemStackHandler(ItemStack[]) 内部是
        // Arrays.asList(pattern)（已按 2.3.88 字节码核对），所以写入会直接落到 PH 的 pattern 数组上。
        ItemStackHandler sharedHandler = new ItemStackHandler(acc.getAe2qolPattern());

        // 槽组要在槽位引用它之前注册；rowSize 传网格宽度（9），shift 优先级 -1 与 PH 一致
        syncManager.registerSlotGroup("pattern_inv", GRID_COLS, -1);

        // 滚动内容：9 列 × 16 行 = 144。可见区只有 9 行，其余靠 ScrollWidget 滚动。
        ParentWidget<?> content1 = new ParentWidget<>().size(18 * GRID_COLS, SCROLL_H);
        ParentWidget<?> content2 = new ParentWidget<>().size(18 * GRID_COLS, SCROLL_H);

        for (int i = 0; i < PATTERN_SLOTS; i++) {
            final int ii = i;
            final int sx = (i % GRID_COLS) * 18;
            final int sy = (i / GRID_COLS) * 18;

            // ---- 第 2 页：只读显示槽 + 每槽倍率输入框 ----
            // 用 GT 的 PatternSlot 渲染样板输出（它会对流体输出做正确的数量处理，见 PH 的 issue #329）。
            content2.child(
                new PatternSlot().slot(new ModularSlot(sharedHandler, i).accessibility(false, false))
                    .pos(sx, sy)
                    .background(GTGuiTextures.SLOT_ITEM_STANDARD));

            // 倍率必须每次经 accessor 现取现写：PH 的 loadNBTData 会替换整个 multiplier 数组，
            // 缓存数组引用会读到过期数据。
            IntSyncValue mulValue = new IntSyncValue(() -> acc.getAe2qolMultiplier()[ii], s -> {
                acc.getAe2qolMultiplier()[ii] = s;
                refresh();
            }).allowC2S();
            content2.child(
                new TextFieldWidget().value(mulValue)
                    .formatAsInteger(true)
                    .numbersInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
                    .setMaxLength(999)
                    .setTextColor(Color.RED.main)
                    .pos(sx, sy)
                    .size(18, 18));

            // ---- 第 1 页：可交互样板槽 + 倍率数字覆盖层 ----
            content1.child(
                new PatternSlot().slot(
                    new ModularSlot(sharedHandler, i).slotGroup("pattern_inv")
                        .filter(stack -> stack.getItem() instanceof ICraftingPatternItem)
                        .changeListener(
                            (newItem, onlyAmountChanged, client, init) -> acc.invokeAe2qolOnPatternChange()))
                    .pos(sx, sy)
                    .background(GTGuiTextures.SLOT_ITEM_STANDARD));

            // 倍率标签叠在槽位上：必须不参与命中测试，否则会吞掉槽位的点击/拖拽/释放。
            content1.child(
                new PatternWindowWidgets.NonInteractiveText(IKey.dynamic(() -> {
                    int mv = acc.getAe2qolMultiplier()[ii];
                    String label = mv == 1 ? "" : CountFormatter.format(mv);
                    return acc.getAe2qolPattern()[ii] == null ? "\u00a77" + label : label;
                })).pos(sx, sy)
                    .size(18, 18));
        }

        ScrollWidget<?> scroll1 = new ScrollWidget<>(new VerticalScrollData());
        scroll1.getScrollArea()
            .getScrollY()
            .setScrollSize(SCROLL_H);
        scroll1.size(18 * GRID_COLS, 18 * VISIBLE_ROWS);
        scroll1.pos(3, 3);
        scroll1.child(content1);
        page1.child(scroll1);

        ScrollWidget<?> scroll2 = new ScrollWidget<>(new VerticalScrollData());
        scroll2.getScrollArea()
            .getScrollY()
            .setScrollSize(SCROLL_H);
        scroll2.size(18 * GRID_COLS, 18 * VISIBLE_ROWS);
        scroll2.pos(3, 3);
        scroll2.child(content2);
        page2.child(scroll2);

        // 标签条下方那条不可见的垫底：标签凸出在面板之外，面板自带的「NEI 排除区」盖不到它，
        // 所以用一个不绘制、点击穿透的控件补上（照抄 PH）。
        builder.child(
            new Widget<>().pos(WIDTH - 3, -1)
                .size(TAB_W, 28 * 4)
                .excludeAreaInRecipeViewer());

        builder.child(
            new Column().coverChildren()
                .pos(WIDTH - 3, -1)
                .child(
                    new PatternWindowWidgets.DragTab()
                        .background(GuiTextures.TAB_RIGHT.get(-1, false), dragIcon)
                        .size(TAB_W, 28)
                        .tooltip(t -> t.addLine(IKey.str("Hold to drag"))))
                .child(
                    new PageButton(0, tabController).tab(GuiTextures.TAB_RIGHT, 0)
                        .overlay(tab1)
                        .tooltip(t -> t.addLine(IKey.str("Patterns"))))
                .child(
                    new PageButton(1, tabController).tab(GuiTextures.TAB_RIGHT, 0)
                        .overlay(tab2)
                        .tooltip(t -> t.addLine(IKey.str("Individual Multiplier Op."))))
                .child(
                    new PageButton(2, tabController).tab(GuiTextures.TAB_RIGHT, 0)
                        .overlay(tab3)
                        .tooltip(t -> t.addLine(IKey.str("Batch Multiplier Op.")))));

        builder.child(
            new PagedWidget<>().controller(tabController)
                .pos(0, 0)
                .size(WIDTH, HEIGHT)
                .addPage(page1)
                .addPage(page2)
                .addPage(page3));

        return builder;
    }
}
