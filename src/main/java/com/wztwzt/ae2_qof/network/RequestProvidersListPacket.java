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
import com.wztwzt.ae2_qof.util.ProviderLocator;
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

    /** Kept for the shutdown hook; provider slots/acceptance are sampled per request. */
    public static void clearCache() {}

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

                // Never cache permissions or empty-slot/acceptance state across requests.
                List<Long> ids = new ArrayList<>();
                List<Integer> totalSlots = new ArrayList<>();
                List<String> locationKeys = new ArrayList<>();
                List<ItemStack> icons = new ArrayList<>();
                List<String> names = new ArrayList<>();
                List<Integer> emptySlots = new ArrayList<>();
                List<ICraftingProvider> providers = new ArrayList<>();
                collectProviders(grid, ids, names, emptySlots, totalSlots, locationKeys, icons, providers);

                // F1: keep only providers that accept this encoded pattern
                ItemStack encodedForFilter = readEncodedPattern(container);
                if (encodedForFilter != null && providers.size() == ids.size()) {
                    List<Integer> accept = new ArrayList<Integer>();
                    for (int i = 0; i < providers.size(); i++) {
                        if (emptySlots.get(i) > 0 && acceptsPattern(providers.get(i), encodedForFilter)) {
                            accept.add(i);
                        }
                    }
                    { // Empty acceptance means no candidate, never the original unfiltered list.
                        List<Long> fIds = new ArrayList<Long>(accept.size());
                        List<String> fNames = new ArrayList<String>(accept.size());
                        List<Integer> fEmpty = new ArrayList<Integer>(accept.size());
                        List<Integer> fTotal = new ArrayList<Integer>(accept.size());
                        List<String> fKeys = new ArrayList<String>(accept.size());
                        List<ItemStack> fIcons = new ArrayList<ItemStack>(accept.size());
                        for (Integer i : accept) {
                            fIds.add(ids.get(i));
                            fNames.add(names.get(i));
                            fEmpty.add(emptySlots.get(i));
                            fTotal.add(totalSlots.get(i));
                            fKeys.add(i < locationKeys.size() ? locationKeys.get(i) : null);
                            fIcons.add(icons.size() > i ? icons.get(i) : null);
                        }
                        ids = fIds;
                        names = fNames;
                        emptySlots = fEmpty;
                        totalSlots = fTotal;
                        locationKeys = fKeys;
                        icons = fIcons;
                    }
                }
                ProvidersListS2CPacket response = new ProvidersListS2CPacket(
                    ids, names, emptySlots, totalSlots, locationKeys, icons, recipeMap, message.forceGui);
                if (response.ids.size() < ids.size()) {
                    player.addChatMessage(new net.minecraft.util.ChatComponentText(
                        "[AE2 QoL] Provider list limited to " + response.ids.size() + "/" + ids.size()
                            + " entries by packet budget; automatic selection disabled."));
                }
                ModNetwork.CHANNEL.sendTo(response, player);
                MyMod.LOG.debug("[Upload] providers list sent: count={}, recipeMap={}", response.ids.size(), recipeMap);
            } catch (Throwable t) {
                MyMod.LOG.error("Providers list request failed", t);
            }
        }

        private static void collectProviders(IGrid grid, List<Long> ids, List<String> names,
            List<Integer> emptySlots, List<Integer> totalSlots, List<String> locationKeys, List<ItemStack> icons,
            List<ICraftingProvider> providers) {
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
                    String name = qualifyProviderName(resolveProviderName(machine), machineNode);
                    ids.add(id);
                    names.add(name);
                    emptySlots.add(estimateEmptySlots(provider));
                    totalSlots.add(estimateTotalSlots(provider));
                    // fix41：一并记录稳定位置（维度+坐标+朝向），供上传时可靠命中
                    locationKeys.add(ProviderLocator.locationKey(machineNode, provider));
                    icons.add(resolveProviderIcon(machine));
                    providers.add(provider);
                }
            }
        }

        /**
         * 读取玩家当前终端里那张「待上传样板」。取不到时返回 null，调用方跳过过滤。
         * 复用 IMergedPatternTerminal / 原生样板终端两条既有解析路径，避免新增反射面。
         */
        private static ItemStack readEncodedPattern(Container container) {
            try {
                if (container instanceof com.wztwzt.ae2_qof.api.IMergedPatternTerminal merged) {
                    net.minecraft.inventory.Slot out = merged.getMergedEncodedSlot();
                    if (out != null && out.getStack() != null) {
                        return out.getStack();
                    }
                    return null;
                }
                String fieldName = null;
                if (container instanceof appeng.container.implementations.ContainerPatternTerm) {
                    fieldName = "patternSlotOUT";
                } else if (container instanceof appeng.container.implementations.ContainerPatternTermEx) {
                    fieldName = "patternSlotOUT";
                }
                if (fieldName == null) {
                    return null;
                }
                java.lang.reflect.Field f = findField(container.getClass(), fieldName);
                f.setAccessible(true);
                Object slot = f.get(container);
                if (slot instanceof net.minecraft.inventory.Slot s && s.getStack() != null) {
                    return s.getStack();
                }
            } catch (Throwable ignored) {}
            return null;
        }

        private static java.lang.reflect.Field findField(Class<?> cls, String name) throws NoSuchFieldException {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {}
            }
            throw new NoSuchFieldException(name);
        }

        /**
         * 判断某个供应器的样板槽能否吃下这张样板。走 IInventory.isItemValidForSlot，
         * 与 UploadPatternPacket 的实际写入条件保持一致：两者结论相同，过滤才有意义。
         */
        private static boolean acceptsPattern(ICraftingProvider provider, ItemStack pattern) {
            try {
                if (provider instanceof IInterfaceViewable viewable) {
                    IInventory patterns = viewable.getPatterns();
                    if (patterns == null) {
                        return false;
                    }
                    int availableSlots = viewable.rows() * viewable.rowSize();
                    int limit = Math.min(availableSlots, patterns.getSizeInventory());
                    // fix41：逐个空槽判断，只要有一个空槽愿意接收就算「能收」。
                    // 旧实现只看第一个空槽，遇到分类型样板槽（前段收物品、后段收流体）会把本来放得下的
                    // 机器误判成「收不下」而从候选里剔掉，玩家看到的就是「明明有空位却不显示」。
                    for (int i = 0; i < limit; i++) {
                        ItemStack slot = patterns.getStackInSlot(i);
                        if (slot == null || slot.stackSize <= 0) {
                            if (patterns.isItemValidForSlot(i, pattern)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            } catch (Throwable ignored) {}
            return false;
        }

        /**
         * F1：把节点坐标拼到供应器展示名后，使同名机器可区分。
         * 旧实现只按机器名匹配，同名时命中哪一台取决于遍历顺序，是自动上传随机传错目标的根因。
         */
        private static String qualifyProviderName(String baseName, IGridNode node) {
            try {
                appeng.api.util.DimensionalCoord loc = node.getGridBlock()
                    .getLocation();
                if (loc != null) {
                    return baseName + " @D" + loc.getDimension() + " " + loc.x + "," + loc.y + "," + loc.z;
                }
            } catch (Throwable ignored) {}
            return baseName;
        }

        /**
         * F1/UI：供应器图标。取不到就返回 null，客户端会退化为纯文字行。
         * AE 接口取自身物品形态；GT 机器取机器方块；其余机器不强行猜测。
         */
        private static ItemStack resolveProviderIcon(Object machine) {
            try {
                if (machine instanceof appeng.parts.AEBasePart) {
                    ItemStack stack = ((appeng.parts.AEBasePart) machine).getItemStack();
                    if (stack != null) {
                        ItemStack copy = stack.copy();
                        copy.stackSize = 1;
                        return copy;
                    }
                }
                if (machine instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity) {
                    gregtech.api.interfaces.metatileentity.IMetaTileEntity mte =
                        ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) machine).getMetaTileEntity();
                    if (mte != null) {
                        ItemStack stack = mte.getStackForm(1L);
                        if (stack != null) {
                            ItemStack copy = stack.copy();
                            copy.stackSize = 1;
                            return copy;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return null;
        }

        /** 供应器样板槽总数（空槽 + 已占用），用于界面显示「空闲/总数」。 */
        private static int estimateTotalSlots(ICraftingProvider provider) {
            if (provider instanceof IInterfaceViewable viewable) {
                try {
                    IInventory patterns = viewable.getPatterns();
                    if (patterns != null) {
                        int availableSlots = viewable.rows() * viewable.rowSize();
                        return Math.min(availableSlots, patterns.getSizeInventory());
                    }
                } catch (Throwable ignored) {}
            }
            if (provider instanceof IInventory inv) {
                try {
                    return inv.getSizeInventory();
                } catch (Throwable ignored) {}
            }
            return 0;
        }

        /** 估算这批图标在包里的字节占用；含 null（1 字节标记）。 */
        private static int estimateEmptySlots(ICraftingProvider provider) {
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

        private static String resolveProviderName(Object machine) {
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
