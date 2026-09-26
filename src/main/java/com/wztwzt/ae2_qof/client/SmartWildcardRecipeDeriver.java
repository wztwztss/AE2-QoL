package com.wztwzt.ae2_qof.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.enums.OrePrefixes;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * 「从 NEI 当前配方推导通配规则」的推导器（3.22.0 M2-B，纯客户端逻辑，无副作用）。
 *
 * <h2>为什么放在客户端</h2>
 * NEI 的配方数据只在客户端有（{@code IRecipeHandler} 是客户端类）；但**写 NBT 一律在服务端**
 * （见 {@code SmartWildcardRulesPacket}）⇒ 这里只产出纯数据：模板 in/out + 规则 + 摘要。
 *
 * <h2>推导规则（逐条都有依据）</h2>
 * <ol>
 * <li>输入取 {@code handler.getIngredientStacks(recipeIndex)}；输出取 {@code getOtherStacks} + {@code getResultStack}
 * —— GT 的 {@code getResultStack()} **恒为 null**（GTNEIDefaultHandler:942-945），只读它会得到“没有输出”；</li>
 * <li><b>跳过流体幻影</b>：GT 把流体塞成 {@code FixedPositionedStack implements GTNEIDefaultHandler.IFluidAlternativeStack}
 * （GTNEIDefaultHandler:571/605）⇒ 不跳过就会拿流体占位物品去推矿辞；</li>
 * <li><b>识别编程电路</b>：判据用 GT 自己的写法 {@code getUnlocalizedName().startsWith("gt.integrated_circuit")}
 * （MTEMultiBlockBase:2019-2021），电路号 = {@code getItemDamage()}（GTUtility.getIntegratedCircuit，上限 24）；</li>
 * <li>矿辞优先：{@code OrePrefixes.detectPrefix(ItemStack)}（GT 权威，按 VALUES 最长前缀匹配）⇒ 规则写成 {@code prefix*}；
 * 取不到矿辞才退化为“显示名精确匹配”（并在摘要里计数，便于用户判断要不要手改）；</li>
 * <li>规则槽位 = 该项在**模板 in 列表里的下标**（展开器就是按这个下标替换的）。</li>
 * </ol>
 */
public final class SmartWildcardRecipeDeriver {

    /** 推导结果：纯数据，可直接进包/进界面。 */
    public static final class Result {

        public final SmartWildcardState state = new SmartWildcardState();
        public final List<ItemStack> templateIn = new ArrayList<>();
        public final List<ItemStack> templateOut = new ArrayList<>();
        public String summary = "";
        public boolean ok;
        public String reason = "";
    }

    private SmartWildcardRecipeDeriver() {}

