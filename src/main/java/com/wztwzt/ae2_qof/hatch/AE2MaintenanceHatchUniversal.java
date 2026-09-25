package com.wztwzt.ae2_qof.hatch;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.registry.GameRegistry;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;

public class AE2MaintenanceHatchUniversal extends MTEHatchMaintenance {

    private static final int CIRCUIT_SLOT = 0;

    // 速度上限: MAX(14)=100, 其他按比例递减
    private static final int[] SPEED_MAX = { 0, 0, 2, 5, 8, 13, 18, 25, 33, 41, 51, 62, 73, 86, 100, 100 };
    private static final int[] PARALLEL_MAX = { 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576,
        4194304, 16777216, 67108864, 268435456, 1073741824 };
    // 线程上限：HV(3) 起可用，UV(8) 封顶 64
    private static final int[] THREAD_MAX = { 1, 1, 1, 2, 4, 8, 16, 32, 64, 64, 64, 64, 64, 64, 64, 64 };

    private static final String[] CIRCUIT_KEYS = { "CircuitULV", "CircuitLV", "CircuitMV", "CircuitHV", "CircuitEV",
        "CircuitIV", "CircuitLuV", "CircuitZPM", "CircuitUV", "CircuitUHV", "CircuitUEV", "CircuitUIV",
        "CircuitUMV", "CircuitUXV", "CircuitMAX" };
    private static Item[] CIRCUIT_ITEMS;

    private int userParallel;
    private int userSpeed;
    private int userThreads;

