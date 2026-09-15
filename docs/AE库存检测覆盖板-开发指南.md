# AE 库存检测覆盖板 · 开发指南（v0.7）

> **v0.7（2026-09-10）修订说明**：
> - **用户实测 fix4 后暴露 4 个问题，均已在 fix5 修复**：
>   - **B10 无法断开**：断开按钮用 `onMousePressed`（只客户端）→ 改 `InteractionSyncHandler` 双端同步（`StockMonitorCoverGui`）。
>   - **B11 网络选择 UI 非 Nexus 原生**：新建 `StockMonitorWirelessEndpoint` 实现 `WirelessBindableEndpoint`，GUI 改用 `WirelessSelectionPanel.build`（Nexus 原生面板，含优先级/频道数/断开按钮）。
>   - **B12 流体识别范围不足**：`tryRecognizeFluidItem` 新增 `IFluidContainerItem` 接口识别（GT 流体单元/ae2fc 存储单元等）+ 通用 NBT "Fluid"/"FluidStack" 兜底。
>   - **B13 只能关机不能自动开机**：根因是流体被识别为物品 → count 读物品通道永远高于阈值 → shouldWork 永远 false；随 B12 修复联动解决。
> - 关联：终端原生流体（`PatternContainer`）、维护仓线程（`MixinProcessingLogicSpeed`）同步在 fix5 修复，见对应文档。
>
> **v0.6（2026-09-09）修订说明**：
> - **代码落实核对**：v0.5 的 B1–B6 已全部落实（B1 反射修复、B2 流体识别、B3 Shift 清空、B4 残留删除、B5 同步链路确认、B6 提示）；详见 §0.3 状态列。
> - **新增实测 bug（用户实测）**：B8（网络面板能弹出但点选无反馈、连不上）、B9（流体仍识别为物品——NBT 读回不重建 / 识别范围外物品，见 §1.3 验证结论）。
> - 关联文档：`docs/AE2-QoL流体识别问题调研与解决方案.md`（流体背景与终端适配）、`docs/万能维护仓-并行速度与线程跨配方并行-方案.md`。
>
> 本文档仍为覆盖板功能的单一事实来源；后续实现与修改应回溯到本文档。

---

## 0. 现状快照（先读本节）

### 0.1 已完成（代码已落地，构建 3.19.0-fix5）

| 模块 | 状态 | 关键代码位置 |
|---|---|---|
| 覆盖板注册（M1） | ✅ | `CommonProxy.preInit` 130–142 行：`CoverRegistry.registerCover` + `ItemStockMonitorCover` + 创造页 + `onlyPlaceIf(isCoverPlaceable)` |
| 覆盖板物品（M1） | ✅ | `ItemStockMonitorCover`：tooltip 三行 + `ae2qof` 标签；Shift+右键清配置 |
| 放置限制 / 拆卸恢复（M1） | ✅ | `isCoverPlaceable` 一机一块；`onCoverRemoval` 调 `enableWorking()` |
| GUI 主面板（M2） | ✅ | `StockMonitorCoverGui extends CoverBaseGui`：连接按钮、目标槽、阈值输入（`numbersLong(0,MAX)`）、预设按钮、模式切换、当前库存、机器状态、断开 |
| 网络选择面板（M2） | ✅ 面板可弹出（B1 已修复）；**点选无反馈/连不上见 B8** | `NetworkSelectPanel`：列网络卡片、点击绑定 |
| AE 连接双通道（M3） | ✅ | `AeConnector.getGrid`（无线优先→邻接回退）+ `WirelessAeConnector`（Nexus）+ `NeighborAeConnector` |
| 库存检测（M3） | ✅ | `AeStockReader.readStock`：`IAEItemStack`/`IAEFluidStack` 分通道 `findPrecise` |
| 机器控制（M4） | ✅ | `doCoverThings`：不可达/无目标强制停机；低于/高于阈值切换 `enableWorking/disableWorking`；`getMinimumTickRate()=10` 原生降频 |
| NBT / 网络同步 | ✅ | `saveDataToNbt`/`readDataFromNbt`；`writeDataToByteBuf`/`readDataFromPacket`（channel/阈值/模式） |
| lang 中英双语 | ✅ | `zh_CN.lang`/`en_US.lang` 270–299 行键齐全 |

### 0.2 未完成 / 待修（按优先级）

| 优先级 | 任务 | 类型 | 详见 |
|---|---|---|---|
| ~~**P0**~~ | ~~修复 Nexus 连接（B1）~~ — **✅ 已落实**（`WirelessAeConnector.java:132-140` 双包名反射） | 🔧 Bug | §0.3 B1 |
| ~~**P0**~~ | ~~网络选择：点卡片无反馈、面板不关闭；离线网络可点但连不上（B8）~~ — **✅ 已修复**（点选关闭+离线拦截+状态同步+日志） | 🔧 Bug | §1.1A |
| ~~**P1**~~ | ~~流体仍识别为物品（B9）：NBT 读回不重建~~ — **✅ 已修复**（readFromNbt 重建）；识别范围外物品待用户确认 | 🔧 Bug | §1.3A |
| ~~**P1**~~ | ~~Nexus 原生网络选择 UI（B 方案，待决策）~~ — 决策：暂不采用，保持自定义面板 | 📋 决策 | §1.2 |
| ~~**P1**~~ | ~~流体识别增强（B2）~~ — **✅ 已落实**（`StockMonitorCoverData.java:76-145` 三类识别），待实测 | 📋 方案已定 | §1.3 |
| **P2** | M5 边界实测：不可达回退、区块卸载、带电拆卸、目标消失、阈值 0、流体 mB 启停等 | ⏳ 待实测 | §7.3 |
| **P3** | M6 统计终端（可选增强）：发信器 + 覆盖板集中管理 | ❌ 未开始 | §12 |

