# AE2-QoL 流体识别问题调研与解决方案

> **fix5（2026-09-10）修复状态**：
> - **二合一终端原生流体（彻底修复）**：`PatternContainer.convertToAEStack` 流体分支改返回 `IAEFluidStack`（原返回 `ItemFluidDrop.newAeStack` 物品）；同时绕过 `FluidPatternDetails.writeToStack()`（它内部把流体经 `stackConvert` 转成 ItemFluidDrop 物品存入 legacy 数组），自己写 NBT 用 `getCondensedAEInputs()`（原生流体）。样板产物不再显示"液态氧液滴"物品。
> - **覆盖板流体识别（扩展）**：`tryRecognizeFluidItem` 新增 `IFluidContainerItem` 接口识别 + 通用 NBT 兜底，覆盖 GT 流体单元/ae2fc 存储单元等。
>
> 主题：二合一终端（MergedTerminal）与库存覆盖板（StockMonitorCover）均无法识别流体，会把流体识别成一个物品。
> 背景判断：1.7.10 原生 AE2 没有原生流体；GTNH 2.9.0 更新之后 AE（AE2UEL）支持原生流体。
> 调研方式：项目源码 + AE2UEL / AE2FC 反编译（javap）+ GitHub 网页 / API（commit、release、issue）。
> 结论：**两个背景判断均成立；两个组件"把流体识别成物品"均有明确代码路径；2.9.0 原生流体下有对应适配方案。**

---

## 一、问题概述

AE2-QoL 的两个功能组件在与 AE 网络交互时，对"流体"的处理不符合预期：

1. **二合一终端（MergedTerminal，`merged/` 包）**：样板编码时把流体当成物品处理。
2. **库存覆盖板（StockMonitorCover，`cover/stockmonitor/` 包）**：对部分"流体表示物"无法识别为流体，按物品处理。

现象统一表现为：**流体被识别成一个物品**。

产生这一现象的根本背景：
- MC 1.7.10 的原版 AE2（rv3）没有原生流体存储实现，流体在 AE 网络中长期以"物品模拟"方式存在（AE2FC 的 `ItemFluidPacket` / `ItemFluidDrop`）。
- GTNH 2.9.0 起，GTNH 维护的 AE2 分支（AE2UEL，Applied-Energistics-2-Unofficial）将存储体系泛型化，流体成为 `StorageChannel.FLUIDS` 一等通道（原生流体），AE2FC 同步适配。
- 两个组件的现有代码仍停留在"物品模拟"时代，或只识别部分流体表示物，因此出现"把流体当物品"的行为。

---

## 二、调研结论速览

| # | 结论项 | 结论 | 关键证据 |
|---|---|---|---|
| 1 | 1.7.10 原生 AE2 是否有原生流体 | **没有** | `CellInventory` 仅实现物品存储，`getCellType()` 恒为 `TYPE.ITEM`；AE2FC README 明确 "AE2 doesn't support fluid as valid crafting ingredients before 1.16" |
| 2 | GTNH 2.9.0 AE 是否支持原生流体 | **支持** | AE2UEL commit `bd11e117` "Universal GUI (#872)"（2025-12-11）引入泛型 `CellInventory<StackType>` + `FluidCellInventory` + `FluidCellInventoryHandler`；合成 CPU 泛型化为 `IAEStack<?>` |
| 3 | 二合一终端为何把流体当物品 | 样板编码用 `ItemFluidDrop.newAeStack(fs)`，**返回 `IAEItemStack`** | `PatternContainer.convertToAEStack()`（458–471 行）+ javap 反编译签名 |
| 4 | 库存覆盖板为何把流体当物品 | phantom 槽只认 `FluidContainerRegistry.getFluidForFilledItem()` 命中的桶/罐/GT 单元；ae2fc `FluidDrop`、GT `ItemFluidDisplay` 落物品分支 | `StockMonitorCoverData.updateTargetFromPhantom()`（75–88 行） |
| 5 | 2.9.0 原生流体下的适配方向 | 终端样板改用原生流体栈（`AEFluidStack` 进 `IAEStack<?>`）；覆盖板补"流体物品"识别层 | AE2FC 1.5.80+ 的 `FluidPatternDetails` / `PartFluidPatternTerminal`；`NetworkInventoryCache` 已有可复用识别逻辑 |

