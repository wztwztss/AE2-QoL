/*
 * 本文件参考 AE2Things 1.3.0（作者 asdflj，GPL-3.0）的样板编码路径：
 * PatternContainer.encode() → IPatternTerminal.encode() → PatternEncodingHelper。
 * 本仓库整体以 GPL-3.0 发布，保留原作者版权声明。
 */
package com.wztwzt.ae2_qof.merged;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.parts.IPatternTerminal;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternEncodingHelper;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.Platform;

/**
 * 1.3.0 编码桥：把本模组的面板网格转换成 AE2 原生样板终端所需的
 * {@link IAEStackInventory}，再调用 {@link PatternEncodingHelper#encode}。
 * <p>
 * AE2Things 1.3.0 的 {@code PatternContainer.encode()} 最终也是走
 * {@code IPatternTerminal.encode(...)} → {@code PatternEncodingHelper}，
 * 因此这里不再使用旧的手搓 {@code encodeItemPattern}/{@code encodeFluidPattern}。
 */
public final class AE2PatternEncoder {

    private AE2PatternEncoder() {}

    public static boolean encode(PatternContainer patternContainer, ContainerMergedTerminal container) {
        if (!Platform.isServer()) return false;
        EntityPlayer player = container.getPlayerInv().player;
        if (player == null) return false;

        ItemStack[] inputStacks = patternContainer.getEncodingInputs();
        ItemStack[] outputStacks = patternContainer.getEncodingOutputs();
        IAEStackInventory inputs = new IAEStackInventory(
            new InventoryOwner(),
            inputStacks.length,
            StorageName.CRAFTING_INPUT);
        IAEStackInventory outputs = new IAEStackInventory(
            new InventoryOwner(),
            outputStacks.length,
            StorageName.CRAFTING_OUTPUT);

        copyToAEInventory(inputStacks, inputs);
        copyToAEInventory(outputStacks, outputs);

        IPatternTerminal terminal = createTerminal(patternContainer, container, inputs, outputs);
        try {
            IGrid grid = container.getGrid();
            if (grid == null) return false;
            IStorageGrid storageGrid = (IStorageGrid) grid.getCache(IStorageGrid.class);
            IMEMonitor<IAEItemStack> monitor = storageGrid == null ? null : storageGrid.getItemInventory();
            IEnergySource power = container.getPowerSource();
            if (power == null) {
                IEnergyGrid energyGrid = (IEnergyGrid) grid.getCache(IEnergyGrid.class);
                if (energyGrid != null) {
                    power = new ChannelPowerSrc(container.getAnchor().getActionableNode(), energyGrid);
                }
            }
            BaseActionSource action = container.getActionSource();
            if (action == null) action = new PlayerSource(player, container.getAnchor());

            boolean encoded = PatternEncodingHelper.encode(
                terminal,
                power,
                monitor,
                action,
                player.getCommandSenderName(),
                player.worldObj);
            if (encoded) {
                patternContainer.getPatternInv().markDirty();
                MyMod.LOG.info("[Encode] AE2 native pattern encoder OK");
            }
            return encoded;
        } catch (Throwable t) {
            MyMod.LOG.warn("AE2 native pattern encoding failed", t);
            return false;
        }
    }

    private static IPatternTerminal createTerminal(PatternContainer patternContainer,
        ContainerMergedTerminal container, IAEStackInventory inputs, IAEStackInventory outputs) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getInventoryByName":
                    if ("pattern".equals(args[0])) return patternContainer.getPatternInv();
                    if ("crafting".equals(args[0])) return patternContainer.getCraftingInv();
                    return null;
                case "getAEInventoryByName":
                    if (args[0] == StorageName.CRAFTING_INPUT) return inputs;
                    if (args[0] == StorageName.CRAFTING_OUTPUT) return outputs;
                    return null;
                case "isCraftingRecipe":
                    return patternContainer.isCraftingMode();
                case "isSubstitution":
                    return patternContainer.isSubstitute();
                case "canBeSubstitution":
                    return patternContainer.isBeSubstitute();
                case "getPatternEncodeListeners":
                    return Collections.emptyList();
                case "addPatternEncodeListeners":
                case "removePatternEncodeListeners":
                    return null;
                case "getActionableNode":
                    return container.getAnchor().getActionableNode();
                case "getGrid":
                    return container.getGrid();
                case "saveAEStackInv":
                case "saveChanges":
                case "onChangeInventory":
                    return null;
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IPatternTerminal) Proxy.newProxyInstance(
            AE2PatternEncoder.class.getClassLoader(),
            new Class<?>[] { IPatternTerminal.class },
            handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static void copyToAEInventory(ItemStack[] source, IAEStackInventory target) {
        for (int i = 0; i < target.getSizeInventory(); i++) {
            target.putAEStackInSlot(i, PatternContainer.convertToAEStack(i < source.length ? source[i] : null));
        }
    }

    /** IAEStackInventory 需要一个 owner 回调；这里只用于临时读写，空实现即可。 */
    private static final class InventoryOwner implements IIAEStackInventory {

        @Override
        public void saveAEStackInv() {}

        @Override
        public IAEStackInventory getAEInventoryByName(StorageName name) {
            return null;
        }
    }
}
