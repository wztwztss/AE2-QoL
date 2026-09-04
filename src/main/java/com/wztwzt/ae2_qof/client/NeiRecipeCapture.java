package com.wztwzt.ae2_qof.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.client.event.MergedTerminalPanelHandler;
import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.merged.GuiMergedTerminal;
import com.wztwzt.ae2_qof.network.MergedTerminalActionPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IRecipeHandler;

/**
 * 捕获并提取当前 NEI 配方视图（GuiRecipe）中的输入/输出，供二合一终端"一键填充"使用。
 * 玩家在 NEI 配方页浏览配方后，面板 NEI 按钮读取最近浏览的配方填充编码格。
 */
public final class NeiRecipeCapture {

    private static volatile IRecipeHandler lastHandler;
    private static volatile int lastRecipeIndex = -1;

    private NeiRecipeCapture() {}

    /** 每次 GuiRecipe.updateScreen 调用，记录当前浏览的配方 handler 与页码 */
    public static void captureFromGui(GuiRecipe<?> gui) {
        try {
            IRecipeHandler handler = gui.getHandler();
            List<Integer> indices = gui.getRecipeIndices();
            if (handler == null || indices == null || indices.isEmpty()) {
                return;
            }
            int page = Math.max(0, Math.min(gui.page, indices.size() - 1));
            lastHandler = handler;
            lastRecipeIndex = indices.get(page);
            captureGTRecipeMap(handler);
        } catch (Throwable ignored) {}
    }

    public static boolean hasCapturedRecipe() {
        return lastHandler != null && lastRecipeIndex >= 0;
    }

    /**
     * 提取最近浏览配方的输入/输出，并判定合成模式。
     */
    public static RecipeData extractCurrentRecipe() {
        RecipeData data = new RecipeData();
        if (!hasCapturedRecipe()) {
            return data;
        }
        extractFrom(lastHandler, lastRecipeIndex, data);
        return data;
    }

    /**
     * 把指定配方直接填入合并终端面板：提取输入/输出 → 设置合成/处理模式 → 发送 FILL 包。
     * 供 NEI「+」覆盖层（含不按 Shift 的单击）直传使用。
     *
     * @return 是否成功填入
     */
    public static boolean fillMergedTerminal(GuiContainer gui, IRecipeHandler handler, int recipeIndex) {
        if (gui == null || handler == null) {
            return false;
        }
        return fillMergedTerminal(gui, extractFrom(handler, recipeIndex));
    }

    /** 使用最近浏览捕获的配方直填合并终端（供 GuiOverlayButton「+」单击直传使用）。 */
    public static boolean fillMergedTerminalFromCapture(GuiContainer gui) {
        if (gui == null || !hasCapturedRecipe()) {
            return false;
        }
        return fillMergedTerminal(gui, extractCurrentRecipe());
    }

    private static boolean fillMergedTerminal(GuiContainer gui, RecipeData data) {
        if (data == null || !data.valid) {
            return false;
        }
        MergedTerminalPanelHandler.mergedCraftingMode = data.crafting;
        if (gui.inventorySlots instanceof IMergedPatternTerminal merged) {
            merged.setMergedCraftingMode(data.crafting);
        }
        // 处理配方：随 FILL 包携带客户端已识别的配方池 id，服务端写入样板并用于映射判定
        String recipeMap = data.crafting ? null : ClientState.pendingRecipeMap;
        ModNetwork.CHANNEL.sendToServer(
            MergedTerminalActionPacket.fill(data.inputs, data.outputs, data.crafting, data.cells, recipeMap));
        // 自动把机器名填入搜索框，过滤出刚填充的机器
        if (!data.crafting && recipeMap != null && !recipeMap.isEmpty()) {
            ClientState.lastRecipeMap = recipeMap;
            String resolved = RecipeMapNameConfig.resolveSearchKeyword(recipeMap);
            // 映射表查不到时，用 NEI 的 getRecipeName() 中文兜底
            if (resolved == null || resolved.equals(recipeMap)) {
                resolved = ClientState.pendingRecipeCnName;
            }
            if (resolved != null && !resolved.isEmpty()) {
                GuiMergedTerminal.setSearchFieldText(resolved);
            }
        }
        return true;
    }

