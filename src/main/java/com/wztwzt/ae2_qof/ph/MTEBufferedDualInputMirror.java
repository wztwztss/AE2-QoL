package com.wztwzt.ae2_qof.ph;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.TextWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import reobf.proghatches.gt.metatileentity.BufferedDualInputHatch;
import reobf.proghatches.gt.metatileentity.DualInputHatchSlave;
import reobf.proghatches.main.registration.Registration;

/**
 * 编程样板输入总成镜像（4.4.0）—— PH「编程样板输入总成」家族的**专用镜像从仓**。
 *
 * <p>
 * 背景：GTNG / Apeiron / 原版格雷都有「总成 + 镜像」配对，唯独 PH 没有
 * {@code BufferedDualInputHatch}（编程样板输入总成 MK.I/II/III/配方表）的专用镜像
 * ——PH 自家的「二合一输入镜像」泛型擦除后虽然实际能绑它，但语义上是给二合一输入仓的，
 * 玩家无从发现。本类补上这块拼图：多台「支持样板输入总成」的机器共用一台主总成。
 *
 * <h2>实现方式（方案 A：继承 PH 泛型从仓）</h2>
 * 除以下 override 外，**全部行为继承** {@link DualInputHatchSlave}：
 * <ul>
 * <li>{@link #trySetMasterFromCoord}：先校验主仓 {@code instanceof BufferedDualInputHatch}
 * （全家族：PH 直系 MK.I/II/III/配方表/Quad + PatternDualInputHatch 家族 + 本模组
 * MK.III/MK.IV——它们都 IS-A BufferedDualInputHatch），再交父类完成坐标记录/watcher 重挂。
 * 父类原实现因泛型擦除只校验空标记接口 IPHDual，任何 PH 双输入家族都放行；</li>
 * <li>{@link #getTierForStructure}：委托主仓——对齐格雷自家
 * {@code MTEHatchCraftingInputSlave} 的做法（PH 从仓没做，某些多方块按结构等级校验仓时
 * 未绑定的镜像会按 ULV 算）；</li>
 * <li>{@link #onRightclick}：数据棒绑定最优先；**已绑定时右击 = 直接打开主仓的样板页**
 * （用户口径：镜像完成后要能一眼看到总成的页面），潜行 + 右击 = 本镜像自己的设置面板；
 * 未绑定时与 PH 镜像同款打开设置面板；</li>
 * <li>{@link #buildUI}：在父类（排序三态按钮）基础上补「[AE2 QoL] 标题 + 绑定状态行」——
 * 父类面板只有一个按钮，无任何归属/状态信息；</li>
 * <li>{@link #getDescription} / {@link #newMetaEntity}：自家语言键与实例构造。</li>
 * </ul>
 *
 * <h2>继承即得的行为（取证见 CHANGELOG 4.4.0）</h2>
 * 绑定 UX 与 PH 镜像同款（空手持数据棒左击主仓写 {@code type="ProgHatchesDualInput"} →
 * 右击本镜像绑定；拒绝格雷系闪存）；watcher 转发（主仓样板/缓冲变化触发接入机器配方重检）；
 * 缓冲顺序三态（0=副本多优先/1=副本少优先/2=跟随主仓，镜像侧独立）；MUI2 自有 GUI
 * （右击打开，左击转开主仓 GUI）；未绑定每 100t 自动重连；NBT 持久化（父类的
 * {@code hasKey("x")} 早退判据在真实存档加载时恒真——vanilla {@code TileEntity.writeToNBT}
 * 会写顶层 x/y/z，绑定恢复可靠）；WAILA（服务端写 tag/客户端读，架构正确）。
 *
 * <h2>性能红线（达标）</h2>
 * 零新增每 tick 逻辑：不扫描、不轮询，仅父类的未绑定 100t 重连；绑定后全部纯委托。
 *
 * <h2>纪律</h2>
 * 本类只在装了 ProgrammableHatches 时才会被加载（注册入口在
 * {@link PhIntegration#register()} 的 PH 守卫之后，与 MK.III/MK.IV 同一套做法）。
 */
