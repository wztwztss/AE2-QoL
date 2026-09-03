# 自适应电网：舱室 UI 复刻 GTNL + 传送/高亮修复指导

> 适用项目：AE2-QoL-1.7.10-GTNH
> 参考实现：`reference_src/GT-Not-Leisure-dev-290/.../EnergyMonitorGui.java`（MonitoringListWidget）
> 范围：**仅针对舱室列表 Tab** 的 UI 复刻，以及"传送和高亮都不能用"的根因修复。
> 行号基于撰写时核对的实际源码。

---

## 一、问题诊断

### 1.1 传送 / 高亮不可用的根因（三条，按可能性排序）

#### 根因 A（高亮 + 传送共同，最可能）：客户端 GUI 依赖 MTE 实例字段 `networkOwner`，未通过同步机制可靠下发

**位置**：`AdaptiveNetTerminal.java` L831-838（`buildHatchListTab` 行点击回调）

```java
.onMousePressed((event) -> {
    if (networkOwner != null) {          // ← 客户端 MTE 的 networkOwner 可能为 null
        boolean shift = Minecraft.getMinecraft().gameSettings.keyBindSneak.getIsKeyPressed();
        int action = shift ? HatchActionPacket.ACTION_TELEPORT : HatchActionPacket.ACTION_HIGHLIGHT;
        ModNetwork.CHANNEL.sendToServer(new HatchActionPacket(
            action, networkOwner.toString(), networkFrequency, entry.index));
    }
    return true;
});
```

**问题**：`networkOwner` / `networkFrequency` 是 `AdaptiveNetTerminal` 的实例字段，仅在服务端 `onFirstTick`（L265）赋值。客户端 MTE 实例依赖 GT 描述包（`getDescriptionPacket`/`onDataPacket`）同步 NBT，但该同步**不保证在 GUI 打开前完成**，且 MTEHatch 的描述包不一定携带自定义 NBT。一旦客户端 `networkOwner == null`，`HatchActionPacket` **根本不会发出**——高亮和传送同时失效。

> 佐证：`buildStatusTab` L514 也直接读 `networkOwner` 显示，若客户端该字段为 null 会显示字符串 `"null"`。可在游戏内观察状态 Tab 的 owner 显示来确认此根因。

**修复方向**：用 ModularUI `StringSyncValue` 把 owner 字符串、`IntSyncValue` 把 frequency 显式同步到客户端（对齐 GTNL `OWNER_SYNC_KEY` 的做法），GUI 从 sync 值读取，不再依赖 MTE 字段。详见 §3.1。

---

#### 根因 B（传送特有，确定）：跨维度传送未实现，`setPositionAndUpdate` 只改坐标不切维度

**位置**：`HatchActionPacket.java` L178-180

```java
private void handleTeleport(EntityPlayerMP player, int x, int y, int z) {
    player.setPositionAndUpdate(x + 0.5, y + 1, z + 0.5);   // ← 不切换维度
}
```

**问题**：自适应电网的仓室可以放在任意维度（`AdaptiveHatchHelper.posDim`），但 `handleTeleport` 完全忽略 `dim`。当仓室与玩家不在同一维度时，`setPositionAndUpdate` 只在当前维度改坐标——玩家要么卡在墙里，要么看似"没反应"。同维度仓室传送应正常，跨维度必然失效。

**修复方向**：加维度判断，跨维度走 `ServerConfigurationManager.transferPlayerToDimension`。详见 §3.2。

---

#### 根因 C（高亮特有，次要）：自定义 `WirelessHighlightRenderer` 裸 GL 渲染，无悬浮文字，且与通用字段冲突

**位置**：`WirelessHighlightRenderer.java`（Tessellator GL_LINES）；`ClientProxy.java` L190-196

