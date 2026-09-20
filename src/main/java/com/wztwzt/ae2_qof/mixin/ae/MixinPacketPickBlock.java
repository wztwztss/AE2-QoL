package com.wztwzt.ae2_qof.mixin.ae;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ServerTerminalHelper;

import appeng.api.storage.data.IAEItemStack;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.packets.PacketPickBlock;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.item.AEItemStack;

/**
 * 「世界里对着方块按中键」的可合成兜底。
 * <p>
 * AE2 原版链路（客户端 {@code KeyBindHandler.handlePickBlock} → 服务端
 * {@code PacketPickBlock.serverPacketData}）在玩家背包里没有该物品时会尝试从 ME 网络取一个：
 * <ul>
 * <li>网络有存量 → 取到手上（保持原样，本 Mixin 不介入）</li>
 * <li>网络没存量 → 原版直接 {@code return}，玩家看到的是「按了没反应」</li>
 * </ul>
 * 本 Mixin 只补第二种情况里「其实有合成样板」的那部分：改打开 AE2 原生的
 * 「要合成多少个」界面（gui.craftAmount），由玩家自己填数量再点确认。
 * <p>
 * 判定顺序刻意做成「能不管就不管」，除目标情况外一律放行给原版：
 * <ol>
 * <li>解析不出被点方块的物品 → 放行</li>
 * <li>背包里已经有同种物品（哪怕是散的、在背包里没在快捷栏）→ 放行，原版会帮你切槽/补齐</li>
 * <li>身上没有可用的无线终端（未携带 / 未绑定 / 不在范围）→ 放行，原版会给出「未找到无线终端」提示</li>
 * <li>ME 网络里还有存量 → 放行，原版自己取物</li>
 * <li>只剩最后一种情况：网络没存量但有可用样板 → 打开下单界面并拦截原版</li>
 * </ol>
 * 任何一步出现异常都按「放行原版」处理，绝不让异常冒泡到网络线程。
 * <p>
 * 线程说明：注入点与原版取物逻辑处在同一个调用上下文，做的又是原版逻辑的子集
 * （读背包、模拟取物、查样板、开界面），因此不额外做线程调度，与原版行为一致。
 */
@Mixin(value = PacketPickBlock.class, remap = false)
public abstract class MixinPacketPickBlock {

    /** AE2 把待取物品放在这个私有字段里；反射读取以避免字段改名导致 Mixin 应用期硬失败。 */
    private static Field ae2qol$pickedBlockField;
    private static boolean ae2qol$pickedBlockFieldResolved;

    @Inject(
        method = "serverPacketData(Lappeng/core/sync/network/INetworkInfo;Lappeng/core/sync/AppEngPacket;Lnet/minecraft/entity/player/EntityPlayer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void ae2qol$craftAmountFallback(INetworkInfo networkInfo, AppEngPacket packet, EntityPlayer player,
        CallbackInfo ci) {
        try {
            if (!(player instanceof EntityPlayerMP playerMP)) return;

            ItemStack picked = ae2qol$readPickedBlock(this);
            if (picked == null || picked.getItem() == null) return;

            // 背包里已经有这个物品 → 原版会切槽或补齐，交给它
            if (ae2qol$inventoryContains(playerMP, picked)) return;

            // 没有可用无线终端（未携带/未绑定/不在范围）→ 交给原版（原版会提示未找到终端）
            WirelessTerminalGuiObject terminal = ServerTerminalHelper.resolveTerminal(playerMP);
            if (terminal == null) return;

            IAEItemStack target = AEItemStack.create(picked.copy());
            if (target == null) return;
            if (target.getStackSize() <= 0) {
                target.setStackSize(1);
            }

            // 网络里还有存量 → 交给原版取物
            if (ServerTerminalHelper.hasNetworkStock(terminal, target)) return;

            // 没存量：有样板就打开「要合成多少个」，没有就仍由原版静默处理
            if (ServerTerminalHelper.openCraftAmountIfCraftable(playerMP, terminal, target)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] pick-block craft fallback skipped: {}", t.toString());
        }
    }

    /**
     * 读取 {@code PacketPickBlock.pickedBlock}（私有 final 字段）。
     * 取不到时返回 null，调用方按「放行原版」处理。
     */
    private static ItemStack ae2qol$readPickedBlock(Object packet) {
        try {
            if (!ae2qol$pickedBlockFieldResolved) {
                ae2qol$pickedBlockFieldResolved = true;
                Field field = PacketPickBlock.class.getDeclaredField("pickedBlock");
                field.setAccessible(true);
                ae2qol$pickedBlockField = field;
            }
            Field field = ae2qol$pickedBlockField;
            if (field == null) return null;
            Object value = field.get(packet);
            return value instanceof ItemStack stack ? stack : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 玩家主背包里是否已有同种物品——判定口径与 AE2 原版取物完全一致
     * （{@code isItemEqual} + {@code areItemStackTagsEqual}），避免抢走原版的切槽/补齐行为。
     */
    private static boolean ae2qol$inventoryContains(EntityPlayerMP player, ItemStack picked) {
        ItemStack[] main = player.inventory.mainInventory;
        if (main == null) return false;
        for (ItemStack stack : main) {
            if (stack == null) continue;
            if (stack.isItemEqual(picked) && ItemStack.areItemStackTagsEqual(stack, picked)) {
                return true;
            }
        }
        return false;
    }
}
