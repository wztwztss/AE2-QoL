# WildcardPatternforGTNH 1.1.0 只读取证报告

源码根：`E:\wzt\MC\modcreater\reference_src_290b3\WildcardPatternforGTNH-1.7.10-1.1.0`
文中相对路径基准 = 该目录。依赖源码基准：
- AE2 `Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH`
- GT5U `GT5-Unofficial-5.09.54.133`
- ModularUI2 `ModularUI2-2.3.88-1.7.10`

所有列出文件均**存在**（无"未找到"项）。全文只做取证，无修改建议、无补丁。

---

## 结论速览

核心机制**不是**"让 AE2 原生理解通配符"，而是：

1. 用 `ItemEncodedPattern` 的子类伪装成普通编码样板，保证任何以 `ICraftingPatternItem` 判定的宿主（AE2 接口 / GT ME 输入仓）都会接受它；
2. 在宿主建立样板索引的入口（`DualityInterface.addToCraftingList` / `MTEHatchCraftingInputME.provideCrafting`）把它们**截胡**；
3. 把一条"规则"按矿辞/名称展开成材料候选集，对每个候选**克隆模板 ItemStack 并改写 `in`/`out` NBT**，再交给 AE2 原生 `PatternHelper` 构造 —— 于是"一张样板"变成**几百上千张各自合法的普通样板**；
4. `WildcardPatternDetails` 本身只是 `PatternHelper` 的零逻辑转发壳。

---

## Q1 通配样板物品怎么注册、继承了谁、为什么处处被接受

注册（两件物品）：`ModItems.init()` 用 `GameRegistry.registerItem` 注册 `wildcard_pattern` / `composite_wildcard_pattern`。

证据：`src/main/java/com/myname/wildcardpattern/ModItems.java:27` —— `GameRegistry.registerItem(wildcardPattern, "wildcard_pattern");`

配方：AE2 空白样板 → 通配样板（无序）；空白样板 + 未配置通配样板 → 复合通配样板。

证据：`src/main/java/com/myname/wildcardpattern/CommonProxy.java:24` —— `GameRegistry.addShapelessRecipe(new ItemStack(ModItems.wildcardPattern), blankPattern);`
证据：`src/main/java/com/myname/wildcardpattern/CommonProxy.java:25` —— `GameRegistry.addRecipe(new CompositeWildcardRecipe());`

继承 AE2 的 `ItemEncodedPattern`：

证据：`src/main/java/com/myname/wildcardpattern/item/ItemWildcardPattern.java:17` —— `public class ItemWildcardPattern extends ItemEncodedPattern {`
证据：`src/main/java/com/myname/wildcardpattern/item/ItemCompositeWildcardPattern.java:17` —— `public class ItemCompositeWildcardPattern extends ItemEncodedPattern {`

而 AE2 的 `ItemEncodedPattern` 实现了 `ICraftingPatternItem`：