**问题**：
1. 渲染器用裸 `Tessellator.startDrawing(GL_LINES)` + 手动 12 条边，在某些光影/渲染管线状态下可能不可见或深度异常。
2. 高亮数据写入 `ClientState.highlightPositions` / `highlightEnabled`（L57-58），这两个字段是**通用字段**，可能被模组内其他功能（如 APU 高亮）覆盖；而专门为 adaptive 准备的 `adaptiveHighlightPositions` / `adaptiveHighlightExpiryTick`（L60-61）**定义了却从未使用**。
3. 无悬浮文字（GTNL 高亮会显示机器名 + "在其他维度"提示）。

**修复方向**：迁移到 AE2 原生 `BlockPosHighlighter.highlightBlocks`（GTNL 同款），保留现有 100 tick 清除机制。详见 §3.3。

---

### 1.2 当前舱室 UI 与 GTNL 的差距

| 能力 | GTNL EnergyMonitorGui | 本系统 buildHatchListTab |
|---|---|---|
| 行内物品图标 | `ItemDrawable(iconStack).size(16,16)` | ❌ 纯文字 `[D]/[E]/[LS]/[LT]` 标签 |
| 彩色 tier 名 | `GTUtility.getColoredTierNameFromTier` | ❌ `HatchType.getTierName`（无色） |
| 行文本格式 | `名称 灰色EU/t 白色(彩色tier)` | `[标签] 名称 (A tier) 绿色EU/t` |
| 独立高亮按钮 | 黄色 `[]` 按钮 + shift 点击行 | ❌ 整行一个按钮 |
| tooltip 内容 | 维度 / 坐标 / 归属（ownerName） | ⚠️ 只有坐标 + 操作提示，**无归属** |
| 滚动 | `VerticalScrollData` + 滚动到底 load more | ⚠️ ListWidget 可滚，但 **50 行硬截断** + `...N more` |
| 高亮实现 | AE2 `BlockPosHighlighter`（描边+悬浮文字） | ❌ 自定义 Tessellator 线框 |
| 归属显示 | `ownerName`（玩家名） | ❌ 无 |

---

## 二、GTNL 舱室显示板块设计解析（复刻依据）

> 源文件：`reference_src/GT-Not-Leisure-dev-290/src/main/java/com/science/gtnl/common/gui/modularui/EnergyMonitorGui.java`

### 2.1 整体结构

```
MonitoringListWidget (继承自定义 ListWidget, VerticalScrollData)
├── 静态内容（buildStaticContent）
│   ├── owner 行
│   ├── 总能量行（含模式按钮）
│   ├── 平均 EU/t 行
│   ├── 预计耗尽/充满行
│   └── 统计行
└── dynamicRows (Flow.column, 动态重建)
    ├── 行 1: createRowWidget
    ├── 行 2: createRowWidget
    ├── ...
    └── [滚动更多] 提示（hasMoreRows 时）
```

- `onUpdate`：检测 `visibleRowsRevision` 变化 → `rebuildDynamicRows()`
- `onMouseScroll`：向下滚动且 `isAtBottom()` 且有更多 → `machine.loadMoreRows()` + 重建

### 2.2 单行布局（createRowWidget，L238-266）

```
Flow.row (TERMINAL_TEXT_WIDTH, ROW_MIN_HEIGHT=18, wrap)
├── contentButton (ButtonWidget, background=EMPTY, 无主题背景)
│   ├── tooltip: buildRowTooltip(row)  → "gtnl.energy_monitor.tooltip" 模板
│   │                              参数: [dim, x, y, z, ownerName]
│   ├── onMousePressed: shift + (左键|右键) → highlightRow
│   └── createRowContent (ParentWidget)
│       ├── ItemDrawable(iconStack.copy()).size(16,16).pos(0,1)   ← 图标
│       └── TextWidget(buildRowText).pos(18,0)                     ← 文本
│           文本 = 白色名称 + 灰色 formattedEut + " EU/t " + 白色"(" + 彩色tier + 白色")"
└── createHighlightButton (ButtonWidget, 黄色 "[]")
    └── onMousePressed: (左键|右键) → highlightRow
```

### 2.3 高亮实现（highlightRow，L365-374）