---

## 三、背景：1.7.10 AE 流体支持的演进

### 3.1 原版 AE2 rv3（1.7.10）：无原生流体

- API 层一直存在 `IStorageGrid.getFluidInventory()`、`IAEFluidStack`、`StorageChannel` 等定义。
- 但存储实现只覆盖物品：`appeng.me.storage.CellInventory` 为物品专用，单元类型写死 `TYPE.ITEM`，**没有流体单元存储实现**。
- 因此流体无法作为一等公民直接存入 AE 网络，也无法作为合成原料直接参与自动合成。
- 官方（AE2FC README）表述：*"AE2 doesn't support fluid as valid crafting ingredients before 1.16, so it can't handle fluids directly."*

### 3.2 AE2FC 物品模拟（GTNH 2.9.0 之前的主流方案）

- AE2FC（AE2FluidCraft-Rework）通过"把流体伪装成物品"解决：流体被包装成 `ItemFluidPacket`（NBT 携带流体）或 `ItemFluidDrop`（流体滴物品）进入 **ITEM 通道**。
- 自动合成时用物品模拟参与，需要离散器（Discretizer）/流体包解码器在"物品态"与"流体态"之间转换。
- **这就是"流体被识别成一个物品"的历史根源**：在那个时代，网络里流体的物理载体确实是一个物品。

### 3.3 AE2UEL 原生流体（2.9.0 开发周期引入）

GTNH 维护的 AE2 分支 `Applied-Energistics-2-Unofficial`（AE2UEL）在 2.9.0 开发周期引入原生流体架构：

- **引入 commit**：`bd11e117` "Universal GUI (#872)"（2025-12-11，作者 lc-1337），后续 `03c28f0f` "Cell Restriction rework (#1265)"（2026-05-06）继续完善。
- 核心改动（来自 #872 的 diff）：
  - `CellInventory` 泛型化：`public abstract class CellInventory<StackType extends IAEStack<StackType>>`，存储槽、数量、类型计数全部泛型化；
  - 新增 `FluidCellInventory extends CellInventory<IAEFluidStack>` 与 `FluidCellInventoryHandler`；
  - `CreativeCellInventory.getCell(ItemStack, StorageChannel)` 按通道分发：`ITEMS → ItemCellInventoryHandler`、`FLUIDS → FluidCellInventoryHandler`；
  - 合成 CPU（CraftingCPUCluster）：`getProviders` / `getScheduledReason` / `getCondensedAEOutputs` 从 `IAEItemStack` 泛型化为 **`IAEStack<?>`**——合成系统原生支持流体；
  - 配套 `FluidList`（`IItemList<IAEFluidStack>`，含 `findPrecise`）、`AEFluidStackType`、`AEFluidTankHandler`。
- 反编译确认（rv3-beta-977-GTNH dev jar）：
  - `FluidCellInventory extends CellInventory<IAEFluidStack>`；
  - `FluidList implements IItemList<IAEFluidStack>`，`findPrecise` 可用；
  - `CellInventory.getCell(ItemStack, ISaveProvider, IAEStackType<?>)` 按 `IAEStackType` 分发物品/流体 handler。

### 3.4 AE2FC 适配与 GTNH 2.9.0 正式整合

- AE2FC 1.5.80-gtnh："Fluid Planes (#424)"；1.5.81-gtnh："Cell Restriction rework (#428)" + "Allow decode drops into fluid"。
- 反编译 AE2FC 1.5.88-gtnh：流体单元基类 `FCBaseItemCell.getStackType()` 返回 **`AEFluidStackType.FLUID_STACK_TYPE`**——流体单元挂在 **FLUID 通道**（`getFluidInventory()`）。
- GTNH 2.9.0 正式版：AE2UEL 升至 **rv3-beta-997-GTNH**、AE2FC 升至 1.5.8x-gtnh；"流体电平发射器（Fluid Level Emitter）并入电平发射器（Level Emitter）"，Level Emitter 原生支持流体。
- **本项目编译依赖**（dependencies.gradle）：`Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev` + `AE2FluidCraft-Rework:1.5.88-gtnh:dev`——**已包含全部原生流体类**，可直接基于原生流体 API 开发。