    /** 从指定配方 handler 的指定配方页提取输入/输出（供 NEI 覆盖层「+」直传使用）。 */
    public static RecipeData extractFrom(IRecipeHandler handler, int recipeIndex) {
        RecipeData data = new RecipeData();
        if (handler == null || recipeIndex < 0) {
            return data;
        }
        extractFrom(handler, recipeIndex, data);
        return data;
    }

    private static void extractFrom(IRecipeHandler handler, int recipeIndex, RecipeData data) {
        try {
            List<ItemStack> ins = new ArrayList<>();
            List<ItemStack> outs = new ArrayList<>();
            List<Integer> cellList = new ArrayList<>();

            List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
            if (ingredients != null) {
                for (PositionedStack ps : ingredients) {
                    ItemStack item = pickStack(ps);
                    if (item != null) {
                        ins.add(item.copy());
                        cellList.add(cellFromPos(ps.relx, ps.rely));
                    }
                }
            }

            PositionedStack result = handler.getResultStack(recipeIndex);
            if (result != null) {
                ItemStack item = pickStack(result);
                if (item != null) {
                    outs.add(item.copy());
                }
            }

            List<PositionedStack> other = handler.getOtherStacks(recipeIndex);
            if (other != null) {
                for (PositionedStack ps : other) {
                    ItemStack item = pickStack(ps);
                    if (item != null) {
                        outs.add(item.copy());
                    }
                }
            }

            // PH 编程工具箱 MK.II：自动把不消耗催化剂替换为编程电路（含归零兜底）
            applyProgrammingToolkit(ins);

            if (ins.size() > 27) {
                ins.subList(27, ins.size())
                    .clear();
                if (cellList.size() > 27) {
                    cellList.subList(27, cellList.size())
                        .clear();
                }
            }
            if (outs.size() > 9) {
                outs.subList(9, outs.size())
                    .clear();
            }

            data.inputs = ins.toArray(new ItemStack[0]);
            data.outputs = outs.toArray(new ItemStack[0]);
            // 分类判定：以 NEI 配方类目为准——工作台配方（有序/无序）的 overlay identifier 均为 "crafting"。
            // 不再用 CraftingManager.findMatchingRecipe 反查：同一组输入可能命中多个配方且返回第一个，
            // 会导致工作台配方被随机误判成其他配方（如 6 石头→石楼梯 被识别成 GT 机器配方）。
            data.crafting = "crafting".equals(handler.getOverlayIdentifier());
            // 处理配方：GT 类配方的 overlay identifier 即配方池 id（如 gt.recipe.blastfurnace），
            // 记录到 pendingRecipeMap 供 FILL 包携带与服务端写入样板/映射判定。
            if (!data.crafting) {
                String id = handler.getOverlayIdentifier();
                if (id != null && !id.isEmpty()) {
                    ClientState.pendingRecipeMap = id;
                }
                // 捕获 NEI 配方处理器的中文名称，供搜索框自动填入兜底
                try {
                    String cnName = handler.getRecipeName();
                    if (cnName != null && !cnName.trim()
                        .isEmpty()) {
                        ClientState.pendingRecipeCnName = cnName.trim();
                    }
                } catch (Throwable ignored) {}
            }
            // 合成配方记录格子位置（3×3），供服务端按形状填入；无效位置退化为顺序填充
            if (data.crafting && cellList.size() == data.inputs.length) {
                data.cells = new int[cellList.size()];
                for (int i = 0; i < cellList.size(); i++) {
                    data.cells[i] = cellList.get(i);
                }
            } else {
                data.cells = null;
            }
            data.valid = data.inputs.length > 0 && data.outputs.length > 0;
        } catch (Throwable t) {
            data.valid = false;
        }
    }

    private static ItemStack pickStack(PositionedStack ps) {
        if (ps == null) {
            return null;
        }
        if (ps.item != null) {
            return ps.item;
        }
        if (ps.items != null && ps.items.length > 0) {
            return ps.items[0];
        }
        return null;
    }

    // ===== PH 编程工具箱 MK.II 适配 =====