    public AE2MaintenanceHatchUniversal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public AE2MaintenanceHatchUniversal(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures, false);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.hatch.maintenance.universal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.hatch.maintenance.universal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.hatch.maintenance.universal.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
    }
    // v7 方案 B：外观走 ModTextures.front/top/side（TextureMap Mixin 注入的自有路径），
    // 不再占用 GT 的 OVERLAY_AUTOMAINTENANCE；未启用时完全回退 GT 原版渲染（含发光/手动模式）。
    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
                                 net.minecraftforge.common.util.ForgeDirection side,
                                 net.minecraftforge.common.util.ForgeDirection facing,
                                 int aColorIndex,
                                 boolean aActive,
                                 boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("universal_maintenance_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("universal_maintenance_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("universal_maintenance_hatch") };
        }
        return super.getTexture(aBaseMetaTileEntity, side, facing, aColorIndex, aActive, aRedstone);
    }

    private static Item[] getCircuitItems() {
        if (CIRCUIT_ITEMS == null) {
            CIRCUIT_ITEMS = new Item[CIRCUIT_KEYS.length];
            for (int i = 0; i < CIRCUIT_KEYS.length; i++) {
                CIRCUIT_ITEMS[i] = GameRegistry.findItem("dreamcraft", CIRCUIT_KEYS[i]);
            }
        }
        return CIRCUIT_ITEMS;
    }

    /**
     * 判定给定物品是否为 dreamcraft 的各电压电路板，并返回其电压档位（-1 表示不是电路板）。
     * 抽出为静态方法是为了让「槽位校验」与「档位读取」共用同一份判据，避免两处判据漂移。
     */
    private static int circuitLevelOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return -1;
        Item item = stack.getItem();
        Item[] items = getCircuitItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && item == items[i]) return i;
        }
        return -1;
    }

    private int getCircuitLevel() {
        return circuitLevelOf(mInventory[CIRCUIT_SLOT]);
    }

    public int getCircuitLevelPublic() { return getCircuitLevel(); }
    public int getUserParallel() { return userParallel; }
    public int getUserSpeed() { return userSpeed; }
    public int getUserThreads() { return userThreads; }

    public int getMaxParallelForLevel() {
        int lvl = getCircuitLevel();
        return (lvl >= 0 && lvl < PARALLEL_MAX.length) ? PARALLEL_MAX[lvl] : 1;
    }

    public int getMaxSpeedForLevel() {
        int lvl = getCircuitLevel();
        return (lvl >= 0 && lvl < SPEED_MAX.length) ? SPEED_MAX[lvl] : 0;
    }

    public int getMaxThreadsForLevel() {
        int lvl = getCircuitLevel();
        return (lvl >= 0 && lvl < THREAD_MAX.length) ? THREAD_MAX[lvl] : 1;
    }

    public int getEffectiveParallel() {
        return Math.max(1, Math.min(userParallel, getMaxParallelForLevel()));
    }

    public double getEffectiveSpeedBoost() {
        int max = getMaxSpeedForLevel();
        int clamped = Math.max(-max, Math.min(userSpeed, max));
        return 1.0 - clamped / 100.0;
    }

    public int getEffectiveThreads() {
        return Math.max(1, Math.min(userThreads, getMaxThreadsForLevel()));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AE2MaintenanceHatchUniversal(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override protected boolean useMui2() { return true; }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        IntSyncValue parallelSync = new IntSyncValue(
            this::getUserParallel,
            v -> this.userParallel = Math.max(1, Math.min(v, getMaxParallelForLevel()))
        ).allowC2S();
        IntSyncValue speedSync = new IntSyncValue(
            this::getUserSpeed,
            v -> { int m = getMaxSpeedForLevel(); this.userSpeed = Math.max(-m, Math.min(v, m)); }
        ).allowC2S();
        IntSyncValue threadSync = new IntSyncValue(
            this::getUserThreads,
            v -> this.userThreads = Math.max(1, Math.min(v, getMaxThreadsForLevel()))
        ).allowC2S();

        ModularPanel panel = ModularPanel.defaultPanel("universal_maintenance_hatch", 260, 210);

        Flow column = Flow.column().coverChildren().childPadding(3).top(7).left(7);

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.hatch.title"))
            .size(246, 12));

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.hatch.circuit_slot"))
            .size(246, 12));
        column.child(new ItemSlot()
            .slot(new ModularSlot(inventoryHandler, CIRCUIT_SLOT))
            .size(18));

        column.child(paramRow("ae2_qof.gui.hatch.parallel",
            new TextFieldWidget().value(parallelSync).formatAsInteger(true)
                .numbersInt(() -> 1L, () -> (long) getMaxParallelForLevel())
                .setMaxLength(10).size(80, 14),
            IKey.dynamic(() -> "max " + getMaxParallelForLevel())));

        column.child(paramRow("ae2_qof.gui.hatch.speed",
            new TextFieldWidget().value(speedSync).formatAsInteger(true)
                .numbersInt(() -> (long) -getMaxSpeedForLevel(), () -> (long) getMaxSpeedForLevel())
                .setMaxLength(5).size(80, 14),
            IKey.dynamic(() -> "max " + getMaxSpeedForLevel() + "%")));

        column.child(paramRow("ae2_qof.gui.hatch.threads",
            new TextFieldWidget().value(threadSync).formatAsInteger(true)
                .numbersInt(() -> 1L, () -> (long) getMaxThreadsForLevel())
                .setMaxLength(3).size(80, 14),
            IKey.dynamic(() -> "max " + getMaxThreadsForLevel())));

        panel.bindPlayerInventory();
        panel.child(column);
        return panel;
    }

    private Flow paramRow(String labelKey, TextFieldWidget field, IKey suffix) {
        return Flow.row().coverChildren().childPadding(3)
            .child(new TextWidget<>(IKey.lang(labelKey)).size(55, 14))
            .child(field)
            .child(new TextWidget<>(suffix).size(120, 14));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("ae2qolPar", userParallel);
        aNBT.setInteger("ae2qolSpd", userSpeed);
        aNBT.setInteger("ae2qolThr", userThreads);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        userParallel = aNBT.getInteger("ae2qolPar");
        if (userParallel <= 0) userParallel = 1;
        userSpeed = aNBT.getInteger("ae2qolSpd");
        userThreads = aNBT.getInteger("ae2qolThr");
        if (userThreads <= 0) userThreads = 1;
    }

    @Override public boolean allowPullStack(IGregTechTileEntity a, int i, ForgeDirection s, ItemStack stack) { return false; }
    @Override public boolean allowPutStack(IGregTechTileEntity a, int i, ForgeDirection s, ItemStack stack) { return false; }

    /**
     * 电路板槽的物品校验。
     * <p>
     * 背景（fix50 修复的回归）：GT 5.09.54 起新接通了一条 MTE 物品校验链——
     * {@code MTEItemStackHandler.isItemValid}（该类 5.09.52 时没有这个方法）→
     * {@code MetaTileEntity.func_94041_b} → {@code MTEHatchMaintenance.func_94041_b}
     * （该覆写同样是 5.09.54 新增）= {@code IsAutoMaintenanceInput(stack) && super}；
     * 而 {@code super}（{@code CommonMetaTileEntity.func_94041_b}）返回
     * {@code getBaseMetaTileEntity().isValidSlot(index)}，即 {@code mAuto && GTMod.proxy.mAMHInteraction}。
     * 本仓构造函数恒传 aAuto=false，于是该链对槽位 0 恒为 false，MUI2 槽位控件因此拒绝一切物品
     * （5.09.52 的 {@code MTEItemStackHandler} 没有 isItemValid 覆写，继承 MUI2 默认 true，故当时可放）。
     * <p>
     * 这里只对电路板槽放行本模组认得的各电压电路板，其余索引与物品一律交回 {@code super}，
     * 因此 GT 原版自动维护仓语义与其它槽位行为完全不变。
     * <p>
     * 注意方法名：{@code func_94041_b} 是 {@code IInventory.isItemValidForSlot} 的 SRG 名。
     * 本项目编译依赖 {@code libs/gregtech-*.jar} 是**未反混淆**的 GT 产物，MC 接口成员在其中保留 SRG 名，
     * 因此覆写必须使用 SRG 名（写成 MCP 名会编译失败：找不到可覆写的方法）。
     */
    @Override
    public boolean func_94041_b(int aIndex, ItemStack aStack) {
        if (aIndex == CIRCUIT_SLOT && circuitLevelOf(aStack) >= 0) return true;
        return super.func_94041_b(aIndex, aStack);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, java.util.List<String> currenttip,
        mcp.mobius.waila.api.IWailaDataAccessor accessor, mcp.mobius.waila.api.IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        try {
            currenttip.add(EnumChatFormatting.AQUA + "P:" + getEffectiveParallel()
                + " | S:" + (getUserSpeed() >= 0 ? "+" : "") + getUserSpeed() + "%"
                + " | T:" + getEffectiveThreads());
        } catch (Exception ignored) {}
    }
}
