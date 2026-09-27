package com.wztwzt.ae2_qof.mixin.mc;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.blockcopy.BlockCopyService;

/**
 * 3.23.1：**放置写回（还原）** —— 拿着带「方块快照」（{@link BlockCopyService#NBT_ROOT}）的物品
 * 普通右键放下后，把快照里的 TileEntity NBT 写回新放下的方块，并清掉物品上的快照。
 *
 * <h2>为什么注入 {@code ItemBlock.onItemUse}</h2>
 * 用户口径是"**普通右键放置就还原**"。Forge 的 {@code PlayerInteractEvent} 发生在**放置之前**，
 * 那时新方块与其 TE 还不存在，拿不到可写回的目标；而 {@code ItemBlock.onItemUse} 在
 * **放置成功之后 RETURN**，此时目标 TE 已就位 ⇒ 在 RETURN 注入最合适（这也是 Stage 1 给出的方案）。
 *
 * <h2>坐标推导</h2>
 * 注入点是方法**返回时**，局部变量已不可靠，故按原版 {@code onItemUse} 的语义**重算**真正放下的位置：
 * 点到的方块若不可替换，则按 {@code side} 偏移一格；特殊情形（顶层雪 {@code Blocks.snow_layer}）按 side=1 处理。
 *
 * <h2>纪律</h2>
 * 只在**服务端**（{@code !world.isRemote}）且放置成功（返回值 true）时动作；失败一律记日志不静默；
 * 快照**一次性**使用（写回后清除，防重复使用）。
 */
@Mixin(ItemBlock.class)
public class MixinItemBlockOnItemUse {

    @Inject(method = "onItemUse", at = @At("RETURN"))
    private void ae2qol$restoreBlockSnapshot(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        int side, float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (world == null || world.isRemote) return;
            if (!cir.getReturnValueZ()) return; // 只有放置成功才谈还原
            if (!BlockCopyService.hasSnapshot(stack)) return;

            Block clicked = world.getBlock(x, y, z);
            int px = x;
            int py = y;
            int pz = z;
            if (clicked == Blocks.snow_layer && (world.getBlockMetadata(x, y, z) & 7) < 1) {
                side = 1;
            } else if (clicked != Blocks.vine && clicked != Blocks.tallgrass
                && clicked != Blocks.deadbush
                && (clicked == null || !clicked.isReplaceable(world, x, y, z))) {
                    switch (side) {
                        case 0:
                            py--;
                            break;
                        case 1:
                            py++;
                            break;
                        case 2:
                            pz--;
                            break;
                        case 3:
                            pz++;
                            break;
                        case 4:
                            px--;
                            break;
                        case 5:
                            px++;
                            break;
                        default:
                            break;
                    }
                }

            boolean restored = BlockCopyService.applySnapshot(player, world, px, py, pz, stack);
            if (restored) {
                BlockCopyService.clearSnapshot(stack); // 快照一次性使用
                if (player instanceof EntityPlayerMP) {
                    BlockCopyService.tell((EntityPlayerMP) player, "方块数据已还原（" + px + "," + py + "," + pz + "）", null, null);
                }
                MyMod.LOG.info("[AE2QoL] 放置写回成功：快照已还原到 {},{},{} 并清除", px, py, pz);
            } else {
                MyMod.LOG.info(
                    "[AE2QoL] 放置写回未生效：{},{},{}（无 TE 或快照不含 TE 数据；快照保留在物品上）",
                    px,
                    py,
                    pz);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 放置写回异常（已记日志，快照保留）", t);
        }
    }
}
