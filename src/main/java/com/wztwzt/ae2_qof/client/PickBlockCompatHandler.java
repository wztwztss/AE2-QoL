package com.wztwzt.ae2_qof.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import org.lwjgl.input.Mouse;

import com.wztwzt.ae2_qof.MyMod;

import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPickBlock;
import appeng.util.PlayerInventoryUtil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 「世界里对**方块**按中键」的客户端触发补丁（fix54）。
 * <p>
 * <b>为什么需要它</b>：AE2 的世界中键取物依赖 GTNHLib 的 {@code PickBlockEvent}（由
 * {@code MixinMinecraft_PickBlockTrap} 挂在原版 {@code Minecraft.middleClickMouse()} 的 HEAD 发出）。
 * 但本整合包内的 <b>sciencenotleisure</b> 在**同一个 HEAD** 注入并 {@code ci.cancel()}：
 * {@code ClientUtils.onBeforePickBlock(...)} 在"准星没有瞄到实体"时执行完它自己的 1000 格远程取物后
 * **无条件返回 true**——于是原版取物例程被整个取消，事件根本不会发出，
 * AE2 永远不会发送 {@code PacketPickBlock}，任何挂在 {@code PacketPickBlock.serverPacketData}
 * 的兜底逻辑（fix52）都形同不存在。
 * <p>
 * 本类因此**另取一个与 SNL、与原版方法都无关的触发点**（Forge 的
 * {@code InputEvent.MouseInputEvent}），在同样的前置条件下自行补发这个包，
 * 把 AE2 原本的行为还回来（有存量→取到手上；没存量但有样板→由 fix52 打开下单界面）。
 * <p>
 * <b>为什么不会重复发包</b>：AE2 客户端只在两条路径下发方块取物包——① GTNHLib 事件路径
 * （被 SNL 挡掉）；② 鼠标事件路径，但那条要求"AE2 的 Pick Block 键与原版『选取方块』**不相等**"。
 * 本模组推荐的两键相等配置下，②对**方块**永不触发，故只可能是本类发包。
 * <p>
 * <b>为什么必须先检查无线终端</b>：AE2 服务端在没有终端时会
 * {@code sender.addChatMessage(PlayerMessages.PickBlockTerminalNotFound)}（见
 * {@code PacketPickBlock.serverPacketData}）。若不预检，没有终端的玩家**每次中键都会被刷一条提示**。
 * 这里的判定直接复用 AE2 自己的 {@link PlayerInventoryUtil#getFirstWirelessTerminal}，
 * 与其服务端口径（含饰品栏）完全一致。
 * <p>
 * 其余条件逐条对齐 AE2 的 {@code KeyBindHandler.handlePickBlock()}：开着 GUI 不动作、创造模式交给原版、
 * 准星必须是方块、空气或 {@code getPickBlock} 为空都不发。任何异常都只记一次日志并放弃本次，
 * 绝不影响原版与其它模组的行为。
 */
@SideOnly(Side.CLIENT)
public final class PickBlockCompatHandler {

    private static final PickBlockCompatHandler INSTANCE = new PickBlockCompatHandler();

    /** 中键上一帧是否按下——用边沿检测，避免依赖事件的具体投递粒度。 */
    private boolean middleDown;

    /** 异常只记一次，避免每次中键刷屏。 */
    private static boolean warnLogged;

    private PickBlockCompatHandler() {}

    public static void register() {
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        try {
            final boolean down = Mouse.isButtonDown(2);
            if (down == this.middleDown) return; // 只在按键状态变化时处理
            this.middleDown = down;
            if (!down) return; // 只处理"按下"那一瞬间

            ae2qol$sendPickBlockIfApplicable();
        } catch (Throwable t) {
            if (!warnLogged) {
                warnLogged = true;
                MyMod.LOG.warn("[AE2QoL] pick-block compat hook failed: {}", t.toString());
            }
        }
    }

    /**
     * 逐条对齐 AE2 {@code KeyBindHandler.handlePickBlock()} 的前置条件后补发取物包。
     */
    private static void ae2qol$sendPickBlockIfApplicable() {
        final Minecraft minecraft = Minecraft.getMinecraft();

        // 开着 GUI 不动作（原版取物同样如此）
        if (minecraft.currentScreen != null) return;

        final EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null) return;

        // 创造模式交给原版（AE2 原注释：Use vanilla pick block when in creative）
        if (player.capabilities.isCreativeMode) return;

        final World world = minecraft.theWorld;
        if (world == null) return;

        final MovingObjectPosition target = minecraft.objectMouseOver;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        final int x = target.blockX;
        final int y = target.blockY;
        final int z = target.blockZ;
        final Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) return;

        // 身上没有无线终端就不发：否则服务端会给玩家刷「未找到无线终端」提示
        if (PlayerInventoryUtil.getFirstWirelessTerminal(player) == null) return;

        final ItemStack picked;
        try {
            picked = block.getPickBlock(target, world, x, y, z, player);
        } catch (Throwable t) {
            // 某些模组方块在 getPickBlock 里会抛异常；原版同样会抛，这里只放弃本次补发
            return;
        }
        if (picked == null || picked.getItem() == null) return;

        NetworkHandler.instance.sendToServer(new PacketPickBlock(picked));
    }
}
