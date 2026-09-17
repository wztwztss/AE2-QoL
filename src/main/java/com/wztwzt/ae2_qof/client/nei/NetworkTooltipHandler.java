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
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.Loader;

/**
 * NEI 物品悬浮提示：在 tooltip 中追加该物品在 AE2 网络中的存量与可合成状态。
 * 数据来自 NetworkInventoryCache（由终端 postUpdate 混入填充）。
 */
public class NetworkTooltipHandler implements IContainerTooltipHandler {

    /**
     * 3.19.0-fix24：chromatictooltips(compat) 会在 GuiContainerManager.renderToolTips 的 HEAD
     * 注入并 cancel 掉原流程，只遍历 handleTooltip 回调，之后自行渲染物品 tooltip。
     * 原生 NEI 的 handleItemTooltip 通道在该 mod 存在时完全不执行，本模组的存量提示因此整体失效。
     */
    private static final boolean CHROMATIC_TOOLTIPS_COMPAT = Loader.isModLoaded("chromatictooltipscompat");

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currentTip) {
        // 仅在 chromatictooltips(compat) 接管渲染时走这条通道：
        // 原生 NEI 中 handleTooltip 的返回值非空会跳过物品名渲染，绝不能无条件写入。
        if (!CHROMATIC_TOOLTIPS_COMPAT) {
            return currentTip;
        }
        try {
            if (gui == null || !GuiContainerManager.shouldShowTooltip(gui)) {
                return currentTip;
            }
            appendNetworkLine(GuiContainerManager.getStackMouseOver(gui), currentTip);
        } catch (Throwable ignored) {}
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
            appendNetworkLine(itemstack, currentTip);
        } catch (Throwable ignored) {}
        return currentTip;
    }

    /**
     * 单次合并查询（#52）：count/craftable/fluid 一并返回，避免 3 遍流体识别。
     * 追加一行「存量 / 可合成」描述；无数据或开关关闭时不产生任何行。
     */
    private static void appendNetworkLine(ItemStack itemstack, List<String> currentTip) {
        if (!OverlayConfig.isEnabled() || itemstack == null || !NetworkInventoryCache.hasData()) {
            return;
        }
        NetworkInventoryCache.QueryResult r = NetworkInventoryCache.query(itemstack);
        long count = r.count;
        boolean craftable = r.craftable;
        if (count <= 0 && !craftable) {
            return;
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
        currentTip.add(sb.toString());
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }
}