```java
BlockPosHighlighter.highlightBlocks(
    syncManager.getPlayer(),
    Collections.singletonList(new DimensionalCoord(x, y, z, dim)),  // 4参构造: x,y,z,dim
    row.getDisplayName(),                                          // 悬浮标题
    PlayerMessages.MachineHighlighted.getUnlocalized(),            // 悬浮行1
    PlayerMessages.MachineInOtherDim.getUnlocalized());            // 悬浮行2
```

- `DimensionalCoord` 用 **4 参构造** `(x, y, z, dim)`，不是 `(World, x, y, z)`。
- 高亮由 AE2 自己的 `HighlighterManager` 管理过期，无需手动渲染。

---

## 三、修复：传送与高亮可用

### 3.1 根因 A 修复：owner / frequency 显式同步到客户端

**文件**：`AdaptiveNetTerminal.java`

**① `buildUI` 内新增 sync 值**（在 L412 `syncManager.syncValue("wFr", frequencySync)` 附近）：

```java
// 新增：owner 字符串只读同步（解决客户端 MTE 字段不可靠问题）
StringSyncValue ownerSync = new StringSyncValue(
    () -> networkOwner != null ? networkOwner.toString() : "",
    v -> { /* 只读，不允许客户端改 owner */ });
syncManager.syncValue("wNO", ownerSync);
```

> 需要 import `com.cleanroommc.modularui.value.sync.StringSyncValue`。

**② `buildHatchListTab` 签名加参数**（L789）：

```java
// 原：private Flow buildHatchListTab()
private Flow buildHatchListTab(StringSyncValue ownerSync, IntSyncValue frequencySync) {
```

**③ `addPage` 调用处更新**（L486）：

```java
.addPage(buildHatchListTab(ownerSync, frequencySync))
```

**④ 行点击回调改用 sync 值**（L831-838 替换）：

```java
.onMousePressed((event) -> {
    String ownerStr = ownerSync.getValue();
    if (ownerStr == null || ownerStr.isEmpty()) {
        return true;   // owner 尚未同步到，静默忽略
    }
    boolean shift = net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindSneak.getIsKeyPressed();
    int action = shift ? HatchActionPacket.ACTION_TELEPORT : HatchActionPacket.ACTION_HIGHLIGHT;
    ModNetwork.CHANNEL.sendToServer(new HatchActionPacket(
        action, ownerStr, frequencySync.getIntValue(), entry.index));
    return true;
})
```

> 同理，`buildStatusTab` L514 的 `networkOwner` 显示也建议改用 `ownerSync.getValue()`，保持一致。

---

### 3.2 根因 B 修复：跨维度传送

**文件**：`HatchActionPacket.java`

**① `handleServer` 调用处传入 dim**（L159-163）：

```java
case ACTION_TELEPORT:
    if (player.getUniqueID().equals(uuid)) {
        handleTeleport(player, x, y, z, dim);   // 加 dim 参数
    }
    break;
```

**② 替换 `handleTeleport` 方法**（L178-180）：

```java
private void handleTeleport(EntityPlayerMP player, int x, int y, int z, int dim) {
    if (player.dimension == dim) {
        player.setPositionAndUpdate(x + 0.5, y + 1, z + 0.5);
        return;
    }
    // 跨维度传送
    net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
    net.minecraft.world.WorldServer targetWorld = server.worldServerForDimension(dim);
    if (targetWorld == null) return;
    server.getConfigurationManager().transferPlayerToDimension(player, dim,
        new net.minecraft.world.Teleporter(targetWorld) {
            @Override
            public void placeInPortal(net.minecraft.entity.Entity entity,
                                      net.minecraft.world.World world,
                                      double px, double py, double pz, float yaw) {
                entity.setLocationAndAngles(x + 0.5, y + 1, z + 0.5,
                    entity.rotationYaw, entity.rotationPitch);
                entity.motionX = 0;
                entity.motionY = 0;
                entity.motionZ = 0;
            }
        });
}
```