### 0.3 Bug 清单（含状态与修复建议）

| # | 位置 | 问题 | 修复建议 | 优先级 | 状态 |
|---|---|---|---|---|---|
| **B1** | `WirelessAeConnector.java:132` | 旧包名 `appeng.api.networking.IGridProxyable` 在 977 已移除，`Class.forName` 抛异常 → `nexusAvailable=false` → 连接按钮无反应 | 已按 §1.1 修复：双包名 try-catch（`appeng.me.helpers.AENetworkProxy` 优先） | **P0** | ✅ **已修复**（`132-140` 行） |
| **B2** | `StockMonitorCoverData.java:80` | phantom 只认 `FluidContainerRegistry`，ae2fc `FluidDrop/Packet`、GT `ItemFluidDisplay` 落物品分支 | 已按 §1.3 修复：`tryRecognizeFluidItem` 三类识别（`76-145` 行） | **P1** | ✅ **已实现**（待实测，见 B9） |
| **B3** | `ItemStockMonitorCover.java:38-45` | Shift+右键清空漏删 `NBT_PHANTOM` | 已补 `removeTag(NBT_PHANTOM)`（`43` 行） | **P1** | ✅ **已修复** |
| **B4** | `StockMonitorCoverGui.java:56` | `StringSyncValue` setter 为空、未 `allowC2S`，无实际用途 | 已删除（GUI 现仅 `LongSyncValue thresholdSync`） | **P2** | ✅ **已修复** |
| **B5** | `StockMonitorCoverGui.java:94` | `PhantomItemSlot` 同步链路存疑 | **已确认无需改**：`PhantomItemSlot.slot()` 自动创建 `PhantomItemSlotSH`（MUI2 源码 `PhantomItemSlot.java:82-84`）；服务端 `readOnServer → phantomClick → putStack → InvWrapper.setStackInSlot → PhantomInventory.setInventorySlotContents → setPhantomStack` 链路完整 | **P2** | ✅ **已确认** |
| **B6** | `StockMonitorCoverGui.java:66-70` | Nexus 不可用时按钮静默失败 | 已加聊天提示 `nexus_not_installed`（`66-74` 行） | **P2** | ✅ **已修复** |
| **B7** | `StockMonitorCoverData.java:173-174` | `Platform.readStackNBT` 读回流体目标类型需验证 | 2.9.0 升级后实测（与 B9 同源问题） | **P3** | ⏳ 待实测 |
| **B8** | `NetworkSelectPanel.java:57-66` | **用户实测**：面板能弹出，但点卡片无任何反馈（面板不关闭、无选中高亮、无提示）；点**离线网络**也会写入 networkId，随后 `getGridForNetwork` 因 `isOnline=false` 返回 null → 连不上 | ① 点击成功卡片后 `panelHandler.closePanel()` 关闭面板；② 离线卡片点击给聊天提示不绑定；③ `StockMonitorCover` ByteBuf 补同步 `lastChannel/lastStock/lastShouldWork`（客户端状态行真实显示，否则永远显示 disconnected）；④ `getGridForNetwork` 分步 `LOG.debug`（当前静默返回 null） | **P0** | ✅ **已修复**（2026-09-09） |
| **B9** | `StockMonitorCoverData.readFromNbt` 230-231 | **用户实测**：流体仍识别为物品。代码链路（拖入→服务端→`updateTargetFromPhantom` 三类识别）已确认正确；根因① **NBT 读回优先**——`hasKey(NBT_MONITOR_TARGET)` 时直接读回旧值（旧版本存的是物品栈），**不按 phantomStack 重建**，升级后永远显示物品；② 用户放入的"流体物品"不在 `tryRecognizeFluidItem` 三类内（如 ae2fc 流体存储单元 `FCBaseItemCell`）——**待用户确认测试物品** | ① `readFromNbt` 读回后 phantomStack 存在时一律 `updateTargetFromPhantom()` 重建覆盖——**已修复**；② 扩展识别 `FCBaseItemCell`（成本较高，待用户确认测试物品种类后决定） | **P1** | ✅ **已修复①**（2026-09-09）；② 待确认 |

---

## 1. 优先任务（未完成前置）

### 1.1 ✅ 已完成：修复 Nexus 连接（Bug B1，2026-09-09 已落实）

**状态**：`WirelessAeConnector.java:132-140` 已改为双包名反射（`appeng.me.helpers.AENetworkProxy` 优先 + `appeng.api.networking.IGridProxyable` 回退）。实测面板可弹出（B1 链路已通）。

### 1.1A P0：网络选择 UX 与离线过滤（Bug B8，用户实测）

