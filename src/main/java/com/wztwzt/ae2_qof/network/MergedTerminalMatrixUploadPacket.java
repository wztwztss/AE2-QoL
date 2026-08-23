package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.wztwzt.ae2_qof.merged.ContainerMergedTerminal;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.data.IAEItemStack;
import appeng.items.misc.ItemEncodedPattern;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端：把编码好的合成样板上传到 GTNL 装配矩阵（C2S）。
 * 行为对齐 GTNL 原生 PktPatternTermUploadPattern：
 * OUT 槽为空先编码 → 仅接受普通合成样板 → 重复配方返还空白样板 → 否则插入矩阵样板库。
 * 全程反射访问 GTNL，模组未安装时静默忽略。
 */
public class MergedTerminalMatrixUploadPacket implements IMessage {

    private static final String MATRIX_CLASS = "com.science.gtnl.common.machine.multiblock.AssemblerMatrix";
    private static final String DIRE_DETAILS_CLASS = "com.science.gtnl.utils.DireCraftingPatternDetails";

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<MergedTerminalMatrixUploadPacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalMatrixUploadPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            com.wztwzt.ae2_qof.network.ServerTerminalHelper.scheduleServerTask(() -> work(player));
            return null;
        }
    }

    private static void work(EntityPlayer player) {
        try {
            if (!(player.openContainer instanceof ContainerMergedTerminal cmt)) return;

            // OUT 槽空则先编码（与原生行为一致）
            ItemStack patternStack = cmt.getMergedEncodedSlot()
                .getStack();
            if (patternStack == null) {
                cmt.mergedEncode();
                patternStack = cmt.getMergedEncodedSlot()
                    .getStack();
                if (patternStack == null) return;
            }

            // 仅普通合成样板可上传（终极/处理样板不适用装配矩阵）
            if (!(patternStack.getItem() instanceof ItemEncodedPattern iep)) return;
            Object details = iep.getPatternForItem(patternStack, player.worldObj);
            if (details == null || !((appeng.api.networking.crafting.ICraftingPatternDetails) details).isCraftable()) {
                return;
            }
            Class<?> direClass = Class.forName(DIRE_DETAILS_CLASS);
            if (direClass.isInstance(details)) return;

            Class<?> matrixClass = Class.forName(MATRIX_CLASS);
            IGrid grid = cmt.getGrid();
            if (grid == null) return;

            @SuppressWarnings({ "unchecked", "rawtypes" })
            appeng.api.networking.IMachineSet nodes = grid.getMachines((Class) matrixClass);
            if (nodes == null || nodes.isEmpty()) return;

            IAEItemStack out = ((appeng.api.networking.crafting.ICraftingPatternDetails) details)
                .getCondensedOutputs()[0];

            // 第一轮：矩阵已存在相同输出 → 提示并返还空白样板
            for (IGridNode node : nodes) {
                Object matrix = matrixClass.cast(node.getMachine());
                java.util.Set<?> outputs = (java.util.Set<?>) matrixClass.getMethod("getPossibleOutputs")
                    .invoke(matrix);
                if (outputs.contains(out)) {
                    player.addChatMessage(new ChatComponentTranslation("text.AssemblerMatrix.tooltip.0"));
                    com.google.common.base.Optional<ItemStack> blank = AEApi.instance()
                        .definitions()
                        .materials()
                        .blankPattern()
                        .maybeStack(patternStack.stackSize);
                    if (blank.isPresent() && blank.get() != null) {
                        ItemStack blankStack = blank.get();
                        if (!player.inventory.addItemStackToInventory(blankStack)) {
                            player.entityDropItem(blankStack, 0.0F);
                        }
                    }
                    cmt.getMergedEncodedSlot()
                        .putStack(null);
                    return;
                }
            }

            // 第二轮：插入第一个有空的矩阵
            java.lang.reflect.Method insert = matrixClass.getMethod("insertPattern", ItemStack.class);
            for (IGridNode node : nodes) {
                Object matrix = matrixClass.cast(node.getMachine());
                if ((Boolean) insert.invoke(matrix, patternStack)) {
                    cmt.getMergedEncodedSlot()
                        .putStack(null);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }
}