> ⚠️ `Teleporter` 构造在 1.7.10 是 `Teleporter(WorldServer)`。若编译报构造签名差异，用 `new Teleporter(targetWorld) {}` 匿名类即可。
> ⚠️ 若后续接入团队聚合（见上一份指导 §2.2），权限判断 `player.getUniqueID().equals(uuid)` 需改为 `AdaptiveTeamHelper.isMemberOf(player.getUniqueID(), uuid)`。

---

### 3.3 根因 C 修复：迁移到 AE2 `BlockPosHighlighter`

**文件**：`ClientProxy.java`（`handleWirelessHighlight`，L190-196）

**替换实现**：

```java
@Override
public void handleWirelessHighlight(final WirelessHighlightPacket message) {
    Minecraft.getMinecraft().func_152344_a(() -> {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;

            java.util.List<appeng.api.util.DimensionalCoord> coords = new java.util.ArrayList<>();
            for (int[] p : message.positions) {
                // DimensionalCoord 4参构造: (x, y, z, dim) —— 对齐 GTNL
                coords.add(new appeng.api.util.DimensionalCoord(p[1], p[2], p[3], p[0]));
            }
            appeng.client.render.highlighter.BlockPosHighlighter.highlightBlocks(
                mc.thePlayer,
                coords,
                "ae2qol_adaptive_net_highlight",          // 唯一 id（同 id 覆盖）
                message.enable ? "Adaptive Net Hatch" : "", // 悬浮标题
                message.enable ? "左键列表行可重新定位" : "");
        } catch (Throwable t) {
            // 降级：AE2 高亮不可用时回退旧渲染器
            ClientState.highlightPositions = message.positions;
            ClientState.highlightEnabled = message.enable;
        }
    });
}
```

**配套改动**：
- `WirelessHighlightRenderer.INSTANCE` 的事件注册（`ClientProxy` L68）**可保留**作为降级路径，但不再是主路径。若确认 AE2 高亮稳定，可注释掉该行并删除渲染器类。
- `ClientState.adaptiveHighlightPositions` / `adaptiveHighlightExpiryTick`（L60-61）目前是死字段，可删除或留作未来扩展。
- 服务端 `HatchActionPacket.scheduleClear`（100 tick 后发空列表）**无需改动**——空列表 + 同 id 调用 `highlightBlocks` 即覆盖清除。

> ⚠️ `BlockPosHighlighter.highlightBlocks` 第 3 参 id 相同会覆盖上一次高亮。若需要同时高亮多个仓室，每次调用都用同一 id 即可（后者覆盖前者，符合"只高亮当前选中"的预期）。

---

## 四、复刻：舱室显示板块

### 4.1 数据层：`HatchEntry` 增加 `ownerName`

**文件**：`HatchListCache.java` L25-49

```java
public static class HatchEntry {
    public final String name;
    public final short metaId;
    public final int eut;
    public final int tier;
    public final int amps;
    public final int hatchType;
    public final int index;
    public final int x, y, z, dim;
    public final String ownerName;   // 新增：归属玩家名（或 UUID 前8位）

    public HatchEntry(String name, short metaId, int eut, int tier, int amps,
                      int hatchType, int index, int x, int y, int z, int dim,
                      String ownerName) {   // 新增尾参
        this.name = name != null ? name : "";
        this.metaId = metaId;
        this.eut = eut;
        this.tier = tier;
        this.amps = Math.max(1, amps);
        this.hatchType = hatchType;
        this.index = index;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.ownerName = ownerName != null ? ownerName : "";
    }
}
```

> `x/y/z/dim` 已有，无需新增。

---

### 4.2 `HatchListSyncPacket` 增加 `ownerName` 序列化

**文件**：`HatchListSyncPacket.java`

**① 字段**：构造函数加 `ownerName`（或在 entry 内携带，推荐 entry 内携带，packet 无需顶层字段）。

**② `toBytes` 每个 entry 加**（L86-98，在 `dim` 之后）：

```java
cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, e.ownerName != null ? e.ownerName : "");
```

**③ `fromBytes` 对应读取**（L49-63，在 `dim` 之后）：