**现象**：B1 修复后"连接AE"能弹出网络面板；但点卡片**无任何反馈**（面板不关闭、无选中高亮），且**点离线网络也能写入 networkId**，随后 `getGridForNetwork` 因 `record.isOnline()==false` 返回 null → 连不上（`WirelessAeConnector.java:94`）。

**修复建议**（`NetworkSelectPanel.java`）：
1. 卡片点击成功后 `panelHandler.closePanel()`（`build` 方法已有 panelHandler 参数）；
2. 离线网络（`entry.online == false`）卡片不绑定 InteractionSyncHandler / 置灰不可点；
3. `setNetworkId` 后显式触发覆盖板数据同步（`issueCoverUpdate` 或 GT 覆盖板 update 机制），让连接状态即时刷新；
4. `WirelessAeConnector.getGridForNetwork` catch 分支补 `LOG.debug`（当前静默返回 null，运行时失败无法定位）。

**待确认**：用户点击的是在线网络还是离线网络；若在线仍连不上，需要服务器日志中 getGridForNetwork 的失败环节（基站在线状态、findController 是否返回 null）。

---

### 1.2 P1：Nexus 原生网络选择 UI（用户期望，待决策）

**背景**：Nexus 1.0.2 自带完整网络选择面板 `WirelessSelectionPanel`（网络名+已用/总频道+在线状态+断开+优先级），并有 GT 集成入口 `GTWirelessUI.addSelectorButton(IGregTechTileEntity, ModularPanel, PanelSyncManager, Flow)`。当前实现是自定义简化面板（`NetworkSelectPanel`，只列网络名+状态+点击绑定）。

**两个可选方案**：

| 方案 | 做法 | 优点 | 代价 |
|---|---|---|---|
| **A（推荐，改动小）** | 保留自定义 `NetworkSelectPanel`，仅修 B1 恢复其可用 | 改动最小；保持"轻量只读"决策 | UI 非 Nexus 原生 |
| **B（用户期望）** | 用 Nexus `WirelessSelectionPanel.build(String, WirelessBindableEndpoint, EntityPlayer, PanelSyncManager, boolean)` 替换自定义面板 | 原生 UI（频道信息、断开、优先级）；与 Nexus 生态一致 | 需实现 `WirelessBindableEndpoint`；改变 2026-09-08"轻量只读、不实现端点"决策 |

**方案 B 实现要点**：
1. 覆盖板（或其数据类）实现 `cn.dancingsnow.ae_wireless_nexus.network.WirelessBindableEndpoint`（接口方法见 §3.2）：
   - `getEndpointWorld()` → 覆盖板所在世界；
   - `getEndpointDisplayName()` → 覆盖板名称；
   - `getWirelessLeaseStatus()` → 返回绑定状态（只读场景可返回最小实现）；
   - `bindToNetwork(UUID, player)` / `unbindFromNetwork()` → 写 `networkId`；
   - `setWirelessPriority(int)` → 可空实现。
2. **不调用** `WirelessNetworkService.registerEndpoint`——只作为面板的绑定目标，不参与频道分配（保持不占频道）。
3. GUI 连接按钮改为：`syncManager.syncedPanel("network_select", true, (psm, ph) -> WirelessSelectionPanel.build("ae2qof_net_select", endpoint, player, psm, true))`。
4. 依赖：`libs/ae_wireless_nexus-1.0.2.jar` 已 `compileOnly`（`dependencies.gradle:55`），运行时需用户安装 Nexus 模组。

**决策点**：是否放弃"轻量只读"采用方案 B？默认建议先做 A（修 B1 立即可用），B 作为后续迭代。

---

### 1.3 ✅ 已实现：流体识别增强（Bug B2，2026-09-09 代码已落实）

**状态**：`StockMonitorCoverData.java:76-145` 已实现 `tryRecognizeFluidItem` 三类识别（ItemFluidPacket 读 `"FluidStack"` 键 / ItemFluidDrop 反射 `getFluidStack` / GT ItemFluidDisplay 按 damage），phantom 同步链路（`PhantomItemSlot.slot()` 自动建 `PhantomItemSlotSH`）已确认完整。**但用户实测"流体仍识别为物品"——见 §1.3A（B9）排查**。

**修复**（`StockMonitorCoverData.updateTargetFromPhantom()`，复用 `NetworkInventoryCache` 的类名识别逻辑）。**识别顺序与 NBT 键均已按 AE2FC 源码复核（2026-09-09）**：

```java
// 在 FluidContainerRegistry 之后补充：
if (fluid == null) {
    fluid = tryRecognizeFluidItem(phantomStack);   // 新增
}
// ...
private static FluidStack tryRecognizeFluidItem(ItemStack stack) {
    try {
        String cls = stack.getItem().getClass().getName();
        // ① ae2fc ItemFluidPacket（网络流体包）：NBT 键 "FluidStack"（复合，FluidStack.writeToNBT）
        //    + 可选 "Amount"(long)/"DisplayOnly"(bool)。复用 NetworkInventoryCache.readPacketFluid 逻辑。
        if (cls.equals("com.glodblock.github.common.item.ItemFluidPacket")) {
            return readPacketFluid(stack);   // getCompoundTag("FluidStack") → FluidStack.loadFluidStackFromNBT
        }
        // ② ae2fc ItemFluidDrop（流体滴，掉落物/手持）：NBT 键 "Fluid"(TAG_STRING 流体名)
        //    + "FluidTag"(复合)，数量在 stackSize。源码 ItemFluidDrop.getFluidStack() 124-141 行。
        if (cls.equals("com.glodblock.github.common.item.ItemFluidDrop")) {
            return ItemFluidDrop.getFluidStack(stack);
        }
        // ③ GT ItemFluidDisplay：damage 即流体注册 ID
        if (cls.equals("gregtech.common.items.ItemFluidDisplay")) {
            return new FluidStack(FluidRegistry.getFluid(stack.getItemDamage()), FluidContainerRegistry.BUCKET_VOLUME);
        }
    } catch (Throwable ignored) {}
    return null;
}
```

