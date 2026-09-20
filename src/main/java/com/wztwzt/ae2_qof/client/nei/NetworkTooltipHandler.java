package com.wztwzt.ae2_qof.client.nei;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.wztwzt.ae2_qof.client.NetworkInventoryCache;
import com.wztwzt.ae2_qof.client.OverlayConfig;
import com.wztwzt.ae2_qof.util.CountFormatter;

import codechicken.nei.guihook.IContainerTooltipHandler;

/**
 * NEI 物品悬浮提示：在 tooltip 中追加该物品在 AE2 网络中的存量与可合成状态。
 * 数据来自 NetworkInventoryCache（由终端 postUpdate 混入填充）。
 */
public class NetworkTooltipHandler implements IContainerTooltipHandler {

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currentTip) {
        // fix42：Chromatic Compat 会通过 ContextInfoEnricher 再调用 handleItemTooltip。
        // 此处不追加，统一由物品回调处理，避免两份独立列表合并后出现重复行。
        return currentTip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currentTip) {
        return currentTip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
        List<String> currentTip) {
        try {
            String line = buildNetworkLine(itemstack);
            if (line == null) {
                return currentTip;
            }
            currentTip.add(line);
        } catch (Throwable ignored) {}
        return currentTip;
    }

    /**
     * 单次合并查询（#52）：count/craftable/fluid 一并返回，避免 3 遍流体识别。
     * 生成一行「存量 / 可合成」描述；无数据、开关关闭或两者皆无时返回 null。
     */
    private static String buildNetworkLine(ItemStack itemstack) {
        if (!OverlayConfig.isEnabled() || itemstack == null || !NetworkInventoryCache.hasData()) {
            return null;
        }
        NetworkInventoryCache.QueryResult r = NetworkInventoryCache.query(itemstack);
        long count = r.count;
        boolean craftable = r.craftable;
        if (count <= 0 && !craftable) {
            return null;
        }
        FluidStack fluid = r.fluid != null
            ? new FluidStack(r.fluid, net.minecraftforge.fluids.FluidContainerRegistry.BUCKET_VOLUME)
            : null;
        StringBuilder sb = new StringBuilder("\u00a77");
        if (count > 0) {
            if (fluid != null) {
                sb.append("\u00a7b\u00a7l")
                    .append(CountFormatter.format(count))
                    .append("\u00a77\u00a7r \u00a77mB ")
                    .append(fluid.getLocalizedName());
            } else {
                sb.append("\u00a7b\u00a7l")
                    .append(CountFormatter.format(count))
                    .append("\u00a77\u00a7r \u00a77AE");
            }
        }
        if (craftable) {
            if (count > 0) {
                sb.append(" \u00a77/\u00a7r ");
            }
            sb.append("\u00a72\u00a7l+")
                .append("\u00a77\u00a7r \u00a77Craft");
        }
        return sb.toString();
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }
}