```java
String ownerName = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
this.entries.add(new HatchListCache.HatchEntry(
    name, metaId, eut, tier, amps, hatchType, index, x, y, z, dim, ownerName));
```

**④ 服务端构建 entry 时解析 ownerName**（`AdaptiveNetTerminal.sendHatchListSync` L168-171）：

```java
// 在 for 循环外预解析一次
String ownerName = "";
if (networkOwner != null) {
    net.minecraft.entity.player.EntityPlayerMP ownerPlayer =
        MinecraftServer.getServer().getConfigurationManager().func_152612_a(networkOwner);
    if (ownerPlayer != null) {
        ownerName = ownerPlayer.getCommandSenderName();
    } else {
        ownerName = networkOwner.toString().substring(0, 8);  // 离线时显示 UUID 前8位
    }
}

// 构造 entry 时加尾参
entries.add(new HatchListCache.HatchEntry(
    h.getCachedName(), h.getCachedMetaId(), eut, tier, amps,
    ht.ordinal(), globalIndex,
    h.getX(), h.getY(), h.getZ(), h.getDim(),
    ownerName));   // 新增
```

> `func_152612_a(UUID)` 是 `ServerConfigurationManager.getPlayerByUUID` 的 SRG 名。若编译环境映射到 `getPlayerByUUID`，直接用该名即可。

---

### 4.3 GUI 行渲染：图标 + 文本 + 操作按钮（复刻 GTNL 外观）

**文件**：`AdaptiveNetTerminal.java` `buildHatchListTab`（L801-846 整体重写）

**① import 补充**：

```java
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
```

**② 行构建替换原 L808-840 的 for 循环**：

```java
if (cache != null) {
    for (int i = 0; i < cache.entries.size(); i++) {   // 去掉 50 行上限
        final HatchListCache.HatchEntry entry = cache.entries.get(i);

        // —— 图标 ——
        net.minecraft.item.ItemStack iconStack = null;
        if (entry.metaId >= 0 && entry.metaId < GregTechAPI.METATILEENTITIES.length
                && GregTechAPI.METATILEENTITIES[entry.metaId] instanceof MetaTileEntity) {
            iconStack = ((MetaTileEntity) GregTechAPI.METATILEENTITIES[entry.metaId]).getStackForm(1L);
        }
        final net.minecraft.item.ItemStack finalIcon = iconStack;

        // —— 行内容（图标 + 文本）——
        com.cleanroommc.modularui.widget.ParentWidget<?> rowContent =
            new com.cleanroommc.modularui.widget.ParentWidget<>().size(CONTENT_W - 28, 18);
        if (finalIcon != null && finalIcon.getItem() != null) {
            rowContent.child(new ItemDrawable(finalIcon.copy()).asWidget().size(16, 16).pos(0, 1));
        }
        rowContent.child(
            new TextWidget<>(IKey.dynamic(() -> {
                String tierName = GTUtility.getColoredTierNameFromTier((byte) entry.tier);
                return EnumChatFormatting.WHITE + entry.name + " "
                    + EnumChatFormatting.GRAY + formatEU(entry.eut, displayMode) + " EU/t "
                    + EnumChatFormatting.WHITE + "(" + tierName + EnumChatFormatting.WHITE + ")";
            }))
            .textAlign(com.cleanroommc.modularui.utils.Alignment.CenterLeft)
            .size(CONTENT_W - 46, 18)
            .pos(finalIcon != null ? 18 : 0, 0));

        // —— 整行可点击按钮（复刻 GTNL contentButton 无主题背景）——
        ButtonWidget<?> rowButton = new ButtonWidget<>()
            .background(IDrawable.EMPTY)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .size(CONTENT_W - 28, 18)
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.GRAY
                    + "dim:" + entry.dim + "  [" + entry.x + ", " + entry.y + ", " + entry.z + "]"));
                t.addLine(IKey.str(EnumChatFormatting.AQUA + "归属: " + entry.ownerName));
                t.addLine(IKey.str(EnumChatFormatting.YELLOW + "左键: 高亮  |  Shift+左键: 传送"));
            })
            .onMousePressed(event -> {
                String ownerStr = ownerSync.getValue();
                if (ownerStr == null || ownerStr.isEmpty()) return true;
                boolean shift = Minecraft.getMinecraft().gameSettings.keyBindSneak.getIsKeyPressed();
                int action = shift ? HatchActionPacket.ACTION_TELEPORT : HatchActionPacket.ACTION_HIGHLIGHT;
                ModNetwork.CHANNEL.sendToServer(new HatchActionPacket(
                    action, ownerStr, frequencySync.getIntValue(), entry.index));
                return true;
            })
            .child(rowContent);

        // —— 右侧操作按钮（复刻 GTNL 黄色 []）——
        ButtonWidget<?> actionButton = new ButtonWidget<>()
            .background(IDrawable.EMPTY)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .size(24, 18)
            .child(new TextWidget<>(IKey.str(EnumChatFormatting.YELLOW + "[]"))
                .textAlign(com.cleanroommc.modularui.utils.Alignment.Center)
                .size(24, 18))
            .onMousePressed(event -> {
                // 点击 [] 按钮 = 高亮（对齐 GTNL）
                String ownerStr = ownerSync.getValue();
                if (ownerStr == null || ownerStr.isEmpty()) return true;
                ModNetwork.CHANNEL.sendToServer(new HatchActionPacket(
                    HatchActionPacket.ACTION_HIGHLIGHT, ownerStr,
                    frequencySync.getIntValue(), entry.index));
                return true;
            });

        Flow rowFlow = Flow.row()
            .width(CONTENT_W)
            .coverChildrenHeight(18)
            .crossAxisAlignment(com.cleanroommc.modularui.utils.Alignment.CrossAxis.START)
            .child(rowButton)
            .child(actionButton);

        list.child(rowFlow);
    }
    // 删除原 L841-845 的 "...N more" 截断提示
}
```

