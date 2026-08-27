package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.util.ContainerTerminalResolver;
import com.wztwzt.ae2_qof.util.RecipeMapDetector;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.ICustomNameObject;
import appeng.parts.AEBasePart;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RequestProvidersListPacket implements IMessage {

    private ItemStack[] recipeInputs;
    private ItemStack[] recipeOutputs;
    private boolean forceGui;
    private String directRecipeMap;

    public RequestProvidersListPacket() {
        this.recipeInputs = new ItemStack[0];
        this.recipeOutputs = new ItemStack[0];
        this.forceGui = false;
        this.directRecipeMap = null;
    }

    public RequestProvidersListPacket(ItemStack[] inputs, ItemStack[] outputs, boolean forceGui) {
        this.recipeInputs = inputs != null ? inputs : new ItemStack[0];
        this.recipeOutputs = outputs != null ? outputs : new ItemStack[0];
        this.forceGui = forceGui;
        this.directRecipeMap = null;
    }

    /**
     * 直接发送 recipeMap（从样板 NBT 读取），跳过服务端配方检测。
     */
    public RequestProvidersListPacket(String recipeMap, boolean forceGui) {
        this.recipeInputs = new ItemStack[0];
        this.recipeOutputs = new ItemStack[0];
        this.forceGui = forceGui;
        this.directRecipeMap = recipeMap;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.recipeInputs = readItemStackArray(buf);
            this.recipeOutputs = readItemStackArray(buf);
            this.forceGui = buf.readBoolean();
            boolean hasDirect = buf.readBoolean();
            this.directRecipeMap = hasDirect ? readString(buf) : null;
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.recipeInputs = new ItemStack[0];
            this.recipeOutputs = new ItemStack[0];
            this.forceGui = false;
            this.directRecipeMap = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeItemStackArray(buf, this.recipeInputs);
        writeItemStackArray(buf, this.recipeOutputs);
        buf.writeBoolean(this.forceGui);
        buf.writeBoolean(this.directRecipeMap != null);
        if (this.directRecipeMap != null) {
            writeString(buf, this.directRecipeMap);
        }
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeItemStackArray(ByteBuf buf, ItemStack[] stacks) {
        buf.writeInt(stacks.length);
        for (ItemStack stack : stacks) {
            cpw.mods.fml.common.network.ByteBufUtils.writeItemStack(buf, stack);
        }
    }

    private ItemStack[] readItemStackArray(ByteBuf buf) {
        int len = buf.readInt();
        // 恶意包防护：分配先于读取，长度无上界会被恶意 C2S 包打爆堆（#45），超界按空数组处理
        if (len < 0 || len > 64) {
            return new ItemStack[0];
        }
        ItemStack[] stacks = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            stacks[i] = cpw.mods.fml.common.network.ByteBufUtils.readItemStack(buf);
        }
        return stacks;
    }

    public static class Handler implements IMessageHandler<RequestProvidersListPacket, IMessage> {

        @Override
        public IMessage onMessage(RequestProvidersListPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 grid/container
            ServerTerminalHelper.scheduleServerTask(() -> handleMessage(player, message));
            return null;
        }

        private void handleMessage(EntityPlayerMP player, RequestProvidersListPacket message) {
            try {
                Container container = player.openContainer;
                if (container == null) {
                    MyMod.LOG.info("[Upload] server: no open container");
                    return;
                }

                IActionHost terminal = ContainerTerminalResolver.resolveTerminal(container);
                if (terminal == null) {
                    MyMod.LOG.info(
                        "[Upload] server: terminal resolve failed for {}",
                        container.getClass()
                            .getSimpleName());
                    return;
                }

                IGridNode node = terminal.getActionableNode();
                if (node == null) {
                    MyMod.LOG.info("[Upload] server: terminal node is null");
                    return;
                }

                IGrid grid = node.getGrid();
                if (grid == null) {
                    MyMod.LOG.info("[Upload] server: grid is null");
                    return;
                }

                // 检测配方池：优先使用客户端直接提供的 recipeMap
                String recipeMap = message.directRecipeMap;
                if (recipeMap == null || recipeMap.isEmpty()) {
                    recipeMap = RecipeMapDetector.detectRecipeMap(
                        message.recipeInputs,
                        message.recipeOutputs,
                        player.getUniqueID()
                            .toString());
                }

                List<Long> ids = new ArrayList<Long>();
                List<String> names = new ArrayList<String>();
                List<Integer> emptySlots = new ArrayList<Integer>();

                for (Class<? extends IGridHost> hostClass : grid.getMachinesClasses()) {
                    if (!ICraftingProvider.class.isAssignableFrom(hostClass)) {
                        continue;
                    }

                    IMachineSet machines = grid.getMachines(hostClass);
                    if (machines == null) {
                        continue;
                    }

                    for (IGridNode machineNode : machines) {
                        if (machineNode == null) {
                            continue;
                        }

                        Object machine = machineNode.getMachine();
                        if (!(machine instanceof ICraftingProvider)) {
                            continue;
                        }

                        ICraftingProvider provider = (ICraftingProvider) machine;
                        long id = System.identityHashCode(provider);
                        String name = resolveProviderName(machine);

                        ids.add(id);
                        names.add(name);
                        emptySlots.add(estimateEmptySlots(provider));
                    }
                }

                // 尺寸预算（#57）：1.7.10 S3F 自定义负载长度为 short（≤32767 字节），
                // 超大网络的供应器名列表可能超限 → 编码/发送失败、客户端选择界面静默无响应。
                // 超限时优先保留「有空槽」的提供器（自动上传才能真正落目标），无空槽的靠后丢弃；
                // 最终列表维持原顺序便于对照。
                final byte[] rmBytes = recipeMap != null ? recipeMap.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : null;
                final int baseUsed = 4 + 1 + 1 + (rmBytes != null ? 2 + rmBytes.length : 1);

                int totalUsed = baseUsed;
                boolean overflow = false;
                for (int i = 0; i < ids.size(); i++) {
                    totalUsed += 8 + 2
                        + names.get(i)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + 4;
                    if (totalUsed > 32000) {
                        overflow = true;
                        break;
                    }
                }

                List<Integer> keep = new ArrayList<Integer>();
                if (!overflow) {
                    for (int i = 0; i < ids.size(); i++) {
                        keep.add(i);
                    }
                } else {
                    // 优先保留有空槽的提供器：按 emptySlots 降序稳定排序后贪心选取，最后还原原顺序
                    Integer[] idxArr = new Integer[ids.size()];
                    for (int i = 0; i < ids.size(); i++) {
                        idxArr[i] = i;
                    }
                    Arrays.sort(idxArr, (a, b) -> Integer.compare(emptySlots.get(b), emptySlots.get(a)));
                    int budget = baseUsed;
                    for (Integer i : idxArr) {
                        int add = 8 + 2
                            + names.get(i)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            + 4;
                        if (budget + add > 32000) {
                            continue;
                        }
                        budget += add;
                        keep.add(i);
                    }
                    keep.sort(Integer::compare);
                    MyMod.LOG.warn(
                        "[Upload] providers list truncated to fit packet budget (kept {} with empty-slot priority): {} -> {}",
                        keep.size(), ids.size(), keep.size());
                }

                List<Long> outIds = new ArrayList<Long>(keep.size());
                List<String> outNames = new ArrayList<String>(keep.size());
                List<Integer> outEmpty = new ArrayList<Integer>(keep.size());
                for (Integer i : keep) {
                    outIds.add(ids.get(i));
                    outNames.add(names.get(i));
                    outEmpty.add(emptySlots.get(i));
                }

                ModNetwork.CHANNEL.sendTo(
                    new ProvidersListS2CPacket(outIds, outNames, outEmpty, recipeMap, message.forceGui),
                    player);
                MyMod.LOG.info("[Upload] providers list sent: count={}, recipeMap={}", limit, recipeMap);
            } catch (Throwable t) {
                MyMod.LOG.error("Providers list request failed", t);
            }
        }

        private int estimateEmptySlots(ICraftingProvider provider) {
            // 与 UploadPatternPacket 一致：优先统计专属样板槽（IInterfaceViewable.getPatterns()），
            // 避免把 GT/PH 机器 IInventory 原料缓存槽误计为样板空位
            if (provider instanceof IInterfaceViewable viewable) {
                IInventory patterns = viewable.getPatterns();
                if (patterns != null) {
                    int availableSlots = viewable.rows() * viewable.rowSize();
                    int limit = Math.min(availableSlots, patterns.getSizeInventory());
                    int empty = 0;
                    for (int i = 0; i < limit; i++) {
                        ItemStack slot = patterns.getStackInSlot(i);
                        if (slot == null || slot.stackSize <= 0) {
                            empty++;
                        }
                    }
                    return empty;
                }
            }
            if (provider instanceof IInventory inv) {
                int empty = 0;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack slot = inv.getStackInSlot(i);
                    if (slot == null || slot.stackSize <= 0) {
                        empty++;
                    }
                }
                return empty;
            }
            return 0;
        }

        private String resolveProviderName(Object machine) {
            String name = "Crafting Provider";

            // GregTech总成等实现了ICustomNameObject（AE2接口），优先检查
            if (machine instanceof ICustomNameObject customNameObj) {
                try {
                    if (customNameObj.hasCustomName()) {
                        String customName = customNameObj.getCustomName();
                        if (customName != null && !customName.isEmpty()) {
                            return customName;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (machine instanceof TileEntity tile) {
                try {
                    if (tile.getBlockType() != null) {
                        name = tile.getBlockType()
                            .getLocalizedName();
                    }
                } catch (Throwable ignored) {}

                if (machine instanceof IInventory inv) {
                    try {
                        if (inv.hasCustomInventoryName()) {
                            name = inv.getInventoryName();
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (machine instanceof AEBasePart part) {
                try {
                    name = part.getCustomName();
                } catch (Throwable ignored) {}
            }
            return name;
        }
    }
}