**NBT 键差异（源码确认，勿混淆）**：
| ae2fc 类 | NBT 结构 | 数量载体 |
|---|---|---|
| `ItemFluidPacket` | `"FluidStack"`（复合，FluidStack 序列化）+ 可选 `"Amount"`/`"DisplayOnly"` | `FluidStack.amount` / `"Amount"` |
| `ItemFluidDrop` | `"Fluid"`（字符串，流体名小写）+ `"FluidTag"`（复合） | `stackSize`（即 mB） |

**注意**（沿用 `NetworkInventoryCache` 注释的教训）：流体判定**必须按物品类名限定**，绝不能对所有物品按 `itemDamage` 查 `FluidRegistry`。

**验证**：phantom 槽分别放桶（容器分支）、ae2fc 流体滴、GT 流体显示物，确认当前库存按 mB 读取、机器按流体阈值启停。

### 1.3A P1：流体仍识别为物品（Bug B9，用户实测 2026-09-09）

**现象**：§1.3 代码已落实后，用户实测"流体依然识别为物品"。

**已排除**（源码核对结论）：
- phantom 同步链路完整：`PhantomItemSlot.slot()` 自动建 `PhantomItemSlotSH`（MUI2 `PhantomItemSlot.java:82-84`）→ 服务端 `readOnServer → phantomClick → putStack → InvWrapper.setStackInSlot → PhantomInventory.setInventorySlotContents → setPhantomStack`（`StockMonitorCover.java:231-238`）→ `updateTargetFromPhantom`。**拖入新物品一定能触发识别**。
- `updateTargetFromPhantom` 三类识别逻辑正确（`StockMonitorCoverData.java:76-145`）。

**剩余两个嫌疑**（按可能性排序）：
1. **NBT 读回优先（最可能）**：`readFromNbt` 230-231 行——`hasKey(NBT_MONITOR_TARGET)` 时直接 `Platform.readStackNBT` 读回旧值，**不按 phantomStack 重建**。旧版本（B2 修复前）把流体存成了物品栈 → 升级后读回仍是物品栈 → 显示"识别为物品"。
   - **修复**：`readFromNbt` 中读回 monitorTarget 后，若 `phantomStack` 存在，用 `updateTargetFromPhantom()` 的重建结果**覆盖**读回值（保证 phantom 与目标一致）；或读回前若 phantomStack 可识别为流体，直接走重建分支。
2. **测试物品不在识别范围内**：`tryRecognizeFluidItem` 只认 ItemFluidPacket / ItemFluidDrop / GT ItemFluidDisplay。若用户放的是 **ae2fc 流体存储单元（`FCBaseItemCell`，`getStackType()==FLUID_STACK_TYPE`）** 或其他流体物品 → 落物品分支。
   - **待用户确认**：测试用的具体物品是什么（流体桶 / ae2fc 流体滴 / GT 流体显示物 / ae2fc 流体存储单元 / 终端编码场景）。
3. **场景区分**：若"流体识别为物品"出现在**合并终端**（而非覆盖板），则是终端问题——`PatternContainer.convertToAEStack` 仍用 `ItemFluidDrop.newAeStack(fs)` 包装成物品（fix21 只增强了识别、未改原生流体栈，见流体文档 §5.1），需另按 §5.1 方案处理。

**验证**：修复 1 后，旧存档覆盖板读回流体目标显示流体名；放 ae2fc 流体滴 / GT 显示物确认 mB 读数；若仍失败，提供测试物品种类与场景。

---

### 1.4 P2：M5 边界实测清单（代码已有保护逻辑，未实测）

- [ ] 基站被拆除 → 覆盖板显示不可达并停机；邻接有 AE 时回退邻接通道继续工作
- [ ] 基站区块卸载 → 停机；重新加载 → 自动恢复
- [ ] 覆盖板带电拆卸 → 机器恢复允许（`onCoverRemoval` 已调 `enableWorking`）
- [ ] 监控目标从 AE 消失 → 库存按 0（低于 N 模式会开机，符合"不足就合成"）
- [ ] 阈值 0 → 机器常停；负数输入被 `numbersLong(0,MAX)` 拒绝
- [ ] 流体目标：桶/单元/流体滴放入、流体取空、mB 阈值启停（依赖 §1.3 修复）
- [ ] 多板同贴 → 第二个被 `isCoverPlaceable` 拒绝
- [ ] 2.8.3 → 2.9.0 升级：绑定网络与流体目标读回正常（B7 验证点）
- [ ] 性能：多板同时运行无明显 TPS 下降

