#!/usr/bin/env bash
# Isolated handler contract tests, NOT Minecraft/NEI/Chromatic integration tests.
# Uses the actual handler and CountFormatter, with deliberately small dependency stubs.
# Usage (Git Bash): JAVA_HOME=/e/java17 bash docs/tooltip-fix42-regression.sh
set -euo pipefail
cd "$(dirname "$0")/.."
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
WORK=build/tooltip-fix42-regression
mkdir -p "$WORK/src" "$WORK/classes"
cd "$WORK/src"
mkdir -p net/minecraft/client/gui/inventory net/minecraft/item net/minecraftforge/fluids
mkdir -p codechicken/nei/guihook com/wztwzt/ae2_qof/client
cat > net/minecraft/client/gui/inventory/GuiContainer.java <<'JAVA'
package net.minecraft.client.gui.inventory;
public class GuiContainer {}
JAVA
cat > net/minecraft/item/ItemStack.java <<'JAVA'
package net.minecraft.item;
public class ItemStack {}
JAVA
cat > net/minecraftforge/fluids/Fluid.java <<'JAVA'
package net.minecraftforge.fluids;
public class Fluid {
    public final String name;
    public Fluid(String name) { this.name = name; }
}
JAVA
cat > net/minecraftforge/fluids/FluidStack.java <<'JAVA'
package net.minecraftforge.fluids;
public class FluidStack {
    private final Fluid fluid;
    public FluidStack(Fluid fluid, int amount) { this.fluid = fluid; }
    public String getLocalizedName() { return fluid.name; }
}
JAVA
cat > net/minecraftforge/fluids/FluidContainerRegistry.java <<'JAVA'
package net.minecraftforge.fluids;
public class FluidContainerRegistry { public static final int BUCKET_VOLUME = 1000; }
JAVA
cat > codechicken/nei/guihook/IContainerTooltipHandler.java <<'JAVA'
package codechicken.nei.guihook;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
public interface IContainerTooltipHandler {
    List<String> handleTooltip(GuiContainer g, int x, int y, List<String> lines);
    List<String> handleItemDisplayName(GuiContainer g, ItemStack s, List<String> lines);
    List<String> handleItemTooltip(GuiContainer g, ItemStack s, int x, int y, List<String> lines);
    Map<String, String> handleHotkeys(GuiContainer g, int x, int y, Map<String, String> keys);
}
JAVA
cat > com/wztwzt/ae2_qof/client/OverlayConfig.java <<'JAVA'
package com.wztwzt.ae2_qof.client;
public class OverlayConfig {
    public static boolean enabled = true;
    public static boolean isEnabled() { return enabled; }
}
JAVA
cat > com/wztwzt/ae2_qof/client/NetworkInventoryCache.java <<'JAVA'
package com.wztwzt.ae2_qof.client;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
public class NetworkInventoryCache {
    public static boolean valid = true;
    public static int queries;
    public static final Map<ItemStack, QueryResult> values = new IdentityHashMap<>();
    public static boolean hasData() { return valid; }
    public static QueryResult query(ItemStack stack) { queries++; return values.get(stack); }
    public static class QueryResult {
        public final long count;
        public final boolean craftable;
        public final Fluid fluid;
        public QueryResult(long count, boolean craftable, Fluid fluid) {
            this.count = count; this.craftable = craftable; this.fluid = fluid;
        }
    }
}
JAVA
cat > TooltipRegression.java <<'JAVA'
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import com.wztwzt.ae2_qof.client.*;
import com.wztwzt.ae2_qof.client.nei.NetworkTooltipHandler;
public class TooltipRegression {
    private static final NetworkTooltipHandler handler = new NetworkTooltipHandler();
    private static int checks;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }
    private static ItemStack item(long count, boolean craftable, Fluid fluid) {
        ItemStack s = new ItemStack();
        NetworkInventoryCache.values.put(s, new NetworkInventoryCache.QueryResult(count, craftable, fluid));
        return s;
    }
    private static List<String> tip(ItemStack s) {
        List<String> lines = new ArrayList<>();
        check(handler.handleItemTooltip(null, s, 0, 0, lines) == lines, "item list identity");
        return lines;
    }
    private static String plain(String s) { return s.replaceAll("\u00a7.", ""); }
    private static void line(ItemStack s, String expected) {
        int before = NetworkInventoryCache.queries;
        List<String> lines = tip(s);
        check(lines.size() == 1, "exactly one network line");
        check(plain(lines.get(0)).equals(expected), "format: " + expected);
        check(NetworkInventoryCache.queries == before + 1, "one merged query");
    }
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        ItemStack a = item(1200, true, null);
        ItemStack b = item(1200, true, null);
        List<String> generic = new ArrayList<>(Arrays.asList("existing base"));
        check(handler.handleTooltip(null, 0, 0, generic) == generic, "generic list identity");
        check(generic.equals(Arrays.asList("existing base")), "generic unchanged");
        check(NetworkInventoryCache.queries == 0, "generic never queries cache");
        check(handler.handleItemDisplayName(null, a, generic) == generic, "display name pass-through");
        Map<String, String> keys = new HashMap<>();
        keys.put("key", "hint");
        check(handler.handleHotkeys(null, 0, 0, keys) == keys && keys.size() == 1, "hotkeys pass-through");
        line(item(64, false, null), "64 AE");
        line(item(0, true, null), "+ Craft");
        line(a, "1.2K AE / + Craft");
        line(b, "1.2K AE / + Craft"); // no wait, distinct item with identical text
        line(a, "1.2K AE / + Craft"); // no wait, same item in the next tooltip
        check(tip(item(0, false, null)).isEmpty(), "neither stock nor craftable");
        check(tip(item(-1, false, null)).isEmpty(), "negative noncraftable count");
        line(item(-1, true, null), "+ Craft");
        Fluid water = new Fluid("Distilled Water");
        line(item(4500000000000000L, false, water), "4.5P mB Distilled Water");
        line(item(64000, true, water), "64K mB Distilled Water / + Craft");
        line(item(0, true, water), "+ Craft");
        line(item(Long.MAX_VALUE, false, null), "9.2E AE");
        int before = NetworkInventoryCache.queries;
        check(tip(null).isEmpty(), "null stack");
        OverlayConfig.enabled = false;
        check(tip(a).isEmpty(), "disabled overlay");
        OverlayConfig.enabled = true;
        NetworkInventoryCache.valid = false;
        check(tip(a).isEmpty(), "missing or expired cache gate");
        check(NetworkInventoryCache.queries == before, "gates avoid query");
        NetworkInventoryCache.valid = true;
        // Model only the inspected separate-list merge; this does not load Chromatic.
        List<String> base = new ArrayList<>(Arrays.asList("item title"));
        handler.handleTooltip(null, 0, 0, base);
        List<String> extra = new ArrayList<>(Arrays.asList("temporary title"));
        handler.handleItemTooltip(null, a, 0, 0, extra);
        extra.remove(0);
        base.addAll(extra);
        check(base.size() == 2 && base.get(0).equals("item title"), "separate-list merge adds one line");
        // Model native NEI's empty-generic-list branch.
        List<String> nativeLines = new ArrayList<>();
        handler.handleTooltip(null, 0, 0, nativeLines);
        check(nativeLines.isEmpty(), "native generic branch remains empty");
        nativeLines.add("item title");
        handler.handleItemTooltip(null, b, 0, 0, nativeLines);
        check(nativeLines.size() == 2, "native item path adds one line");
        System.out.println("PASS: " + checks + " isolated handler assertions (dependency stubs, not game integration)");
    }
}
JAVA
cd ../../..
"$JAVAC" --release 8 -encoding UTF-8 -d "$WORK/classes" \
    "$WORK/src/net/minecraft/client/gui/inventory/GuiContainer.java" \
    "$WORK/src/net/minecraft/item/ItemStack.java" \
    "$WORK/src/net/minecraftforge/fluids/"*.java \
    "$WORK/src/codechicken/nei/guihook/IContainerTooltipHandler.java" \
    "$WORK/src/com/wztwzt/ae2_qof/client/"*.java \
    src/main/java/com/wztwzt/ae2_qof/util/CountFormatter.java \
    src/main/java/com/wztwzt/ae2_qof/client/nei/NetworkTooltipHandler.java \
    "$WORK/src/TooltipRegression.java"
"$JAVA" -version
"$JAVA" -cp "$WORK/classes" TooltipRegression