> 时间线小结：原版 rv3（无原生流体）→ AE2FC 物品模拟（ItemFluidPacket/FluidDrop 进 ITEM 通道）→ AE2UEL #872（2025-12，FLUIDS 一等通道）→ GTNH 2.9.0（2026-06，正式整合发布）。

---

## 四、根因分析（本地源码实证）

### 4.1 二合一终端 MergedTerminal

**代码位置**：`src/main/java/com/wztwzt/ae2_qof/merged/PatternContainer.java`

**关键路径**：

```java
// PatternContainer.convertToAEStack()（458–471 行）
private static IAEStack<?> convertToAEStack(ItemStack stack) {
    if (stack == null) return null;
    if (isFluidItem(stack)) {
        FluidStack fs = getFluidFromItem(stack);
        if (fs != null) {
            if (isGTFluidDisplayItem(stack)) {
                fs.amount = fs.amount * stack.stackSize;
            }
            IAEItemStack drop = ItemFluidDrop.newAeStack(fs);   // ← 关键：流体被包装成"流体滴物品"
            if (drop != null) return drop;
        }
    }
    return appeng.util.item.AEItemStack.create(stack);
}
```

**证据**（javap 反编译 AE2FC 1.5.88）：

```java
public static appeng.api.storage.data.IAEItemStack newAeStack(net.minecraftforge.fluids.FluidStack);
public static appeng.api.storage.data.IAEItemStack newAeStack(appeng.api.storage.data.IAEFluidStack);
```

→ `ItemFluidDrop.newAeStack()` **返回 `IAEItemStack`**，即把流体转成了物品栈。

**同文件混用点**：`createItemTag()`（481–501 行）对流体又用 `AEFluidStack.toNBTGeneric()` 写入（原生流体风格的 NBT），而 `convertToAEStack()` 却走物品包装——**新旧两套机制并存**，说明代码正处于 2.9.0 过渡状态，需要统一。

**结论**：终端在样板编码层把流体包装成 `ItemFluidDrop` 物品，流体以"物品"身份参与样板与合成，这是"把流体识别成一个物品"的直接来源。

### 4.2 库存覆盖板 StockMonitorCover

**代码位置**：`src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/`

**关键路径**（`StockMonitorCoverData.updateTargetFromPhantom()`，75–88 行）：

```java
FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(phantomStack);
if (fluid != null) {
    monitorTarget = AEApi.instance().storage().createFluidStack(fluid);  // → IAEFluidStack，查 FLUID 通道
} else {
    IAEItemStack itemStack = AEApi.instance().storage().createItemStack(phantomStack);  // ← 流体物品落这里
    itemStack.setStackSize(1);
    monitorTarget = itemStack;  // → IAEItemStack，查 ITEM 通道
}
```

**问题**：
- `FluidContainerRegistry.getFluidForFilledItem()` 只能识别**流体容器**（桶、罐、GT 单元等）。
- 对 ae2fc `ItemFluidDrop`、GT `ItemFluidDisplay` 这类**"流体物品"**（本身不是容器），该方法返回 null → 落入物品分支 → 按 `IAEItemStack` 查 ITEM 通道 → **把流体当物品**。

**已正确 / 无需改动的部分**：
- `AeStockReader.readStock()`（`ae/AeStockReader.java`）已按 `IAEItemStack` / `IAEFluidStack` 分通道 `findPrecise`，且 2.9.0 下 `getFluidInventory()` 有真实数据（FluidCellInventory），**流体分支可直接工作**。
- 客户端 `NetworkInventoryCache` 已实现三类"流体物品"识别（见 4.3），可复用。

**GUI 层缺口**：`cover/stockmonitor/gui/` 下没有任何流体相关提示/单位换算（mB/L）代码，用户放入流体目标时缺乏引导。

### 4.3 客户端流体识别现状（NetworkInventoryCache，可复用）

`client/NetworkInventoryCache.java` 已经实现"从物品识别流体"的完整逻辑（供 NEI tooltip 使用）：