### 1.5 P3：M6 统计终端（设计完整，未开工）

见 §12。开工前先完成 P0–P2。

---

## 2. 需求分析（FR 状态标注）

| FR | 需求 | 状态 |
|---|---|---|
| FR-1 | 覆盖板可贴任意 GT 机器面，撬棍拆卸 | ✅ 已实现 |
| FR-2 | 监控 AE 网络库存（物品/流体），低于/高于阈值控制机器启停 | ✅ 已实现（B2 已落实，B9 待排查） |
| FR-3 | GUI 可设目标、阈值、模式、快捷数量、绑定/断开网络 | ✅ 已实现（B1 已修复；B8 选网络 UX 待修） |
| FR-4 | 支持邻接直连（无线不可用时） | ✅ 已实现 |
| FR-5 | 拆卸保留配置，Shift+右键清空 | ✅ 已实现（B3 已修复） |
| FR-6 | 未安装 Nexus 时不崩溃、提示邻接 | ✅ 已实现（`isNexusAvailable` 守卫） |
| FR-7 | 中英双语文案 + `ae2qof` tooltip 标签 | ✅ 已实现 |
| FR-8 | 性能：10 tick 降频、不占频道 | ✅ 已实现 |
| FR-9 ~ FR-13 | 统计终端相关（M6） | ❌ 未开始 |

非功能需求（NFR）：安全优先（不可达即停机）、服务端权威、数据可追溯——已实现。

---

## 3. AE Wireless Nexus 参考（含 1.0.2 反编译实测）

### 3.1 三层架构
```
① 基站层：TileWirelessController（继承 AE2 TileController，天然是 ME 控制器）
② 服务层：WirelessNetworkService（静态：getVisibleNetworks / findController / 频道分配）
③ 端点与 UI 层：WirelessBindableEndpoint / GTWirelessEndpoint / GTWirelessUI / WirelessSelectionPanel
```

### 3.2 关键 API 实测（javap，ae_wireless_nexus-1.0.2.jar）
| 类 / 方法 | 签名（javap 实测） | 用途 |
|---|---|---|
| `WirelessNetworkService.getVisibleNetworks` | `static List<WirelessNetworkRecord> (World, EntityPlayer)` | 列可见网络（含 BUILD 权限过滤） |
| `WirelessNetworkSavedData.get` | `static WirelessNetworkSavedData (World)` | 拿世界存档 |
| `WirelessNetworkSavedData.get` | `WirelessNetworkRecord (UUID)` | 按 UUID 取记录 |
| `WirelessNetworkService.findController` | `static TileWirelessController (WirelessNetworkRecord)` | 找基站 TE |
| `WirelessNetworkRecord` | `getId()/getName()/isOnline()` | 记录字段 |
| `TileController.getProxy`（AE2） | `AENetworkProxy getProxy()`（继承链） | 拿网络代理 |
| `AENetworkProxy.getNode`（AE2） | `IGridNode getNode()` | 拿网格节点 |
| `IGridNode.getGrid`（AE2） | `IGrid getGrid()` | 拿 IGrid |
| `WirelessSelectionPanel.build` | `static ModularPanel (String, WirelessBindableEndpoint, EntityPlayer, PanelSyncManager, boolean)` | **Nexus 原生网络选择面板** |
| `GTWirelessUI.addSelectorButton` | `static Flow (IGregTechTileEntity, ModularPanel, PanelSyncManager, Flow)` | GT 机器 GUI 一键加选择按钮 |
| `WirelessBindableEndpoint` | `getEndpointWorld / getEndpointDisplayName / getWirelessLeaseStatus / bindToNetwork / unbindFromNetwork / setWirelessPriority` | 可绑定端点接口 |

### 3.3 采用 / 不采用（v0.5 更新）
| 部分 | 结论 | 说明 |
|---|---|---|
| `getVisibleNetworks` / `findController` / `SavedData` / `Record` | ✅ 采用 | 无线连接数据源（P0 修复后可用） |
| `WirelessSelectionPanel` | ⚠️ 原"不采用"，现列为 P1 可选方案（§1.2-B） | 用户期望 Nexus 原生 UI |
| `WirelessBindableEndpoint` | ⚠️ 原"不实现"，现列为 P1 可选方案（§1.2-B） | 仅作面板绑定目标，不 `registerEndpoint`、不占频道 |
| `GTWirelessEndpoint` / `registerEndpoint` / `GridConnection` | ❌ 仍不采用 | 正式端点会占频道、改变网络拓扑 |
| `GTWirelessUI.addSelectorButton` | ❌ 覆盖板不直接采用 | 其参数为 `IGregTechTileEntity`（机器 GUI 用）；覆盖板 GUI 参照其 syncedPanel 模式即可 |

---

## 4. 整体架构（已实现，简述）

```
玩家 GUI（MUI2 CoverBaseGui）
  ├─ 连接按钮 → NetworkSelectPanel（列表/绑定）→ networkId
  ├─ 目标槽（phantom，物品/流体容器）→ monitorTarget（IAEStack<?>）
  ├─ 阈值/模式/预设 → threshold/mode
  └─ 状态行：lastChannel / lastStock / lastShouldWork
        ↓ doCoverThings（每 10 tick，GT5 原生降频）
  AeConnector.getGrid（无线 Nexus → 邻接回退）
        ↓
  AeStockReader.readStock（getItemInventory / getFluidInventory 分通道 findPrecise）
        ↓
  判定 shouldWork → enableWorking()/disableWorking()
```