public class MTEBufferedDualInputMirror extends DualInputHatchSlave<BufferedDualInputHatch> {

    /** GT MetaTileEntity ID：32112。32112-32139 全段已对实例 40+ jar 做字节码审计核实空闲。 */
    public static final int MTE_ID = 32112;

    /** MTE 内部名，决定显示名 lang 键 {@code gt.blockmachines.<MTE_NAME>.name}。 */
    public static final String MTE_NAME = "ae2qof.hatch.input.buffered.mirror";

    private static final String LANG_PREFIX = "gt.blockmachines." + MTE_NAME + ".";

    /**
     * 注册用（模板实例）构造器。
     * <p>
     * 父类构造器会把自己加进 {@code Registration.items}（PH 创造页的数据源）——按方案定稿
     * 这里立即移除，物品只进 ae2_qof 创造页（与 MK.III/MK.IV 一致）；移除失败只 WARN，
     * 表现是物品多出现在 PH 创造页里（可见、无害，不静默）。
     */
    public MTEBufferedDualInputMirror(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        boolean removed = Registration.items.remove(new ItemStack(GregTechAPI.sBlockMachines, 1, aID));
        if (!removed) {
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 镜像未能从 PH 创造页清单移除（将同时出现在 PH 创造页）：id={}", aID);
        }
    }

    /** 真实方块实体用构造器（{@code newMetaEntity} 走这里，签名与父类一致）。 */
    public MTEBufferedDualInputMirror(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBufferedDualInputMirror(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal(LANG_PREFIX + "desc"),
            StatCollector.translateToLocal(LANG_PREFIX + "desc.0"),
            StatCollector.translateToLocal(LANG_PREFIX + "desc.1"),
            StatCollector.translateToLocal(LANG_PREFIX + "desc.2"),
            StatCollector.translateToLocal(LANG_PREFIX + "desc.3"),
            EnumChatFormatting.GRAY + "["
                + StatCollector.translateToLocal("ae2_qof.modname")
                + " + ProgrammableHatches]" };
    }