> ⚠️ `ParentWidget` 的完整类名是 `com.cleanroommc.modularui.widget.ParentWidget`（GTNL import 用 `com.cleanroommc.modularui.widget.ParentWidget`）。若本项目 ModularUI2 版本包名不同，以 GTNL 实际 import 为准（GTNL L26 是 `com.cleanroommc.modularui.widget.ParentWidget`）。
> ⚠️ `disableThemeBackground` / `disableHoverThemeBackground` / `disableHoverBackground` / `disableHoverOverlay` 是 GTNL 用到的方法，本项目 ModularUI2 版本（2.3.73）应支持；若编译报方法不存在，退化为普通 `ButtonWidget` 不加这些调用即可。

---

### 4.4 tooltip：维度 / 坐标 / 归属

已在 §4.3 的 `tooltipBuilder` 中实现：

```
dim:0  [123, 64, -45]
归属: PlayerName
左键: 高亮  |  Shift+左键: 传送
```

对齐 GTNL `buildRowTooltip` 的参数顺序（dim → x,y,z → ownerName）。

---

### 4.5 滚动条 + load more

**简单方案（推荐起步）**：
- 去掉 `MAX_HATCH_COORD_DISPLAY = 50` 硬截断（§4.3 已全量渲染）。
- `ListWidget` 已有 `scrollDirection(VerticalScrollData)`（L802），自动显示滚动条。
- 删除 `...N more` 提示。

**完整方案（对齐 GTNL，量级大时再上）**：
- 参考 GTNL `MonitoringListWidget.onMouseScroll`（L509-517）+ `isAtBottom`（L519-526）：
  ```java
  @Override
  public boolean onMouseScroll(UpOrDown dir, int amount) {
      boolean handled = super.onMouseScroll(dir, amount);
      if (dir.isDown() && hasMore && isAtBottom()) {
          visibleCount += 20;
          rebuildRows();
          return true;
      }
      return handled;
  }
  ```