数据流、模块划分与 NBT 结构沿用 v0.4 设计（已实现，细节见代码注释，不再展开）。

---

## 5. 模块设计关键点（已实现 + 待改点）

| 模块 | 关键点 | 状态 |
|---|---|---|
| 注册 | `CoverRegistry.registerCover(item, texture, factory, placer)` + `onlyPlaceIf(isCoverPlaceable)` | ✅ |
| 行为 | `doCoverThings` 4 步：取 grid → 查库存 → 判定 → 控机；不可达/无目标强制停机 | ✅ |
| 连接 | 双通道调度（`AeConnector`）；Nexus 反射隔离（`WirelessAeConnector`） | 🔧 B1 |
| 检测 | `AeStockReader` 分通道 `findPrecise`；流体按 mB | ✅ |
| 数据 | `StockMonitorCoverData`：networkId/monitorTarget/threshold/mode/phantomStack + 运行态缓存 | 🔧 B2/B7 |
| 物品 | `ItemStockMonitorCover`：tooltip + Shift 清空 | 🔧 B3 |
| GUI | `StockMonitorCoverGui` + `NetworkSelectPanel` | 🔧 B4/B5/B6 |
| NBT/同步 | `saveDataToNbt`/`readDataFromNbt`；ByteBuf 同步 channel/threshold/mode | ✅ |
| lang | 中英双语键齐全 | ✅ |

---

## 6. 关键 API 调用链（v0.5 更新为 AE2UEL 977 新包名）

### 6.1 列出可用网络
```
WirelessNetworkService.getVisibleNetworks(world, player)   // 服务端调用
  → List<WirelessNetworkRecord>（仅在线 + 有 BUILD 权限）
```
（当前在 `NetworkSelectPanel.fetchNetworks` 中经 `WirelessAeConnector` 反射调用；P0 修复后可用。）

### 6.2 绑定网络
```
覆盖板 GUI 点网络卡片 → InteractionSyncHandler（服务端执行）
  → coverData.setNetworkId(uuid)   // NBT 持久化
```

### 6.3 获取 IGrid（双通道）
```
AeConnector.getGrid(networkId, world, x, y, z, coverSide)
  ├─ 通道A 无线：SavedData.get(world).get(uuid).isOnline()
  │     → Service.findController(record) → controller.getProxy()   // AENetworkProxy
  │     → proxy.getNode().getGrid()                                 // IGridNode → IGrid
  └─ 通道B 邻接：NeighborAeConnector.findGrid（自身+6邻 IGridProxyable/IGridHost）
```

### 6.4 查询库存
```
IAEItemStack  → storage.getItemInventory().getStorageList().findPrecise(target).getStackSize()
IAEFluidStack → storage.getFluidInventory().getStorageList().findPrecise(target).getStackSize()  // mB
```

### 6.5 控制机器
```
shouldWork = mode.shouldWork(stock, threshold)
shouldWork && !allowed → enableWorking()；!shouldWork && allowed → disableWorking()
```

---

## 7. 开发里程碑（v0.5 重构：M1–M4 已归档，M5/M6 活动）

### 7.1 已归档里程碑（✅ 完成）
- **M1 注册骨架**：✅ 注册/防重/拆卸恢复/Shift 清空（B3 除外）
- **M2 GUI 与网络选择**：✅ 主面板全部元素；⚠️ 网络选择依赖 B1 修复
- **M3 AE 连接与库存检测**：✅ 双通道 + 分通道读库存
- **M4 机器控制**：✅ enable/disableWorking + 判定 + 10tick 降频

### 7.2 M5：边界处理与测试（活动，见 §1.4）
### 7.3 M6：统计终端（活动，见 §12）

### 7.4 整体验收（更新）
- [ ] P0–P2 全部完成（B1–B6 修复并验证）
- [ ] §1.4 边界用例全部通过
- [ ] 构建通过；升级 2.9.0 后流体/绑定存档读回正常（B7）
- [ ] M6 按 §12 完成（若立项）

---

## 8. 边界情况与安全策略（沿用 v0.4，已实现）

| 场景 | 策略 | 代码 |
|---|---|---|
| AE 不可达 | `CHANNEL_NONE` + `disableWorking()` 强制停机；不清绑定，恢复后自动恢复 | `doCoverThings` 72–79 |
| 目标未设置 | 停机 + GUI 提示 | 84–89 |
| 目标不存在 | 库存按 0（低于 N 模式会开机，符合"不足就合成"） | `AeStockReader` 返回 0 |
| 阈值 < 0 | `numbersLong(0,MAX)` 拒绝 | GUI |
| 阈值 = 0 | 机器常停（`stock < 0` 恒假） | `ThresholdMode.shouldWork` |
| 拆卸 | `onCoverRemoval` 恢复 `enableWorking()`；配置保留 NBT；Shift 清空（B3） | Cover |
| 多板同贴 | `isCoverPlaceable` 拒绝 | Cover |
| 未装 Nexus | `isNexusAvailable=false` 静默降级邻接（B6：按钮无提示） | `WirelessAeConnector` |
| 流体边界 | 物品/流体分通道独立计，不混算（附录 F 结论） | `AeStockReader` |