1. **ae2fc 纯流体 packet**：按物品类名 `com.glodblock.github.common.item.ItemFluidPacket` 识别，从 NBT `"FluidStack"` 复合标签读流体；
2. **GT Display_Fluid**：按物品类名 `gregtech.common.items.ItemFluidDisplay` 识别，damage 值即流体注册 ID；
3. **流体方块物品**：通过 `registerFluidItem()` 建立的映射反查。

⚠️ 注意（代码注释中的既有教训）：流体判定**必须按物品类名限定**，绝不能对所有物品按 `itemDamage` 查 `FluidRegistry`（damage 是物品元数据，与流体注册 ID 无对应关系，误判会导致随机物品显示流体量）。

---

## 五、解决方案

### 5.1 二合一终端：样板编码改用原生流体栈

**目标**：流体以 `IAEFluidStack` 身份直接参与样板与合成，不再包装成 `ItemFluidDrop` 物品。

> **落实情况（2026-09-09 核对）**：**部分落实**。git 提交 `9f95206 fix21` / `5ccbd83 fix19` 已增强 `isFluidItem()`（补 `ItemFluidDrop` / `ItemFluidPacket` / `IFluidContainerItem` 检测，`PatternContainer.java:1054-1070`）并新增 `getFluidFromItem()`（1134 行），但 **`convertToAEStack()` 仍用 `ItemFluidDrop.newAeStack(fs)` 把流体包装成物品**（458-466 行）——即"识别"已增强，"编码为原生流体栈"未改。**终端场景下流体仍以物品身份进样板**。若用户实测"流体仍识别为物品"发生在终端，需按本节继续改：
> 1. `convertToAEStack()` 流体分支改为 `return AEApi.instance().storage().createFluidStack(fs);`（IAEFluidStack）；
> 2. 确认 `FluidPatternDetails` / pattern provider 解析链能消费流体栈输入/输出。

**依据**：AE2FC 1.5.80+ 的原生流体样板机制——

- `com.glodblock.github.util.FluidPatternDetails`：`aeInputs/aeOutputs` 为 `IAEStack<?>[]`（物品栈与流体栈统一），实现 `ICraftingPatternDetails`；
- `com.glodblock.github.common.parts.PartFluidPatternTerminal extends PartPatternTerminal`：流体可直接编码进样板。

**改动点（PatternContainer.java）**：
1. `convertToAEStack()`：流体分支不再调 `ItemFluidDrop.newAeStack(fs)`，改为
   ```java
   return AEApi.instance().storage().createFluidStack(fs);   // IAEFluidStack
   ```
   （或 `appeng.util.item.AEFluidStack.create(fs)`）
2. `createItemTag()` 已用 `AEFluidStack.toNBTGeneric()` 写流体 NBT，与新机制一致，保留即可；需确认读回路径（`Platform.readStackNBT`）与写入对称。
3. 检查流体样板的合成执行路径：确认 `FluidPatternDetails`（或对应 pattern provider 逻辑）能解析上述流体栈输入/输出，替换掉旧的"流体滴物品"样板解析。

### 5.2 库存覆盖板：phantom 槽补"流体物品"识别

**目标**：桶/罐/GT 单元之外，ae2fc `FluidDrop`、GT `ItemFluidDisplay` 等"流体物品"也能转成 `IAEFluidStack` 进行流体检测。

> **落实情况（2026-09-09 核对）**：**已实现**。`StockMonitorCoverData.java:76-145` 已按本方案实现 `tryRecognizeFluidItem` 三类识别（ItemFluidPacket 读 `"FluidStack"` 键 / ItemFluidDrop 反射 `getFluidStack` / GT ItemFluidDisplay 按 damage），phantom 同步链路（`PhantomItemSlot.slot()` 自动建 `PhantomItemSlotSH`）已确认完整。**但用户实测仍"识别为物品"**——见开发指南 §1.3A（B9）：优先排查 NBT 读回不重建、其次确认测试物品是否在三类内。