    public static Result derive(IRecipeHandler handler, int recipeIndex, World world) {
        Result result = new Result();
        if (handler == null) {
            result.reason = "no-handler";
            return result;
        }
        try {
            List<PositionedStack> inputs = handler.getIngredientStacks(recipeIndex);
            List<PositionedStack> outputs = new ArrayList<>();
            List<PositionedStack> other = handler.getOtherStacks(recipeIndex);
            if (other != null) {
                for (PositionedStack stack : other) {
                    if (stack != null) outputs.add(stack);
                }
            }
            PositionedStack primary = handler.getResultStack(recipeIndex);
            if (primary != null) outputs.add(primary);

            int oreRules = 0;
            int nameRules = 0;
            int skippedFluid = 0;
            int skippedEmpty = 0;

            if (inputs != null) {
                for (PositionedStack positioned : inputs) {
                    if (positioned == null) continue;
                    // ② 流体幻影：GT 用它把流体伪装成物品输入
                    if (positioned instanceof GTNEIDefaultHandler.IFluidAlternativeStack) {
                        skippedFluid++;
                        continue;
                    }
                    ItemStack stack = pickStack(positioned);
                    if (stack == null || stack.getItem() == null) {
                        skippedEmpty++;
                        continue;
                    }
                    // 模板：按原样保留（含电路——AE2 样板里带上电路，机器才能匹配到该配方）
                    int slot = result.templateIn.size();
                    ItemStack templateStack = stack.copy();
                    templateStack.stackSize = Math.max(1, stack.stackSize);
                    result.templateIn.add(templateStack);

                    // ③ 编程电路：记进 state.circuit，并且**仍然保留在模板输入里**。
                    // 两条路互补（纠正此前"电路不入样板"的判断——那会让 AE2 ME 接口这类不写幽灵电路槽的宿主
                    // 完全没有电路）：
                    //   ・模板里带电路 = GTNH 样板惯例，保证任何总成/机器都能匹配到该配方；
                    //   ・机器索引期再由 SmartWildcardCircuit 把同一个号码写进虚拟电路槽，让机器直接读取。
                    String unlocalized = stack.getItem()
                        .getUnlocalizedName();
                    if (unlocalized != null && unlocalized.startsWith("gt.integrated_circuit")) {
                        result.state.circuit = stack.getItemDamage();
                        continue;
                    }

                    // ④ 矿辞优先
                    String matcher = null;
                    try {
                        List<OrePrefixes.ParsedOreDictName> parsed = OrePrefixes.detectPrefix(stack);
                        if (parsed != null) {
                            for (OrePrefixes.ParsedOreDictName name : parsed) {
                                if (name == null || name.prefix == null) continue;
                                String key = name.prefix.getOreprefixKey();
                                if (key == null || key.isEmpty() || name.material == null || name.material.isEmpty())
                                    continue;
                                matcher = key + "*";
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        MyMod.LOG.warn("[AE2QoL] 推导时读取矿辞前缀失败（按名称匹配处理）", t);
                    }
                    if (matcher != null) {
                        result.state.rules.add(new SmartWildcardState.Rule(slot, true, matcher, templateStack.stackSize));
                        oreRules++;
                    } else {
                        // 退化：显示名精确匹配（摘要里会计数，用户可在界面里手改成通配串）
                        String display = String.valueOf(
                            stack.getItem()
                                .getItemStackDisplayName(stack));
                        result.state.rules.add(
                            new SmartWildcardState.Rule(slot, false, display, templateStack.stackSize));
                        nameRules++;
                    }
                }
            }

            for (PositionedStack positioned : outputs) {
                if (positioned == null) continue;
                if (positioned instanceof GTNEIDefaultHandler.IFluidAlternativeStack) continue;
                ItemStack stack = pickStack(positioned);
                if (stack == null || stack.getItem() == null) continue;
                ItemStack templateStack = stack.copy();
                templateStack.stackSize = Math.max(1, stack.stackSize);
                result.templateOut.add(templateStack);
            }

            result.summary = "in=" + result.templateIn.size()
                + " out="
                + result.templateOut.size()
                + " oreRules="
                + oreRules
                + " nameRules="
                + nameRules
                + " skippedFluid="
                + skippedFluid
                + " skippedEmpty="
                + skippedEmpty
                + " circuit="
                + result.state.circuit;
            result.ok = !result.templateIn.isEmpty();
            if (!result.ok) result.reason = "no-inputs";
            return result;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 从 NEI 配方推导规则失败", t);
            result.ok = false;
            result.reason = "exception: " + t;
            return result;
        }
    }

    /** 取该配方格的代表物品（与 {@code NeiRecipeCapture.pickStack} 同口径：先当前 item，再候选数组）。 */
    public static ItemStack pickStack(PositionedStack positioned) {
        if (positioned == null) return null;
        if (positioned.item != null && positioned.item.getItem() != null) return positioned.item;
        if (positioned.items != null) {
            for (ItemStack candidate : positioned.items) {
                if (candidate != null && candidate.getItem() != null) return candidate;
            }
        }
        return null;
    }

    /** 把模板 in/out 写成 AE2 样板 NBT 的两个列表（与展开器写候选用同一套键：in/out + Count/Cnt）。 */
    public static NBTTagList toPatternList(List<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            stack.writeToNBT(tag);
            tag.setInteger("Count", Math.max(1, stack.stackSize));
            tag.setLong("Cnt", Math.max(1, stack.stackSize));
            list.appendTag(tag);
        }
        return list;
    }
}