---

## 9. 构建与测试

```bash
# 构建
./gradlew build
# 客户端/服务端测试（GTNH 标准）
./gradlew runClient / runServer
```

- 依赖：AE2UEL `rv3-beta-977-GTNH` + AE2FC `1.5.88-gtnh`（原生流体已含）；Nexus `libs/ae_wireless_nexus-1.0.2.jar`（`compileOnly`，`dependencies.gradle:55`）；GT `libs/gregtech-5.09.52.594.jar`。
- 运行验证重点：B1 修复后 Nexus 面板；§1.3 流体识别；§1.4 边界用例。

---

## 10. FAQ（更新）

| 问题 | 原因 / 处理 |
|---|---|
| 点"连接AE"没反应 | **B1**：`WirelessAeConnector` 反射旧包名 → `nexusAvailable=false`。修 §1.1 |
| 网络列表为空 | Nexus 未装 / 玩家无 BUILD 权限 / 基站离线 / B1 未修 |
| GUI 打不开 | 检查是否走 MUI2（`GLOBAL_SWITCH_MUI2`）；`hasCoverGUI()` 已 true |
| 库存不更新 | 覆盖板所在区块未加载；或目标为"流体物品"（B2 未修） |
| 机器不停机 | 检查是否绑定网络、目标是否存在、模式/阈值是否正确 |
| 流体显示为物品 | **B2**：phantom 槽放入的是 ae2fc FluidDrop / GT ItemFluidDisplay，见 §1.3 |

---

## 11. 后续扩展方向（沿用 v0.4）

多目标联合判定 / 合成触发 / 流体扩展（§1.3）/ 滞回（Hysteresis）/ 正式端点模式 / 多方块整体控制。

---

## 12. M6 统计终端（v0.4 设计，未开工）

> 需求（2026-09-08）：基地放统计终端，集中查看/修改发信器（`PartLevelEmitter`）与本 mod 覆盖板。可选增强，不影响覆盖板独立运行。

**形态**：GT 单方块机器（MTE + MUI2，**免电信息终端**），连网复用覆盖板双通道逻辑（抽公共 `AeConnector`）。

**核心设计（沿用 v0.4，摘要）**：
- 枚举：`grid.getMachines(PartLevelEmitter.class)`（对齐 AE2 接口终端 `ContainerInterfaceTerminal` 308/338 行）；
- 发信器读写：`getAEInventoryByName(StorageName.CONFIG).getAEStackInSlot(0)` / `putAEStackInSlot(0,...)`；`getReportingValue()` / `setReportingValue(long)`；`Settings.LEVEL_TYPE` 区分物品/流体/能量（能量只读）；
- 覆盖板发现：`CoverRegistry`（WorldSavedData，键 `ae2_qof_covers`），安装/配置变更 upsert、拆卸删除、失联置灰；
- 覆盖板远程修改：服务端按 (dim,x,y,z,side) 定位 → `getCoverAtSide(side)` → setter → `markDirty()` + `issueCoverUpdate(side)`；
- 权限：`ISecurityGrid.hasPermission(player, BUILD)` 拦截修改；
- 边界：跨网络不可见（按 networkId 过滤）、能量发信器只读、区块未加载拒绝、不占频道。

**里程碑 M6 任务清单**（开工时逐项打勾）：
- [ ] 统计终端 GT 机器（MTE 单方块 + MUI2，免电，tooltip 打 `ae2qof`）
- [ ] 覆盖板注册表 `CoverRegistry`（WorldSavedData + upsert/删除/失联检测）
- [ ] 覆盖板暴露 `setMonitorTarget / setThreshold / setMode` 与 `issueCoverUpdate`
- [ ] 发信器列表 + 覆盖板列表（按 networkId 过滤）
- [ ] 编辑面板（抽公共 `ThresholdEditPanel`）与远程写回
- [ ] 权限拦截 + 边界处理
- [ ] GuideNH 页面 + lang 双语 + 发布 checklist

---

## 13. 待办与决策记录（v0.6 更新）

### 13.1 待办（当前活动）
- [x] ~~**P0**：修 B1（Nexus 反射旧包名）~~ — ✅ 已落实（`WirelessAeConnector.java:132-140`）
- [x] ~~**P1**：修 B3（Shift 清空漏删 NBT_PHANTOM）~~ — ✅ 已落实（`ItemStockMonitorCover.java:43`）
- [x] ~~**P1**：修 B2（流体识别增强）~~ — ✅ 已实现（`StockMonitorCoverData.java:76-145`），**待实测验证**
- [x] ~~**P2**：修 B4/B5/B6（GUI 小问题）~~ — ✅ B4 已删、B5 已确认链路、B6 已加提示
- [x] ~~**P0**：修 B8（网络选择 UX）~~ — ✅ 已修复（点选关闭+离线拦截+状态同步+日志）
- [x] ~~**P1**：修 B9（流体 NBT 读回不重建）~~ — ✅ 已修复；识别范围外物品待用户确认测试物品种类
- [ ] **P1**：决策 §1.2（自定义面板 vs Nexus 原生 WirelessSelectionPanel）—— 建议维持自定义面板
- [ ] **P1**：决策 §1.2（自定义面板 vs Nexus 原生 WirelessSelectionPanel）—— 建议维持自定义面板
- [ ] **P2**：M5 边界实测——§1.4
- [ ] **P3**：M6 统计终端——§12