**改动点（StockMonitorCoverData.updateTargetFromPhantom() 或新增工具类）**：
1. 在 `FluidContainerRegistry.getFluidForFilledItem()` 之前/之后补充识别：
   - ae2fc `ItemFluidDrop`：类名识别 + `ItemFluidDrop.getFluidStack(ItemStack)`（AE2FC 已提供静态方法，javap 确认签名：`public static FluidStack getFluidStack(ItemStack)`）；
   - GT `ItemFluidDisplay`：类名识别 + damage 查 `FluidRegistry`（复用 `NetworkInventoryCache.isGtFluidDisplay()` 逻辑）。
2. 命中后同样 `AEApi.instance().storage().createFluidStack(fs)` → `IAEFluidStack`。
3. `AeStockReader` 无需改动（流体分支 2.9.0 下直接有效）。
4. 建议 GUI 增加提示：目标槽说明"支持桶/单元/罐，以及 ae2fc 流体滴、GT 流体显示物"；当前库存按 mB/L 显示。

### 5.3 兼容性与迁移注意

| 场景 | 行为 |
|---|---|
| 2.9.0 之前（旧存档/旧依赖） | 网络流体以 ItemFluidPacket/FluidDrop 存在于 ITEM 通道；`getFluidInventory()` 无真实数据。覆盖板流体检测依赖 AE2FC 流体单元是否挂 FLUID 通道（2.9.0 前版本为自定义实现），**适配需按运行环境分别验证** |
| 2.9.0 之后 | 流体单元挂 FLUID 通道（`getStackType() = FLUID_STACK_TYPE`），`getFluidInventory()` 有真实数据，分通道查询即可 |
| 2.8 → 2.9 世界升级 | GTNH 官方已出现 "Fluid Level Emitters 未迁移" 类问题（issue #25399/#25401），覆盖板 NBT 中 `MonitorTarget` 用 `Platform.writeStackNBT` 序列化（含物品/流体类型标记），升级后需实测目标是否能被 `readStackNBT` 正确还原为流体栈 |

---

## 六、验证方式

1. **反编译验证**（本次已做）：
   ```
   javap -p -classpath <ae2fc-dev.jar> com.glodblock.github.common.item.ItemFluidDrop
   javap -p -classpath <ae2fc-dev.jar> com.glodblock.github.common.item.FCBaseItemCell
   javap -p -classpath <ae2uel-dev.jar> appeng.me.storage.FluidCellInventory
   javap -p -classpath <ae2uel-dev.jar> appeng.me.storage.CellInventory
   javap -p -classpath <ae2uel-dev.jar> appeng.util.item.FluidList
   ```
2. **运行验证建议**：
   - 终端：用流体桶/GT 流体显示物编码一条流体样板 → 下发合成 → 确认 CPU 以流体栈取料，而不是出现"流体滴物品"；
   - 覆盖板：phantom 槽分别放入桶（命中容器分支）、ae2fc 流体滴（新识别分支）、GT 流体显示物（新识别分支），观察当前库存读数与机器启停；
   - 升级场景：2.8.3 存档升级 2.9.0 后，覆盖板 `MonitorTarget` 流体目标是否仍有效。

---

## 七、参考资料

- AE2FC（GTNewHorizons fork）README：https://github.com/GTNewHorizons/AE2FluidCraft-Rework/
- AE2FC Releases（1.5.80 Fluid Planes / 1.5.81 Cell Restriction rework）：https://github.com/GTNewHorizons/AE2FluidCraft-Rework/releases
- AE2UEL commit "Universal GUI (#872)"（原生流体引入）：https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/commit/bd11e117413a6f062ed23749eaf3b69bc9e4a762
- AE2UEL commit "Cell Restriction rework (#1265)"：https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/commit/03c28f0fb8080ed0281e7d54d33941cd1d4d638d
- GTNH 2.9 概览（AE2 overhaul / AE2FC Fluid Planes / universal GUI）：https://hypeserv.com/en/blog/gtnh-2.9-what%27s-new-and-how-to-play-it-on-a-server
- GTNH issue #25401（2.9 流体电平发射器合并问题）：https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/25401
- GT-Steam-Reborn（2.9.0-beta-2 依赖：AE2 rv3-beta-997-GTNH）：https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Overview_CN

---

## 八、源码级复核记录（2026-09-09）

