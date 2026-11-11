package com.wztwzt.ae2_qof.ph;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.item.ItemPatternClipboard;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 样板剪贴板右键拦截（4.5.0-fix18）。
 *
 * <h2>为什么需要这个事件层</h2>
 * GT {@code BlockMachines.onBlockActivated}（reference gregtech-5.09.54.264 :402-440）对
 * IGregTechTileEntity 直接 {@code gtTE.onRightclick(...)} 并 return —— 非潜行右键时机器 GUI
 * 先吃掉事件，{@code Item.onItemUse} 永远执行不到；旧实现（只在 onItemUse 处理）于是表现为
 * 「GUI 能开但剪贴板操作无效」（用户在专用服上的实测现象）。只有潜行时 GT 的
 * onBlockActivated 在「潜行+非工具」分支 return false，物品 onItemUse 才有机会执行。
 *
 * <h2>修复语义（用户拍板：反转优先级，接管整个总成家族）</h2>
 * <ul>
 * <li>非潜行 + 右键样板总成 + 手持剪贴板 = 执行剪贴板当前模式（服务端），机器 GUI 不再被打开；</li>
 * <li>潜行 + 右键 = 保留原 GT 行为（开机器 GUI），剪贴板不接管。</li>
 * </ul>
 *
 * <h2>为什么只能在服务端取消（不能双端取消）</h2>
 * 1.7.10 的双端语义不对称，且都读过源码确认：
 * <ul>
 * <li>客户端 {@code Minecraft#clickMouse}（build/rfg 源码 :1528-1529）：只有当
 * {@code PlayerInteractEvent} **未被取消**时才调用 {@code playerController.onPlayerRightClick}；
 * 而 C08 右键包是在 {@code PlayerControllerMP#onPlayerRightClick}（:389）里发出的。
 * ⇒ 客户端取消 = 包不发 = 服务端收不到 = 服务端事件也不触发，剪贴板业务永远不执行。</li>
 * <li>服务端 {@code ItemInWorldManager#activateBlockOrUseItem}（:386-390）：事件被取消后直接
 * {@code return false}，既不走 {@code item.onItemUseFirst}，也不走
 * {@code block.onBlockActivated}（也就是 GT 的机器 GUI）。
 * ⇒ 服务端取消 = 既拦住 GT GUI，又能由我们自己执行业务，正好是要的语义。</li>
 * </ul>
 * 因此：**客户端只做「识别到就不做任何事」，绝不动用 setCanceled；服务端取消并执行**。
 */
public class PatternClipboardInterceptHandler {

    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // PlayerInteractEvent 由 ForgeEventFactory#onPlayerInteract 投递到 MinecraftForge.EVENT_BUS，
        // 与 KnifeNameCopyHandler 同一处总线；不要再往 FML 总线注册一份（会变成幽灵监听）。
        MinecraftForge.EVENT_BUS.register(new PatternClipboardInterceptHandler());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.entityPlayer == null) return;

        final World world = event.world;
        if (world == null) return;

        final ItemStack held = event.entityPlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemPatternClipboard)) return;

        final TileEntity te = world.getTileEntity(event.x, event.y, event.z);
        if (te == null) return;
        // 整个总成家族（PH 原版 / MK.II / Super / MappingSlave / MK.III / MK.IV）
        final PatternHostAdapter.Host host = PatternHostAdapter.resolveHost(te);
        if (host == null) return;

        // 客户端：**什么都不做**。一 cancel 就不发包，服务端分支就永远不执行（见类注释）。
        if (world.isRemote) return;

        final EntityPlayer player = event.entityPlayer;

        // ---- 潜行：机器 GUI（用户拍板的「反转优先级」）----
        // 为什么必须自己调 MTE 层、而不能调 gtTE.onRightclick：
        // BaseMetaTileEntity#onRightclick（:1643）末尾是
        // `if (!aPlayer.isSneaking() && hasValidMetaTileEntity()) return mMetaTileEntity.onRightclick(...)`
        // —— 潜行时 GT **故意**不分发给 MTE，这正是原版「潜行右键机器不开 GUI」的由来。
        // 我们要的语义是「潜行 = 机器 GUI」，所以直接调 MTE 那一层（PH 的切割刀改名 /
        // Buffered 的文件与锯子开关 / MTEHatchInputBus 的 openGui 都在里面）。
        // 同时取消事件：否则 GT 返回 false 后 item.onItemUse 会再跑一遍剪贴板（双重执行）。
        if (player.isSneaking()) {
            final gregtech.api.interfaces.tileentity.IGregTechTileEntity gtTE =
                (gregtech.api.interfaces.tileentity.IGregTechTileEntity) te;
            final gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = gtTE.getMetaTileEntity();
            if (mte == null) return;

            // 权限：手动调 MTE 层会跳过 GT 自己的两道校验，必须在这里补等价守卫，否则
            // 「非主人手持剪贴板 + 潜行右键」就能打开别人上锁/私有机器的 GUI。
            //  - BlockMachines#onBlockActivated（reference :432-437）：非 remote 时先判 isUseableByPlayer，
            //    不通过则 return true（不开 GUI）；
            //  - BaseMetaTileEntity#onRightclick（:1417）：hasPermission = !privateAccess()
            //    || (isServerSide() && 玩家 UUID == ownerUuid)。
            // isUseableByPlayer（:896-903）本身已包含 canAccessData / playerOwnsThis(..,false) /
            // mTickTimer / 距离 64 / mMetaTileEntity.isAccessAllowed，直接复用它最省事且不重复实现策略。
            if (!gtTE.isUseableByPlayer(player)) {
                event.setCanceled(true);
                return;
            }

            // 同时取消事件：否则 GT 返回 false 后 item.onItemUse 会再跑一遍剪贴板（双重执行）。
            event.setCanceled(true);
            try {
                mte.onRightclick(
                    gtTE,
                    player,
                    net.minecraftforge.common.util.ForgeDirection.getOrientation(event.face),
                    0.5F,
                    0.5F,
                    0.5F);
            } catch (Throwable t) {
                MyMod.LOG.error("[AE2QoL] 手持剪贴板潜行右键：打开机器 GUI 失败", t);
            }
            return;
        }

        // ---- 非潜行：剪贴板接管（服务端取消 → GT 的 onBlockActivated 与 item.onItemUse 都不执行）----
        event.setCanceled(true);

        try {
            final int mode = ItemPatternClipboard.getMode(held);
            MyMod.LOG.info(
                "[AE2QoL] 样板剪贴板拦截：模式={} 目标={} @ d{} [{},{},{}] 玩家={}",
                mode,
                host.hostName(),
                player.dimension,
                event.x,
                event.y,
                event.z,
                player.getCommandSenderName());
            if (mode == ItemPatternClipboard.MODE_PASTE) {
                ItemPatternClipboard.doPasteIntercepted(player, host, event.x, event.y, event.z, true);
            } else if (mode == ItemPatternClipboard.MODE_PASTE_APPEND) {
                ItemPatternClipboard.doPasteIntercepted(player, host, event.x, event.y, event.z, false);
            } else if (mode == ItemPatternClipboard.MODE_CUT) {
                ItemPatternClipboard.doCopyIntercepted(player, host, event.x, event.y, event.z, true);
            } else if (mode == ItemPatternClipboard.MODE_COPY) {
                ItemPatternClipboard.doCopyIntercepted(player, host, event.x, event.y, event.z, false);
            }
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] 样板剪贴板拦截执行失败（不影响机器 GUI）", t);
        }
    }
}