- 需要自定义 `ListWidget` 子类并重写 `onMouseScroll`。本系统当前直接 `new ListWidget()`，若要实现 load more，需建一个内部类继承 `ListWidget`。
- 通常自适应电网的仓室数量在几十以内，简单方案足够。

---

### 4.6 交互保留说明

| 操作 | GTNL | 本系统（保留） |
|---|---|---|
| 左键行 | 无（需 shift） | 高亮 |
| Shift+左键行 | 高亮 | 传送 |
| 点击 `[]` 按钮 | 高亮 | 高亮（§4.3 已实现） |

本系统的"左键高亮 / Shift+左键传送"比 GTNL 更友好，**保留不变**；`[]` 按钮作为视觉复刻 + 快捷高亮入口。

---

## 五、实施顺序

| 步骤 | 内容 | 依赖 | 风险 |
|---|---|---|---|
| 1 | §3.1 owner/frequency sync 修复 | 无 | 低（高亮+传送的共同前提） |
| 2 | §3.2 跨维度传送 | 步骤 1 | 低 |
| 3 | §3.3 AE2 高亮迁移 | 步骤 1 | 中（需验证 AE2 高亮在 GTNH 环境正常） |
| 4 | §4.1-4.2 HatchEntry 加 ownerName + packet | 无 | 低 |
| 5 | §4.3-4.5 GUI 行复刻（图标/彩色tier/tooltip/滚动） | 步骤 1, 4 | 中（ModularUI API 适配） |

建议 1-3 作为"功能修复"一轮提交，4-5 作为"UI 复刻"一轮提交，方便回滚。

---

## 六、验证清单

- [ ] **§3.1**：打开终端，状态 Tab 的 owner 显示正常（非 "null"）；子仓 Tab 左键行能发出 packet（可在服务端日志加临时打印验证）。
- [ ] **§3.2**：同维度仓室 Shift+左键传送正常；跨维度仓室传送后维度切换 + 坐标正确，不卡墙。
- [ ] **§3.3**：左键行后，AE2 方块描边出现 + 悬浮文字显示；约 100 tick（5秒）后自动消失；多个仓室依次点击，后者覆盖前者。
- [ ] **§4.3**：每行左侧显示对应仓室物品图标（16×16）；tier 名带 GT 颜色；文本格式为 `名称 灰色EU/t (彩色tier)`；右侧黄色 `[]`。
- [ ] **§4.4**：hover 行显示 `dim:X [x,y,z]` + `归属: 玩家名` + 操作提示。
- [ ] **§4.5**：仓室超过列表可视区域时出现滚动条，可滚动查看全部；无 `...N more` 截断。
- [ ] **回归**：设置 Tab 换仓室物品后，子仓列表的 tier/amps/EU-t 实时刷新（依赖上一份指导 §1.4 的 `markHatchListDirty` 修复）。
- [ ] **编译**：`gradlew compileJava` 通过，重点检查 `ParentWidget` 包名和 `disableThemeBackground` 方法是否与当前 ModularUI2 版本匹配。

---

## 七、参考文件索引

| 文件 | 用途 |
|---|---|
| `reference_src/.../EnergyMonitorGui.java` | GTNL 行渲染 / 滚动 / 高亮调用（复刻依据） |
| `reference_src/.../EnergyMonitorHighlightTarget.java` | GTNL 高亮目标数据结构 |
| `src/.../hatch/adaptive/AdaptiveNetTerminal.java` | 终端 GUI（改动主战场） |
| `src/.../hatch/adaptive/HatchListCache.java` | HatchEntry（加 ownerName） |
| `src/.../network/HatchListSyncPacket.java` | 列表序列化（加 ownerName） |
| `src/.../network/HatchActionPacket.java` | 高亮/传送服务端处理（跨维度修复） |
| `src/.../network/WirelessHighlightPacket.java` | 高亮 S→C 包（客户端处理改 AE2） |
| `src/.../ClientProxy.java` | `handleWirelessHighlight`（AE2 高亮接入点） |
| `src/.../client/render/WirelessHighlightRenderer.java` | 旧自定义渲染器（降级路径 / 可删除） |