> 本轮对前文结论做了**全套源码级复核**（此前主要依据 javap 反编译签名 + GitHub 检索，存在细节层不确定）。复核材料：`reference_src/` 下 AE2UEL rv3-beta-977 完整源码、AE2FC Rework 完整源码，以及本项目自身代码。

### 8.1 复核结论

| 前文结论 | 复核结果 | 源码证据 |
|---|---|---|
| 2.9.0 起 AE2UEL 原生流体（FLUIDS 一等通道） | ✅ 成立 | `appeng/api/storage/StorageChannel.java:32` `FLUIDS(IAEFluidStack.class)`；`appeng/me/storage/FluidCellInventory.java`、`FluidCellInventoryHandler.java` 存在；`CellInventory` 系全部泛型化（`VoidCellInventory/NetworkInventoryHandler/StorageBusInventoryHandler` 等 `<T extends IAEStack<T>>`） |
| AE2FC 流体单元挂 FLUID 通道 | ✅ 成立 | `AE2FC/common/item/FCBaseItemCell.java:30-32` `getStackType()` 返回 `FLUID_STACK_TYPE` |
| 终端样板把流体包成物品（根因） | ✅ 成立 | 本项目 `merged/PatternContainer.java:458-466` `ItemFluidDrop.newAeStack(fs)` 返回 `IAEItemStack`；同文件 1151 行注释亦记录 ae2fc 流体 drop 的 NBT 结构 |
| 覆盖板 phantom 只认容器（根因） | ✅ 成立 | 本项目 `cover/stockmonitor/StockMonitorCoverData.java:75-88` 仅 `FluidContainerRegistry.getFluidForFilledItem()`，ae2fc/GT 流体物品落物品分支 |
| `ItemFluidDrop.getFluidStack(ItemStack)` 静态方法存在 | ✅ 成立 | `AE2FC/common/item/ItemFluidDrop.java:124` `public static FluidStack getFluidStack(ItemStack)` |
| 客户端 `NetworkInventoryCache` 三类识别可复用 | ✅ 成立且**无需改** | 本项目 `client/NetworkInventoryCache.java`：类名限定 `ItemFluidPacket`（读 `"FluidStack"` 复合键）+ `ItemFluidDisplay`（damage）+ 流体方块反查；注释与实现一致 |

### 8.2 细节修正（原文档未明确处，现已明确）

**ae2fc 两个流体物品类的 NBT 结构不同，勿混用**：

| 类 | NBT 结构 | 数量载体 | 用途 |
|---|---|---|---|
| `ItemFluidPacket` | `"FluidStack"`（复合，`FluidStack.writeToNBT`）+ 可选 `"Amount"`(long)、`"DisplayOnly"`(bool) | `FluidStack.amount` / `"Amount"` | AE 网络内流体物品表示（AE2FC `ItemFluidPacket.java:39-47,111-154`） |
| `ItemFluidDrop` | `"Fluid"`（TAG_STRING，流体名小写）+ `"FluidTag"`（复合） | `stackSize`（即 mB） | 流体滴（掉落物/手持），`ItemFluidDrop.java:124-141` |

- 覆盖板 phantom 槽识别应**三类都覆盖**：`ItemFluidPacket`（读 `"FluidStack"`，可直接复用 `NetworkInventoryCache.readPacketFluid`）→ `ItemFluidDrop`（读 `"Fluid"`+stackSize）→ GT `ItemFluidDisplay`（damage）。开发指南 §1.3 已同步更新。
- 原 5.2 节仅列了 `ItemFluidDrop` 与 GT 显示物，现补 `ItemFluidPacket` 分支。

### 8.3 与本次 GT 深度调研的对照说明

Nexus 反射 bug（`WirelessAeConnector:132` 旧包名）亦经 977 源码复核：`appeng/me/helpers/IGridProxyable.java`（新包名，`AENetworkProxy getProxy()`）存在，`appeng/api/networking/` 下已无 `IGridProxyable`（0 命中）→ `Class.forName` 必抛异常，结论成立。详见 `docs/AE库存检测覆盖板-开发指南.md` §0.3 B1。

---

*本文档 v2 由源码反编译 + GitHub 检索 + reference_src 全套源码复核整理；本地代码行号以当前仓库为准。*