### 13.2 已决策记录（追加）
| 日期 | 决策 | 理由 |
|---|---|---|
| 2026-09-09 | **Nexus 连接 bug（B1）已定位并给出修复**，P0 优先 | 反射旧包名导致 `nexusAvailable=false`，连接 UI 完全不显示 |
| 2026-09-09 | **Nexus 原生 UI（WirelessSelectionPanel）列为 P1 可选方案**，是否放弃"轻量只读"待用户拍板 | 用户期望显示 Nexus 原生网络选择 UI |
| 2026-09-09 | **流体"物品化"根因确认**：AE2FC 物品模拟（2.9.0 前）/ 流体物品识别缺失（2.9.0） | 详见 `docs/AE2-QoL流体识别问题调研与解决方案.md` |
| 2026-09-09 | **流体识别增强列 P1**：phantom 槽补 ae2fc FluidDrop / GT ItemFluidDisplay 识别 | 复用 `NetworkInventoryCache` 已验证逻辑 |
| 2026-09-09 | **B1–B6 全部落实**（代码核对）；新增实测 bug B8/B9 | 用户实测：面板可弹出但点选连不上 / 流体仍识别为物品 |
| 2026-09-09 | **§1.2 维持自定义面板**（不采用 Nexus 原生 WirelessSelectionPanel） | B 方案需实现 `WirelessBindableEndpoint`，改动大；当前先修 B8 UX |

（v0.4 其余决策——轻量只读、双通道、10tick、阈值禁负允0、拆卸保留配置、MUI2-only、统计终端免电/跨网络口径等——继续生效，不再重复。）

### 13.3 待决策
| 编号 | 问题 | 影响 | 建议 |
|---|---|---|---|
| Q-09 | §1.2 是否采用 Nexus 原生 `WirelessSelectionPanel`（需实现 `WirelessBindableEndpoint`） | 放弃"轻量只读"承诺 | 先修 B1 用自定义面板，B 方案后续评估 |

---

## 附录

### A. 参考模组关键源码索引（沿用 v0.4，更新 IGridProxyable 路径）
- **AE2：IGridHost / IGridProxyable**：`Applied-Energistics-2-Unofficial-rv3-beta-997-GTNH/.../appeng/api/networking/IGridHost.java`、**`appeng/me/helpers/IGridProxyable.java`（新包名，B1 依据）**
- **AE2：AENetworkProxy**：`.../appeng/me/helpers/AENetworkProxy.java`（`getNode()` → IGridNode）
- Nexus：`reference_src/Applied-Energistics-Wireless-Nexus-main/...`（TileWirelessController / WirelessNetworkService / WirelessNetworkSavedData / WirelessNetworkRecord / WirelessBindableEndpoint / GTWirelessUI / WirelessSelectionPanel / GTWirelessEndpoint）
- GT5：`reference_src/GT5-Unofficial-master/...`（Cover / CoverControlsWork / CoverableTileEntity / CoverRegistry / IMachineProgress / CoverBaseGui / GTGuis）
- MUI2：`TextFieldWidget.numbersLong(min,max)`（291–333 行）
- AE2 终端参照：`PartInterfaceTerminal` / `ContainerInterfaceTerminal`（getMachines 308/338 行）/ `PacketInterfaceTerminalUpdate`（增量同步 337–364 行）

### B. GT5 覆盖板 API 速查（GT 5.09.52.594 实测，沿用 v0.4）
（覆盖板基类 `Cover`、`IMachineProgress.enableWorking/disableWorking`、`doCoverThings` 调度、`CoverRegistry.registerCover`、`CoverPlacer.builder().onlyPlaceIf(...)`、NBT 读写钩子、`issueCoverUpdate` 同步、`getMinimumTickRate()` 原生降频——均已实现，代码与 v0.4 一致，不再重复展开。）

### C. AE2 流体双通道（997/977 源码结论，v0.5 摘要）
- 2.9.0 前：AE2FC 物品模拟（`ItemFluidPacket`/`ItemFluidDrop` 进 ITEM 通道）→ "流体被当成物品"的历史根源；
- 2.9.0 起：AE2UEL `CellInventory<StackType>` 泛型化 + `FluidCellInventory` + `FluidList`，FLUIDS 一等通道；AE2FC 1.5.80+ 流体单元挂 FLUID 通道（`getStackType()=AEFluidStackType.FLUID_STACK_TYPE`）；
- 本 mod 依赖 `rv3-beta-977-GTNH` 已含全部原生流体类，`AeStockReader` 流体分支直接可用；待补的是 phantom 槽对"流体物品"的识别（§1.3）。

---

*本文档 v0.6 由代码审读 + 源码复核（AE2UEL 977 / AE2FC / MUI2 / Nexus）+ 用户实测整理；代码行号以当前仓库为准。*
