package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.IInterfaceViewable;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
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
                if (!(container instanceof ContainerPatternTerm) && !(container instanceof ContainerPatternTermEx)) {
                    return;
                }

                IActionHost terminal = resolveTerminal(container);
                if (terminal == null) {
                    return;
                }

                IGridNode node = terminal.getActionableNode();
                if (node == null) {
                    return;
                }

                IGrid grid = node.getGrid();
                if (grid == null) {
                    return;
                }

                // 检测配方池：优先使用客户端直接提供的 recipeMap
                String recipeMap = message.directRecipeMap;
                if (recipeMap == null || recipeMap.isEmpty()) {
                    recipeMap = detectRecipeMap(
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

                ModNetwork.CHANNEL
                    .sendTo(new ProvidersListS2CPacket(ids, names, emptySlots, recipeMap, message.forceGui), player);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        /** 每个玩家全量反射扫描 GT 配方池的冷却时间（毫秒），防恶意连发卡服 */
        private static final long SCAN_COOLDOWN_MS = 3000L;
        private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_SCAN_TIMES =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();
        private static final java.util.concurrent.ConcurrentHashMap<String, String> LAST_RECIPE_MAPS =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

        private String detectRecipeMap(ItemStack[] inputs, ItemStack[] outputs, String playerKey) {
            if (playerKey != null) {
                long now = System.currentTimeMillis();
                Long last = LAST_SCAN_TIMES.get(playerKey);
                if (last != null && now - last < SCAN_COOLDOWN_MS) {
                    // 冷却期内复用上次结果，避免全量反射扫描
                    return LAST_RECIPE_MAPS.get(playerKey);
                }
                LAST_SCAN_TIMES.put(playerKey, now);
            }
            String detected = scanRecipeMaps(inputs, outputs);
            if (playerKey != null) {
                LAST_RECIPE_MAPS.put(playerKey, detected);
            }
            return detected;
        }

        private String scanRecipeMaps(ItemStack[] inputs, ItemStack[] outputs) {
            if (inputs == null || inputs.length == 0) {
                System.out.println("[APU] detectRecipeMap: no inputs");
                return null;
            }

            System.out.println(
                "[APU] detectRecipeMap: inputs=" + inputs.length
                    + ", outputs="
                    + (outputs != null ? outputs.length : 0));
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] != null) {
                    System.out
                        .println("[APU]   input[" + i + "]=" + inputs[i].getDisplayName() + " x" + inputs[i].stackSize);
                }
            }

            try {
                // 通过反射获取 RecipeMap.ALL_RECIPE_MAPS
                Class<?> recipeMapClass = Class.forName("gregtech.api.recipe.RecipeMap");
                System.out.println("[APU] RecipeMap class found: " + recipeMapClass.getName());

                java.lang.reflect.Field allMapsField = recipeMapClass.getField("ALL_RECIPE_MAPS");
                java.util.Map<?, ?> allMaps = (java.util.Map<?, ?>) allMapsField.get(null);
                System.out.println("[APU] ALL_RECIPE_MAPS size: " + allMaps.size());

                // 获取 findRecipeQuery() 方法
                java.lang.reflect.Method findRecipeQueryMethod = recipeMapClass.getMethod("findRecipeQuery");
                // 获取 FindRecipeQuery 的 items() 和 find() 方法
                Class<?> queryClass = Class.forName("gregtech.api.recipe.FindRecipeQuery");
                java.lang.reflect.Method itemsMethod = queryClass.getMethod("items", ItemStack[].class);
                java.lang.reflect.Method findMethod = queryClass.getMethod("find");

                for (Object map : allMaps.values()) {
                    try {
                        // 获取配方池名字用于日志
                        java.lang.reflect.Field nameField = recipeMapClass.getField("unlocalizedName");
                        String mapName = (String) nameField.get(map);

                        Object query = findRecipeQueryMethod.invoke(map);
                        query = itemsMethod.invoke(query, (Object) inputs);
                        Object recipe = findMethod.invoke(query);

                        if (recipe != null) {
                            System.out.println("[APU] Detected recipe map: " + mapName);
                            return mapName;
                        }
                    } catch (Throwable t) {
                        // 单个配方池查找失败，继续下一个
                    }
                }
                System.out.println("[APU] No recipe found in any map");
            } catch (Throwable t) {
                System.out.println("[APU] detectRecipeMap error: " + t.getMessage());
                t.printStackTrace();
            }
            return null;
        }

        private IActionHost resolveTerminal(Container container) {
            if (container instanceof ContainerPatternTerm term) {
                return (IActionHost) term.getPatternTerminal();
            }
            if (container instanceof ContainerPatternTermEx termEx) {
                return (IActionHost) termEx.getPatternTerminal();
            }
            return null;
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