    /** PH 模组类可用性缓存（null=未初始化） */
    private static Boolean phAvailable;
    private static java.lang.reflect.Method phHolding;
    private static java.lang.reflect.Method phAddEmpty;
    private static java.lang.reflect.Method phWrap;

    private static void initPhReflection() {
        try {
            Class<?> toolkit = Class.forName("reobf.proghatches.item.ItemProgrammingToolkit");
            Class<?> circuit = Class.forName("reobf.proghatches.item.ItemProgrammingCircuit");
            phHolding = toolkit.getMethod("holding");
            phAddEmpty = toolkit.getMethod("addEmptyProgCiruit");
            phWrap = circuit.getMethod("wrap", ItemStack.class);
            phAvailable = true;
        } catch (Throwable t) {
            phAvailable = false;
        }
    }

    private static boolean phHolding() {
        if (phAvailable == null) initPhReflection();
        if (!phAvailable) return false;
        try {
            return (Boolean) phHolding.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean phAddEmpty() {
        try {
            return (Boolean) phAddEmpty.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static ItemStack phWrap(ItemStack catalyst) {
        try {
            return (ItemStack) phWrap.invoke(null, catalyst);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * PH 编程工具箱 MK.II 自动逻辑（与原生样板终端行为一致）：
     * 输入中 stackSize==0 的项视为不消耗催化剂 → 替换为对应编程电路；
     * 无催化剂且工具箱处于兜底模式时追加归零电路。电路置于输入列表最前。
     */
    private static void applyProgrammingToolkit(List<ItemStack> ins) {
        if (!phHolding()) return;
        List<ItemStack> circuits = new ArrayList<>();
        boolean hasCatalyst = false;
        java.util.Iterator<ItemStack> it = ins.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (s != null && s.stackSize == 0) {
                hasCatalyst = true;
                it.remove();
                ItemStack c = phWrap(s.copy());
                if (c != null) circuits.add(c);
            }
        }
        if (!hasCatalyst && phAddEmpty()) {
            ItemStack zero = phWrap(null);
            if (zero != null) circuits.add(zero);
        }
        for (int i = circuits.size() - 1; i >= 0; i--) {
            ins.add(0, circuits.get(i));
        }
    }

    /**
     * 由 NEI 配方展示坐标换算 3×3 合成格索引（0-8）。
     * NEI 合成格标准布局：格子中心 x=25/43/61，y=6/24/42，格距 18。
     * 非合成格布局（如 GT 机器 4×4 网格）返回 -1，调用方退化为顺序填充。
     */
    private static int cellFromPos(int relx, int rely) {
        int cx = (relx - 25) / 18;
        int cy = (rely - 6) / 18;
        if (cx < 0 || cx > 2 || cy < 0 || cy > 2) {
            return -1;
        }
        return cy * 3 + cx;
    }

    private static void captureGTRecipeMap(IRecipeHandler handler) {
        try {
            Class<?> handlerClass = handler.getClass();
            String className = handlerClass.getName();
            if (!className.contains("gregtech") && !className.contains("GTNEI")) {
                return;
            }
            java.lang.reflect.Method getRecipeMapMethod = null;
            try {
                getRecipeMapMethod = handlerClass.getMethod("getRecipeMap");
            } catch (NoSuchMethodException e) {
                Class<?> superClass = handlerClass.getSuperclass();
                while (superClass != null) {
                    try {
                        getRecipeMapMethod = superClass.getMethod("getRecipeMap");
                        break;
                    } catch (NoSuchMethodException ex) {
                        superClass = superClass.getSuperclass();
                    }
                }
            }
            if (getRecipeMapMethod != null) {
                Object recipeMap = getRecipeMapMethod.invoke(handler);
                if (recipeMap != null) {
                    String mapName = (String) recipeMap.getClass()
                        .getField("unlocalizedName")
                        .get(recipeMap);
                    if (mapName != null && !mapName.isEmpty()) {
                        ClientState.pendingRecipeMap = mapName;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static class RecipeData {

        public ItemStack[] inputs = new ItemStack[0];
        public ItemStack[] outputs = new ItemStack[0];
        /** 合成模式下每个输入对应的 3×3 格子索引（0-8），null 表示处理配方或未知布局 */
        public int[] cells = null;
        public boolean crafting = false;
        public boolean valid = false;
    }
}