证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/items/misc/ItemEncodedPattern.java:56` —— `public class ItemEncodedPattern extends AEBaseItem implements ICraftingPatternItem {`

"所有样板总成/接口都接受"的直接原因：宿主只做**类型判定**，不做物品白名单。AE2 接口侧：

证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/DualityInterface.java:528` —— `if (is.getItem() instanceof ICraftingPatternItem cpi) {`
证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/DualityInterface.java:529` —— `final ICraftingPatternDetails details = cpi.getPatternForItem(is, this.iHost.getTileEntity().getWorldObj());`

GT ME 输入仓侧同样是"强制转型 + 调 getPatternForItem"：

证据：`../GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java:156` —— `this.patternDetails = ((ICraftingPatternItem) Objects.requireNonNull(pattern.getItem())).getPatternForItem(`

因此：**继承本身即可被接受**；通配语义再通过 mixin 覆盖 `getPatternForItem` 注入（见 Q5）。物品自带标记位 `WildcardPattern`：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:33` —— `|| stack.hasTagCompound() && stack.getTagCompound().getBoolean(KEY_WILDCARD));`

---

## Q2 `WildcardPatternDetails` 怎么做到"一张样板匹配多类配方"

### 2.1 它自己不含任何匹配逻辑——纯 delegate

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:13` —— `private final PatternHelper delegate;`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:16` —— `this.delegate = new PatternHelper(stack, world);`

13 个接口方法全部一行转发，例如：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:26` —— `return this.delegate.isValidItemForSlot(slotIndex, itemStack, world);`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:36` —— `return this.delegate.getInputs();`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:66` —— `return this.delegate.getOutput(craftingInv, world);`

唯一被"改写"的只有**身份语义**（`hashCode`/`equals` 走生成 ID），因为同一个通配样板会派生 N 个 details，必须彼此可区分：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:84` —— `result = 31 * result + WildcardPatternGenerator.getPatternIdentity(pattern).hashCode();`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternDetails.java:109` —— `String leftId = WildcardPatternGenerator.getGeneratedPatternId(left);`

### 2.2 "一张覆盖多类"的真正位置：展开 + 逐候选改写 NBT

规则 → 候选材料池 → 每个候选一个 details：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:140` —— `for (int ruleIndex = 0; ruleIndex < MAX_RULES; ruleIndex++) {`

`MAX_RULES` 就是"一张样板最多几条规则"：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:25` —— `private static final int MAX_RULES = 9;`

候选池构造（输入候选 ∩ 输出候选；都没候选时退化到全材料表）：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:231` —— `Set<String> inputCandidates = input == null || input.isEmpty() ? java.util.Collections.<String>emptySet() : input.getCandidateMaterials();`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:259` —— `return WildcardPatternEntry.getAllKnownMaterialNames();`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:180` —— `for (Materials material : Materials.getAll()) {`

**关键一步**：把模板 ItemStack 复制一份，塞进具体 `in`/`out` 列表、写唯一 ID、显式清 `crafting` 位：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:343` —— `ItemStack result = template.copy();`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:345` —— `resultTag.setTag("in", inputList);`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:350` —— `buildGeneratedPatternId(ruleIndex, materialName, inputStack, outputStack));`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:351` —— `resultTag.setBoolean("crafting", false);`

数量写进 `Count`/`Cnt` 两个键（后者是 AE2 读 0 数量时的回退）：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:395` —— `rewrittenTag.setInteger("Count", count);`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:396` —— `rewrittenTag.setLong("Cnt", count);`

每个候选取一个独立 details：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:159` —— `ICraftingPatternDetails detail = createDetailForCurrentStack(generated, world);`

于是 AE2 侧看到的是一堆**完全合法的普通加工样板**（AE2 原生构造器解析 `in`/`out`）：

证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/PatternHelper.java:64` —— `final NBTTagList inTag = nbt.getTagList("in", NBT.TAG_COMPOUND);`
证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/PatternHelper.java:66` —— `this.isCrafting = nbt.getBoolean("crafting");`
证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/PatternHelper.java:123` —— `for (int x = 0; x < outTag.tagCount(); x++) {`

**匹配逻辑究竟在哪一行**：不在 `WildcardPatternDetails`，而在候选生成 + 矿辞/名称解析本身——
矿辞候选枚举：证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:472` —— `for (String oreName : OreDictionary.getOreNames()) {`
名称/矿辞匹配：证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:592` —— `return compiled != null && compiled.matcher(displayName).find();`
矿辞匹配：证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:608` —— `return compiled != null && compiled.matcher(normalizedOre).matches();`

### 2.3 展开发生在"宿主建索引"的那一刻（不是合成时）

证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:44` —— `List<ICraftingPatternDetails> detailsList = WildcardPatternGenerator.generateAllDetails(stack, world);`
证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:52` —— `this.craftingList.add(details);`

优先级沿用 AE2 公式：

证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:49` —— `int priority = slot - 36 * this.getPriority();`

### 2.4 预览用另一套 details（不参与合成）

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPreviewPatternDetails.java:47` —— `return false;`（`isCraftable()` 恒 false）
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:78` —— `return new WildcardPreviewPatternDetails(stack, getRepresentativeInput(stack), getRepresentativeOutput(stack));`

---

## Q3 通配规则在 NBT 里怎么存（键名与结构）

### 3.1 每级键名

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:22` —— `private static final String KEY_WILDCARD = "WildcardPattern";`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:24` —— `public static final String KEY_GENERATED_PATTERN_ID = "WildcardGeneratedPatternId";`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:15` —— `private static final String KEY_INPUT_COMPONENTS = "WildcardInputComponents";`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:16` —— `private static final String KEY_OUTPUT_COMPONENTS = "WildcardOutputComponents";`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:17` —— `private static final String KEY_EXPANDED_PATTERN_COUNT = "WildcardExpandedPatternCount";`
证据：`src/main/java/com/myname/wildcardpattern/item/CompositeWildcardPatternState.java:15` —— `private static final String KEY_WILDCARD_INPUT = "CompositeWildcardInput";`
证据：`src/main/java/com/myname/wildcardpattern/item/CompositeWildcardPatternState.java:17` —— `private static final String KEY_FIXED_INPUTS = "CompositeWildcardFixedInputs";`

### 3.2 结构：列表（每条规则一个 CompoundTag）

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:155` —— `NBTTagList list = new NBTTagList();`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:140` —— `result.add(WildcardPatternEntry.fromNbt(list.getCompoundTagAt(index)));`

条目内部字段（模式 / 匹配串 / 数量 / 栈 / 显示栈）：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:25` —— `private static final String KEY_MODE = "Mode";`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:28` —— `private static final String KEY_MATCHER = "Matcher";`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:29` —— `private static final String KEY_AMOUNT = "Amount";`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:87` —— `tag.setBoolean(KEY_MODE, this.oreDictMode);`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:89` —— `tag.setLong(KEY_AMOUNT, getAmountLong());`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:67` —— `entry.matcher = tag.hasKey(KEY_MATCHER) ? tag.getString(KEY_MATCHER) : tag.getString("Prefix");`

数量被硬钳位：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:30` —— `public static final long MAX_AMOUNT = 2_100_000_000L;`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:404` —— `this.amount = Math.max(1L, Math.min(MAX_AMOUNT, amount));`

### 3.3 "未配置样板"其实是 AE2 原生编码样板的空壳

首次初始化把原生 `in`/`out` 导入成通配组件列表，然后在没有生成 ID 时把 `in`/`out` 删掉：

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:30` —— `tag.setTag(KEY_INPUT_COMPONENTS, importPatternList(tag.getTagList("in", NBT.TAG_COMPOUND)));`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:131` —— `tag.removeTag("in");`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:132` —— `tag.removeTag("out");`

### 3.4 复合样板只有 **1 条**通配规则 + 最多 8 个固定输入

证据：`src/main/java/com/myname/wildcardpattern/crafting/CompositeWildcardPatternGenerator.java:23` —— `private static final int RULE_INDEX = 0;`
证据：`src/main/java/com/myname/wildcardpattern/item/CompositeWildcardPatternState.java:21` —— `public static final int MAX_FIXED_INPUTS = 8;`

### 3.5 导出/应用（跨端同步的就是这份 NBT）

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:102` —— `copyIfPresent(source, exported, "WildcardGlobalExcludeMaterials");`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:119` —— `copyIfPresent(config, tag, "WildcardGlobalExcludeMaterials");`

---

## Q4 排除（黑名单）：两级，判定在 `acceptsCandidate`

### 4.1 两级键名

全局排除是**单个字符串**：

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:24` —— `private static final String KEY_GLOBAL_EXCLUDE_MATERIALS = "WildcardGlobalExcludeMaterials";`

每条规则排除/允许是**字符串列表**（按规则下标取值）：

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:26` —— `private static final String KEY_RULE_EXCLUDE_MATERIALS = "WildcardRuleExcludeMaterials";`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:25` —— `private static final String KEY_RULE_INCLUDE_MATERIALS = "WildcardRuleIncludeMaterials";`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:188` —— `return ruleIndex >= 0 && ruleIndex < list.tagCount() ? normalizeList(list.getStringTagAt(ruleIndex)) : "";`

写入：

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:79` —— `tag.setString(KEY_GLOBAL_EXCLUDE_MATERIALS, normalizeList(globalExclude));`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:81` —— `tag.setTag(KEY_RULE_EXCLUDE_MATERIALS, writeStringList(ruleExcludes));`

### 4.2 判定行（先全局，后规则允许，再规则排除）

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:137` —— `if (matchesList(getGlobalExcludeMaterials(stack), candidateTerms)) {`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:141` —— `if (!includeValue.isEmpty() && !matchesList(includeValue, candidateTerms)) {`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:144` —— `return !matchesList(getRuleExcludeMaterials(stack, ruleIndex), candidateTerms);`

调用点（生成候选时逐个过滤）：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:219` —— `if (!WildcardPatternConfig.acceptsCandidate(stack, ruleIndex, candidate, inputStack, outputStack)) {`

### 4.3 匹配的"候选词"集合（决定排除串能写什么）

候选词 = 材料名 + 输入显示名 + 输出显示名 + 矿辞名（含 GT++ 回退）：

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:256` —— `String materialName = normalizeMaterialName(candidateName);`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:289` —— `String oreName = normalizeMaterialName(getPrefixName(prefix) + material.mName);`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:301` —— `result.add(normalizeMaterialName(oreName));`

排除串支持通配：`*` / `?` 转正则并以 `matches()` 全匹配：

证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:352` —— `if (token.indexOf('*') >= 0 || token.indexOf('?') >= 0) {`
证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternConfig.java:407` —— `return this.wildcardPattern.matcher(value).matches();`

### 4.4 UI 侧如何落到这两级

总排除 / 规则排除同屏并可切换编辑目标：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1576` —— `return this.excludeRule >= 0 ? this.ruleExcludes.get(this.excludeRule) : this.globalExclude;`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1582` —— `this.ruleExcludes.set(this.excludeRule, next);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1584` —— `this.globalExclude = next;`

预览页逐行"排除"按钮 → 写入**规则级**排除：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:442` —— `exclude.setOnClick((clickData, widget) -> state.excludePreviewRow(lineIndex));`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:2024` —— `appendRuleExclude(row.rule, token);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:2091` —— `this.ruleExcludes.set(rule, String.join(" ", tokens));`

---

## Q5 四个 mixin 的注入点与各自解决的问题

配置文件（required=true，带 refmap）：

证据：`src/main/resources/mixins.wildcardpattern.json:2` —— `"required": true,`
证据：`src/main/resources/mixins.wildcardpattern.json:5` —— `"refmap": "mixins.wildcardpattern.refmap.json",`
证据：`src/main/resources/mixins.wildcardpattern.json:8` —— `"DualityInterfaceMixin",`（同处列出其余三个，第 9–11 行）

### 5.1 `ItemEncodedPatternMixin` → `appeng.items.misc.ItemEncodedPattern`

证据：`src/main/java/com/myname/wildcardpattern/mixin/ItemEncodedPatternMixin.java:21` —— `@Mixin(value = ItemEncodedPattern.class, remap = false)`

四个 `@Inject` 全在 `HEAD` 且 `cancellable = true`：

| 目标方法 | 行号证据 | 目的 |
|---|---|---|
| `getPatternForItem` | `:24` `@Inject(method = "getPatternForItem", at = @At("HEAD"), cancellable = true)` | 把原生 `PatternHelper` 换成通配展开/details |
| `getOutput` | `:35` `@Inject(method = "getOutput", at = @At("HEAD"), cancellable = true)` | 让循环合成/GUI 拿到代表性产物 |
| `getOutputAE` | `:43` `@Inject(method = "getOutputAE", at = @At("HEAD"), cancellable = true)` | AE 栈形态的产物同样被替换 |
| `addCheckedInformation` | `:54` `@Inject(method = "addCheckedInformation", at = @At("HEAD"), cancellable = true)` | 屏蔽原版 Shift 预览（原版会尝试解析空的 in/out）并打印用法 |

生效条件（只对通配样板）：

证据：`src/main/java/com/myname/wildcardpattern/mixin/ItemEncodedPatternMixin.java:29` —— `if (!WildcardPatternGenerator.isWildcardPattern(item)) {`

`getOutputAE` 的返回值构造：

证据：`src/main/java/com/myname/wildcardpattern/mixin/ItemEncodedPatternMixin.java:51` —— `cir.setReturnValue(output == null ? null : AEItemStack.create(output.copy()));`

### 5.2 `DualityInterfaceMixin` → `appeng.helpers.DualityInterface`

证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:21` —— `@Mixin(value = DualityInterface.class, remap = false)`
证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:36` —— `@Inject(method = "addToCraftingList", at = @At("HEAD"), cancellable = true, remap = false)`

解决：AE2 样板总成/接口只会给一张样板建**一个** `craftingList` 条目；这里换成"一批条目"并 `ci.cancel()` 掉原逻辑。

证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:54` —— `ci.cancel();`

被 shadow 的三个成员必须与 AE2 一致（否则静默失效）：

证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:25` —— `private AppEngInternalInventory patterns;`
证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:31` —— `public List<ICraftingPatternDetails> craftingList;`
证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:34` —— `protected abstract int getPriority();`

对照 AE2 真实签名：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/DualityInterface.java:521` —— `protected void addToCraftingList(final int slot) {`

### 5.3 `MTEHatchCraftingInputMEMixin` → GT `MTEHatchCraftingInputME`（最大、最脏的一个）

证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:37` —— `@Mixin(value = MTEHatchCraftingInputME.class, remap = false)`

四个注入点：

| 目标 | 行号证据 | 目的 |
|---|---|---|
| `provideCrafting` HEAD+cancel | `:60` `@Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true)` | 绕开 GT 每槽"一个 details"的注册，改为按槽压入全部展开 details，并重建 `patternDetailsPatternSlotMap` |
| `pushPattern` HEAD+cancel | `:98` `@Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)` | 合成 CPU 推配方时，用"生成 ID"把请求反查回通配槽并绑定/校验 |
| `onPatternChange` RETURN | `:165` `@Inject(method = "onPatternChange", at = @At("RETURN"))` | 样板槽变化后重新登记展开映射 |
| `loadNBTData` RETURN | `:170` `@Inject(method = "loadNBTData", at = @At("RETURN"))` | 存档后按保存的"活动样板"恢复映射 |

GT 真实方法签名对照：
`../GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java:1224` —— `public void provideCrafting(ICraftingProviderHelper craftingTracker) {`
`../GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java:1242` —— `public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {`
`../GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java:809` —— `public void loadNBTData(NBTTagCompound aNBT) {`
`../GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java:1075` —— `public void onPatternChange(int index, ItemStack newItem) {`

它为什么需要"子类化槽位"：GT 的 `PatternSlot` 有 `final patternDetails` + `final patternItemId`，一个槽只能绑一个 details，无法承载展开集：

证据：`../GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java:143` —— `protected final ICraftingPatternDetails patternDetails;`

于是自定义 `WildcardPatternSlot`（继承 `PatternSlot`，覆写 `getPatternDetails()`，并按"活动样板"持久化）：

证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:442` —— `private static final class WildcardPatternSlot extends MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> {`
证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:541` —— `public ICraftingPatternDetails getPatternDetails() {`
证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:564` —— `public NBTTagCompound writeToNBT(NBTTagCompound nbt) {`
证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:444` —— `private static final String KEY_ACTIVE_PATTERN = "WildcardActivePattern";`

槽位包装（把 GT 原槽替换成子类，并搬运已存内容）：

证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:388` —— `WildcardPatternSlot wrapped = new WildcardPatternSlot((MTEHatchCraftingInputME) (Object) this, stack, slot);`
证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:461` —— `this.itemInventory.add(itemStack.copy());`

它还额外做了刷新合成缓存（**反射 + 静默兜底**）：

证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:296` —— `Field field = findField(((Object) this).getClass(), "processingLogics");`
证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:307` —— `// GTNH 2.9 removed this field; cache clearing is best-effort for older GT versions.`

以及流体包支持判定（`supportFluids` 为 false 时拒绝流体包）：

证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:198` —— `if (itemStack != null && itemStack.getItem() instanceof ItemFluidPacket) {`

### 5.4 `PatternMultiplierHelperMixin` → `appeng.util.PatternMultiplierHelper`

证据：`src/main/java/com/myname/wildcardpattern/mixin/PatternMultiplierHelperMixin.java:17` —— `@Mixin(value = PatternMultiplierHelper.class, remap = false)`
证据：`src/main/java/com/myname/wildcardpattern/mixin/PatternMultiplierHelperMixin.java:20` —— `@Inject(method = "applyModification", at = @At("HEAD"), cancellable = true)`
证据：`src/main/java/com/myname/wildcardpattern/mixin/PatternMultiplierHelperMixin.java:33` —— `@Inject(method = "getMaxBitMultiplier", at = @At("HEAD"), cancellable = true)`
证据：`src/main/java/com/myname/wildcardpattern/mixin/PatternMultiplierHelperMixin.java:47` —— `@Inject(method = "getMaxBitDivider", at = @At("HEAD"), cancellable = true)`

解决：通配样板**没有**真实 in/out，AE2 原生的"×2 位乘"会把 0 长度输入算成 0 或崩溃；这里直接改成改通配条目数量。

证据：`src/main/java/com/myname/wildcardpattern/mixin/PatternMultiplierHelperMixin.java:28` —— `WildcardPatternState.applyBitModification(stack, bitMultiplier);`

原生实现对照：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/util/PatternMultiplierHelper.java:13` —— `public static int getMaxBitMultiplier(ICraftingPatternDetails details) {`
调用方（AE2 三个 GUI/容器）：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/container/implementations/ContainerInterface.java:229` —— `final int max = multiplier < 0 ? PatternMultiplierHelper.getMaxBitDivider(details)`

---

## Q6 有没有"NEI 拖入"或"从配方自动生成规则"

**NEI 拖入：有（仅物品→配置项，不是"从 NEI 配方生成"）。**

拖拽接口来自 ModularUI2 的 `IDragAndDropHandler`，实现类三个：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardEntryDropTextField.java:10` —— `public class WildcardEntryDropTextField extends TextFieldWidget implements IDragAndDropHandler {`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardEntryDropButton.java:10` —— `public class WildcardEntryDropButton extends ButtonWidget implements IDragAndDropHandler {`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardFilterDropTextField.java:17` —— `public class WildcardFilterDropTextField extends TextFieldWidget implements IDragAndDropHandler {`

规则格拖入后的自动转换（**这正是"少手写通配符"的地方**）：有 GT 矿辞关联就连前缀一起转矿辞模式，否则按名称模式：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:246` —— `if (next.canOreDict()) {`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:247` —— `next.convertToOreDict();`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:315` —— `this.matcher = getPrefixName(prefix) + "*";`

过滤器文本框拖入会生成矿辞 token 并去重追加：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardFilterDropTextField.java:35` —— `token = buildOreToken(association);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardFilterDropTextField.java:42` —— `String next = appendToken(getText(), token);`

**从配方自动生成规则：没有。** 全源码只出现一个 `IRecipe`，且它只是**合成通配样板本身**的合成表，不读任何机器配方：

证据：`src/main/java/com/myname/wildcardpattern/crafting/CompositeWildcardRecipe.java:13` —— `public class CompositeWildcardRecipe implements IRecipe {`
证据：`src/main/java/com/myname/wildcardpattern/crafting/CompositeWildcardRecipe.java:30` —— `if (isBlankPattern(stack) && !foundBlankPattern) {`

关键词扫描结果：`CraftingManager` 0 处、`findMatchingRecipe` 0 处、`RecipeSorter` 0 处、`NEI` 0 处（此前 grep 命中的 "NEI" 全部来自 `lineIndex` 的子串误报）。**未发现任何"扫描配方/NEI 配方导出"的实现。**

唯一的"半自动"是搜索兼容（NEI 风格搜索语法），只是过滤预览行：

证据：`src/main/java/com/myname/wildcardpattern/compat/NechSearchCompat.java:46` —— `if (!Loader.isModLoaded("nech")) {`

---

## Q7 GUI 结构与可做操作

统一入口：右键打开，服务端/客户端各建一个 ModularUI 窗口：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardGuiHandler.java:19` —— `WildcardPatternWindow.createWindow(buildContext, player, x);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardGuiHandler.java:28` —— `CompositeWildcardPatternWindow.createWindow(buildContext, player, x);`
（注：`gui/ContainerWildcardPattern.java` 存在但**未被引用**，是遗留空容器，只有 `canInteractWith`。）

窗口尺寸与页容量常量：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:41` —— `private static final int GUI_WIDTH = 452;`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:43` —— `private static final int RULE_ROWS = 9;`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:44` —— `private static final int PREVIEW_LINES = 12;`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:46` —— `private static final int EXCLUDE_LINES = 9;`

四个页面：主页面 / 预览页 / 排除页 / 去重页：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:74` —— `addMainPage(builder, state);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:75` —— `addPreviewPage(builder, state);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:76` —— `addExcludePage(builder, state);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:77` —— `addDedupePage(builder, state);`

### 主页面控件

- 9 行规则，每行：输入文本框 + 模式按钮 + 数量框 → 输出文本框 + 模式按钮 + 数量框，加 4 个动作按钮：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:187` —— `ButtonWidget preview = button("gui.wildcardpattern.preview_short");`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:193` —— `ButtonWidget filter = button(`（"筛"：选定当前规则，供排除页/允许框联动）
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:206` —— `ButtonWidget multiply = button("gui.wildcardpattern.multiply_short");`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:220` —— `ButtonWidget clear = button("gui.wildcardpattern.clear_short");`

- 模式按钮在名称/矿辞间切换：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:289` —— `tr(entries.get(index).isOreDict() ? "gui.wildcardpattern.mode_oredict" : "gui.wildcardpattern.mode_name"),`

- 数量框：解析并对超界钳位：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:303` —— `entries.get(index).setAmount(parseAmount(value));`

- 全局排除框 + 规则排除编辑入口 + 清空 / 去重 / 全部预览 / 保存：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:117` —— `addGlobalExclude(builder, state, 8, 248);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:118` —— `addRuleExcludeEditor(builder, state, 164, 248);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:139` —— `dedupe.setOnClick((clickData, widget) -> state.openDedupe());`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:145` —— `previewAll.setOnClick((clickData, widget) -> state.openPreview(-1));`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:152` —— `state.save();`

### 预览页

- 由规则放大镜打开；含搜索框、**仅当单规则预览时才出现的"允许"（include）框**、排除摘要、每行"排除"按钮、翻页：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:396` —— `if (state.previewRule >= 0) {`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:401` —— `() -> state.ruleIncludes.get(state.previewRule),`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:439` —— `ButtonWidget exclude = button("gui.wildcardpattern.exclude_short");`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:475` —— `if (state.previewPageIndex + 1 < state.getPreviewPageCount()) {`

- 预览在**后台线程**构建并打断上一个线程：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1740` —— `Thread thread = new Thread(() -> {`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1746` —— `WildcardPatternGenerator.generateRulePreviewPatterns(entry.getValue(), rule);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1792` —— `thread.setDaemon(true);`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1716` —— `prev.interrupt();`

### 排除页

- 草稿输入框 + `+` 添加 + 逐行 `X` 删除 + 翻页 + 清空/返回：

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:501` —— `TextFieldWidget field = new WildcardFilterDropTextField(value -> state.excludeDraft = value == null ? "" : value)`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:515` —— `ButtonWidget add = button("+");`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:538` —— `ButtonWidget delete = button("X");`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1638` —— `setCurrentExcludeValue(String.join(" ", tokens));`

### 去重页（同一矿辞多个物品时挑保留项）

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:139` —— `dedupe.setOnClick((clickData, widget) -> state.openDedupe());`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1797` —— `private void rebuildDedupe() {`
证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:1960` —— `private void cycleDedupeChoice(String oreName) {`
默认优先 GT 物品：证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:2009` —— `if (id != null && "gregtech".equalsIgnoreCase(id.modId)) {`

### 复合窗口

- 1 行通配规则 + 8 个固定输入格子（拖入 + 数量 + `X` 删除）：

证据：`src/main/java/com/myname/wildcardpattern/gui/CompositeWildcardPatternWindow.java:134` —— `for (int index = 0; index < FIXED_INPUTS; index++) {`
证据：`src/main/java/com/myname/wildcardpattern/gui/CompositeWildcardPatternWindow.java:293` —— `WildcardEntryDropButton drop = new WildcardEntryDropButton(stack -> {`
证据：`src/main/java/com/myname/wildcardpattern/gui/CompositeWildcardPatternWindow.java:342` —— `ButtonWidget remove = button("X");`

### 保存路径（客户端改完发服务端整包 NBT）

证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:2104` —— `WildcardNetwork.CHANNEL.sendToServer(new MessageUpdateWildcardConfig(this.slot, WildcardPatternState.exportConfig(preview)));`
证据：`src/main/java/com/myname/wildcardpattern/gui/CompositeWildcardPatternWindow.java:1840` —— `new MessageUpdateCompositeWildcardConfig(this.slot, CompositeWildcardPatternState.exportConfig(preview)));`
服务端按槽位重新校验物品再应用：
证据：`src/main/java/com/myname/wildcardpattern/network/MessageUpdateWildcardConfig.java:49` —— `if (stack == null || stack.getItem() != ModItems.wildcardPattern) {`
证据：`src/main/java/com/myname/wildcardpattern/network/MessageUpdateWildcardConfig.java:54` —— `WildcardPatternState.applyConfig(stack, message.config);`

---

## Q8 明显局限（逐条带源码依据）

1. **一张样板最多 9 条规则**（每条规则 = 一组输入/输出条件）。
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:25` —— `private static final int MAX_RULES = 9;`
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:191` —— `if (ruleIndex >= inputs.size() || ruleIndex >= outputs.size()) {`

2. **一条规则只有 1 个输入 + 1 个输出槽**（复合样板才有 8 个固定输入，但那是**固定具体物品**，不参与展开）。
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:337` —— `NBTTagList inputList = buildPatternList(inputStack);`
   证据：`src/main/java/com/myname/wildcardpattern/crafting/CompositeWildcardPatternGenerator.java:23` —— `private static final int RULE_INDEX = 0;`
   证据：`src/main/java/com/myname/wildcardpattern/crafting/CompositeWildcardPatternGenerator.java:164` —— `for (ItemStack fixed : fixedInputs) {`

3. **候选池无上界**：两个条目都拿不到矿辞候选时，直接吃 GT 全材料表（材料 × 前缀），展开数量可能爆炸。
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:259` —— `return WildcardPatternEntry.getAllKnownMaterialNames();`
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:185` —— `for (String oreName : OreDictionary.getOreNames()) {`

4. **必须手工给出匹配串或拖物品**：没有"从配方反推规则"的入口（见 Q6）；手写时还要懂两种模式差别（名称 vs 矿辞）。
   证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:271` —— `entry.setOreNameOrPrefix(value);`

5. **不能表达"不消耗物品/铸模/编程电路"**：数量被强制 `Math.max(1, …)`，AE2 表示非消耗输入的 `stackSize = 0` + `Cnt` 语义被主动填成 ≥1；也没有任何 `circuit/mold/lens/nonConsum` 分支。
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:394` —— `int count = Math.max(1, stack.stackSize);`
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:404` —— `this.amount = Math.max(1L, Math.min(MAX_AMOUNT, amount));`

6. **只做加工样板（processing），不做工作台样板**：生成时把 `crafting` 写成 false。
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:351` —— `resultTag.setBoolean("crafting", false);`
   AE2 侧对应：工作台样板走 `isCrafting` 分支，加工样板不查配方表——
   证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/helpers/PatternHelper.java:93` —— `if (this.isCrafting) // processing recipes are not looked up`

7. **流体只能"借道"物品形态；未装流体支持的 GT 仓直接拒绝流体包**。
   证据：`src/main/java/com/myname/wildcardpattern/mixin/MTEHatchCraftingInputMEMixin.java:198` —— `if (itemStack != null && itemStack.getItem() instanceof ItemFluidPacket) {`

8. **展开是"每个候选一份完整样板对象"**，代价随候选数线性上升；且展开在服务端建索引时同步发生（线程风险见风险节）。
   证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:159` —— `ICraftingPatternDetails detail = createDetailForCurrentStack(generated, world);`
   证据：`src/main/java/com/myname/wildcardpattern/mixin/DualityInterfaceMixin.java:44` —— `List<ICraftingPatternDetails> detailsList = WildcardPatternGenerator.generateAllDetails(stack, world);`

9. **整个配置（含每条的完整 ItemStack NBT）走一个网络包同步**，规则多/物品 NBT 大时有超包风险。
   证据：`src/main/java/com/myname/wildcardpattern/item/WildcardPatternState.java:100` —— `exported.setTag(KEY_INPUT_COMPONENTS, source.getTagList(KEY_INPUT_COMPONENTS, NBT.TAG_COMPOUND).copy());`
   证据：`src/main/java/com/myname/wildcardpattern/network/MessageUpdateWildcardConfig.java:36` —— `ByteBufUtils.writeTag(buffer, this.config);`

10. **每个候选都克隆整份样板栈**（含全部既有 NBT），内存/GC 压力放大。
    证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternGenerator.java:343` —— `ItemStack result = template.copy();`

11. **复合样板被写死为单条规则**，无法像普通通配样板那样 9 条。
    证据：`src/main/java/com/myname/wildcardpattern/crafting/CompositeWildcardPatternGenerator.java:23` —— `private static final int RULE_INDEX = 0;`

12. **依赖反射读取 GT 枚举/字段**，失败即静默回退空数组（会表现为"什么都没法展开"而不是报错）。
    证据：`src/main/java/com/myname/wildcardpattern/compat/GTCompat.java:34` —— `return new OrePrefixes[0];`
    证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:697` —— `} catch (Exception ignored) {}`

13. **配置生效依赖"槽位号 + 物品类型"**：主手换槽/丢出即无法保存（服务端会静默 return）。
    证据：`src/main/java/com/myname/wildcardpattern/network/MessageUpdateWildcardConfig.java:45` —— `return null;`
    证据：`src/main/java/com/myname/wildcardpattern/gui/WildcardPatternWindow.java:2137` —— `return this.player.inventory.getStackInSlot(this.slot);`

---

## Q9 circuit / mold / lens / nonConsum 相关处理

**结论：全部没有。**

扫描（源码根下全部 `.java`，大小写各扫一遍）：

- `circuit` / `Circuit`：各 1 处命中，且都是**误命中**——`WildcardPatternEntry.java:231` 注释里的 "Short-circuit"。
  证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:231` —— `// Short-circuit for items with no recognised ore prefix (e.g. vanilla items).`
- `mold` / `Mold`：0
- `lens` / `Lens`：0
- `nonConsum` / `notConsum` / `NonConsum` / `NotConsum`：0
- `recycle` / `disassemble`：0
- `isInputOnly` / `getInputOnlyUuid`（AE2 的非消耗/内联语义）：在通配模组内 0 处实现——`ICraftingPatternDetails` 有这两个 default 方法，通配 details 也没有覆写：
  证据：`../Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/src/main/java/appeng/api/networking/crafting/ICraftingPatternDetails.java:134` —— `default boolean isInputOnly() {`

编程电路只能靠"当作普通物品拖入 + 名称模式精确匹配显示名"这种副作用方式命中（走 `fromStack` → 显示名 matcher），没有任何专用逻辑：

证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:52` —— `entry.matcher = displayName == null ? "" : displayName;`
证据：`src/main/java/com/myname/wildcardpattern/crafting/WildcardPatternEntry.java:589` —— `return displayName.equalsIgnoreCase(pattern);`

---

## 值得本仓借鉴的实现要点

1. **"伪装成原生物品 + 在宿主建索引处截胡"是最省兼容成本的扩展姿势。**
   继承 `ItemEncodedPattern` 就自动通过所有 `instanceof ICraftingPatternItem` 判定（`ItemWildcardPattern.java:17` ↔ `DualityInterface.java:528`），不需要改 AE2，也不需要注册新的样板类型。
2. **给展开出来的每个变体一个稳定、可持久化的唯一 ID（`WildcardGeneratedPatternId`），并让 `equals/hashCode` 只认它。**
   这是让"同一物品的多个 details"在 AE2/GT 的 Map/List 里互不串味的核心（`WildcardPatternGenerator.java:356`、`WildcardPatternDetails.java:84`、`MTEHatchCraftingInputMEMixin.java:499`）。
3. **候选集用"输入候选 ∩ 输出候选"作交集，而不是各自展开再配对**，能有效压缩笛卡尔积（`WildcardPatternGenerator.java:282` `current.retainAll(narrowed);`）。
4. **给 O(材料×前缀) 的路径做缓存 + 精确名直查快路径**，并显式注释"避免与主线程抢 OreDictionary 锁"（`WildcardPatternEntry.java:130-136`、`ORE_CANDIDATE_CACHE`）。
5. **预览放后台线程 + 生成号（generation）防串页 + 打断上一次**（`WildcardPatternWindow.java:1710-1794`）——重活不进渲染帧。
6. **两级过滤（全局 + 每条规则）分开存、判定顺序固定为"全局排除 → 规则允许 → 规则排除"**（`WildcardPatternConfig.java:137-144`），语义清晰、UI 也好挂。
7. **拖入物品自动判定矿辞/名称模式并生成 `prefix*` token**（`WildcardPatternWindow.java:246`、`WildcardPatternEntry.java:315`），把"写通配符"的门槛降到拖一下。
8. **`getPatternInputs()` 覆写让 GT 双输入仓真正吃到展开后的输入**（`MTEHatchCraftingInputMEMixin.java:807`）——不覆写的话机器侧只会看到空输入。
9. **活动样板要持久化**（`WildcardActivePattern` / `WildcardActivePatternId` + `loadNBTData` RETURN 恢复），否则重启后"仓里存的物品"与"当前绑定的配方"对不上（`MTEHatchCraftingInputMEMixin.java:564`、`:170`）。
10. **对版本差异用"先反射枚举 values()，失败再读 VALUES 字段"的双通道 + 注释说明原因**（`GTCompat.java:44-46`），比硬编码一个版本更耐迁移。

---

## 不建议照抄 / 风险点

1. **"一个候选一份样板对象"的展开方式会线性放大对象与 GC 压力**，无上界（`WildcardPatternGenerator.java:140-165`、`:343`）。在本仓（MK.III 144 槽）若叠加，索引构建时间与内存都会成倍增长。
2. **展开发生在服务端线程的主路径上**（`DualityInterface.addToCraftingList` / `gt` `provideCrafting` 都是网络钩子触发），候选多时是明确的卡服点；`ALL_KNOWN_MATERIAL_NAMES` 一次性遍历 `Materials.getAll()` + `OreDictionary.getOreNames()`（`WildcardPatternEntry.java:173-193`）。
3. **异步预览线程里读 `OreDictionary`/`GameRegistry` 并写共享静态集合**（`WildcardPatternEntry.java:31-37` 用的是 `ConcurrentHashMap`，但 `computeIfAbsent` 内部仍在别的线程枚举全局矿辞表）。作者自己在 `:129-131` 注释承认"避免与主线程锁竞争"——说明这条路径确实踩过。
4. **`mixins.wildcardpattern.json` 里 `"required": true`**（`:2`）。目标类任何一个方法/字段因 GTNH 版本变动而消失，就是**启动期硬崩**，而不是降级。本仓若做类似扩展，建议 `required: false` + 显式自检日志。
5. **`@Shadow` 的字段/方法名必须与目标完全一致，否则静默不生效**：`DualityInterfaceMixin.java:25/31/34`、`MTEHatchCraftingInputMEMixin.java:41/44/48/52`。这与本仓已知的"SRG 名 / 静默失效"坑位是同一类。
6. **用子类替换 GT 的 `PatternSlot`**（`MTEHatchCraftingInputMEMixin.java:388`）：父类是 `static class` 且字段 `final`，子类必须靠构造期手工搬运 `itemInventory/fluidInventory`（`:459-468`）。GT 一改构造签名或校验，这里会以难查的方式失效。
7. **多处 `catch (...) {}` 静默兜底**：`GTCompat.java:44/57`、`WildcardPatternEntry.java:697/699/847`、`MTEHatchCraftingInputMEMixin.java:306/340`。与本仓"不允许静默回退，必须能靠日志定性"的项目原则**直接冲突**——照抄会把"勾了没用"变成新一轮排障黑洞。
8. **热路径上会写 NBT**：`initializeFromPattern` → `ensureInitialized` 在任何 `getEntries` 调用里都可能给物品塞 tag（`WildcardPatternState.java:28-36`、`:136`），`ItemEncodedPattern.getOutput/getOutputAE` 又被上层缓存（AE2 `OUTPUT_STACK_CACHE` 是 `WeakHashMap`，见 `ItemEncodedPattern.java:60`）。物品 NBT 频繁改动 + 弱引用缓存叠在一起，容易出"改了不生效"的观感问题。
9. **整份配置走单包同步**（`MessageUpdateWildcardConfig.java:36`）：条目多时超包/丢包，且**没有版本号或校验**，客户端与服务端配置可能静默不一致。
10. **配置绑定"玩家背包槽位号"**（`WildcardPatternWindow.java:2137`、`MessageUpdateWildcardConfig.java:44-48`）：换槽/换维度/并发操作时体验脆弱，服务端只有静默 `return null`。
11. **预览 `isCraftable()` 恒 false 的 details 与真实 details 是两套类**（`WildcardPreviewPatternDetails.java:47` vs `WildcardPatternDetails.java:11`），二者行为不完全等价（预览不参与槽位校验），会让人误判"预览能出、实际出不来"。
12. **`WildcardPatternEntry.java:221` / `WildcardPatternConfig.java:221、347`、`WildcardFilterDropTextField.java:129` 里的全角分隔符字符在 UTF-8 源码中显示为乱码**（`[,;锛涳紱\s]+` 形态）。语义上仍能分隔（当前是按字符类匹配），但这属于编码链路上的隐患，照抄会带进同样的乱码。