    /**
     * 主仓类型校验（方案定稿 Q1：全家族）。
     * <p>
     * 只接受 {@link BufferedDualInputHatch} 家族：PH 直系（MK.I/II/III/配方表/Quad，
     * 全部以 BufferedDualInputHatch 及其子类注册）+ {@code PatternDualInputHatch} 家族
     * （含本模组 MK.III/MK.IV，二者继承自它）。格雷系「样板输入总成(ME)」与 GTNG 等
     * 其它家族在此被拒（它们的闪存 type 本就是 "CraftingInputBuffer"，父类也会拒）。
     * 校验通过后交给父类完成坐标记录、master 赋值与 watcher 重挂。
     */
    @Override
    public gregtech.common.tileentities.machines.IDualInputHatch trySetMasterFromCoord(int x, int y, int z) {
        TileEntity te = getBaseMetaTileEntity().getWorld()
            .getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity)) return null;
        IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
        if (!(mte instanceof BufferedDualInputHatch)) return null;
        return super.trySetMasterFromCoord(x, y, z);
    }

    /**
     * 结构等级跟随主仓（对齐格雷 {@code MTEHatchCraftingInputSlave#getTierForStructure}）。
     * 未绑定时回落到本镜像自身的档（tier 6，与 PH 从仓一致）。
     */
    @Override
    public byte getTierForStructure() {
        BufferedDualInputHatch master = getMaster();
        return master == null ? super.getTierForStructure() : master.getTierForStructure();
    }

    // ===================== 交互路由（4.4.0-fix2，用户口径） =====================

    /**
     * 右击路由（优先级从高到低）：
     * <ol>
     * <li>数据棒：有则处理绑定（复刻父类私有 {@code tryLinkDataStick} 的行为——含
     * "不支持GT5u总成" 兼容提示），无论是否潜行都吃掉本次点击；</li>
     * <li>已绑定且未潜行：**直接打开主仓总成的样板页**（{@code m.onRightclick} 与父类
     * 左击转发同一条调用路径，服务端线程安全）；</li>
     * <li>其余（未绑定 / 潜行）：本镜像自己的设置面板（缓冲顺序三态）。</li>
     * </ol>
     * 潜行 + 左击（拆方块）不受影响；父类的「未潜行左击 = 主仓 GUI」转发仍然保留。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!(aPlayer instanceof EntityPlayerMP)) return false;
        if (ae2qol$handleDataStick(aPlayer)) return true;
        if (!aPlayer.isSneaking()) {
            BufferedDualInputHatch master = getMaster();
            if (master != null) {
                return master.onRightclick(master.getBaseMetaTileEntity(), aPlayer);
            }
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    /**
     * 数据棒绑定（行为对齐父类私有 {@code tryLinkDataStick}，跨包无法直接调用故自写）：
     * 数据棒 type = "ProgHatchesDualInput" → 按坐标绑定；type = "CraftingInputBuffer"
     * （格雷系）→ PH 口径的兼容拒绝提示；其余 → 不处理（返回 false 让点击继续走默认路由）。
     */
    private boolean ae2qol$handleDataStick(EntityPlayer aPlayer) {
        ItemStack dataStick = aPlayer.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return false;
        if (dataStick.hasTagCompound() && dataStick.stackTagCompound.getString("type")
            .equals("CraftingInputBuffer")) {
            aPlayer.addChatMessage(new ChatComponentTranslation("programmable_hatches.gt.slave.compat"));
            return false;
        }
        if (!dataStick.hasTagCompound() || !dataStick.stackTagCompound.getString("type")
            .equals("ProgHatchesDualInput")) {
            return false;
        }
        int x = dataStick.stackTagCompound.getInteger("x");
        int y = dataStick.stackTagCompound.getInteger("y");
        int z = dataStick.stackTagCompound.getInteger("z");
        if (trySetMasterFromCoord(x, y, z) != null) {
            aPlayer.addChatMessage(new ChatComponentText("Link successful"));
        } else {
            aPlayer.addChatMessage(new ChatComponentText("Link failed"));
        }
        return true;
    }

    // ===================== GUI（父类面板 + 品牌/状态） =====================

    /**
     * 父类面板只有一个排序按钮，无任何归属与状态信息。这里补上：
     * <ul>
     * <li>标题行：{@code [AE2 QoL] 编程样板输入总成镜像}；</li>
     * <li>绑定状态行：未绑定（红）/ 已绑定 + 主仓名与坐标（绿）。</li>
     * </ul>
     * 状态经 S2C sync value 下发（getter 延迟到 detectAndSendChanges 才求值，构建期安全——
     * 4.4.0-fix2 的"构建期不碰重活"纪律）；文案在客户端渲染，服务端只发数据。
     */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        ModularPanel panel = super.buildUI(data, syncManager, uiSettings);

        IntSyncValue boundSync = new IntSyncValue(() -> getMaster() != null ? 1 : 0);
        StringSyncValue masterInfoSync = new StringSyncValue(() -> {
            BufferedDualInputHatch m = getMaster();
            if (m == null) return "";
            IGregTechTileEntity b = m.getBaseMetaTileEntity();
            return getNameOf(m) + " @ " + b.getXCoord() + ", " + b.getYCoord() + ", " + b.getZCoord();
        });
        syncManager.syncValue("mirror_bound", boundSync);
        syncManager.syncValue("mirror_master", masterInfoSync);

        panel.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.AQUA + "[AE2 QoL] "
                        + StatCollector.translateToLocal("ae2_qof.gui.pattern_mirror.title"))).pos(7, 27)
                            .size(190, 12));
        panel.child(new TextWidget<>(IKey.dynamic(() -> {
            if (boundSync.getIntValue() == 0) {
                return EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.gui.pattern_mirror.unbound");
            }
            return EnumChatFormatting.GREEN + StatCollector.translateToLocal("ae2_qof.gui.pattern_mirror.bound")
                + " "
                + EnumChatFormatting.WHITE
                + masterInfoSync.getValue();
        })).pos(7, 41)
            .size(190, 22));
        return panel;
    }
}
