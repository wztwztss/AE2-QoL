# 服务器端问题修复记录

版本：GTNH 2.9.0-beta-1 | MC1.7.10
时间戳：2026-08-29

## 问题概述

服务器端（云服务器/面板服）出现三个问题：
1. ME任务检测器服务器无效+右键显示IO端口功能
2. 二合一终端F键搜索无效
3. 自动上传记忆功能失效（配方名映射、记忆供应器、工作台配方映射）

## 问题1：ME任务检测器

### 问题描述
- 服务器上ME任务检测器无效
- 右键点击显示IO端口功能

### 根因分析
- `TileQuestDetector` 继承自 `TileIOPort`，导致右键显示IO端口GUI
- 应该继承自 `AENetworkTile`（而非 `AEBaseTile`，因为需要 `getProxy()` 方法访问ME网络）

### 修复方案
- 将 `TileQuestDetector extends TileIOPort` 改为 `TileQuestDetector extends AENetworkTile`

### 修改文件
- `src/main/java/com/wztwzt/ae2_qof/tile/TileQuestDetector.java`

### 状态
✅ 已修复

---

## 问题2：F键搜索

### 问题描述
- 二合一终端中按F键搜索无效

### 根因分析
- `KeyInputHandler.isAE2Gui()` 方法中没有包含 `com.wztwzt.ae2_qof.` 前缀的GUI类
- 导致在二合一终端中按F键时，无法识别当前打开的GUI

### 修复方案
- 在 `isAE2Gui()` 方法中添加 `className.startsWith("com.wztwzt.ae2_qof.")` 判断

### 修改文件
- `src/main/java/com/wztwzt/ae2_qof/client/event/KeyInputHandler.java`

### 状态
✅ 已修复

---

## 问题3：自动上传记忆功能

### 问题描述
1. 自动上传的记忆功能失效
2. 二合一终端的自动搜索名字不出现
3. 工作台的配方还是不会映射成合成

### 根因分析

#### 配置文件架构
- `recipe_names.json`：配方名映射，存储在客户端 `config/ae2_qof/` 目录下
- `remembered_providers.json`：记住的供应器，存储在客户端 `config/ae2_qof/` 目录下
- 内置默认映射：`/apu/recipe_type_names.json`（jar资源，47个配方映射）

#### 关键代码路径
1. **RecipeNameUtil.java**：配方名映射工具类
   - 静态初始化块加载配置文件
   - `loadBuiltinDefaults()` 从jar资源加载内置映射
   - `loadMappings()` 先加载内置映射，再加载用户配置文件
   - `writeTemplate()` 在配置文件不存在时创建空文件

2. **RecipeMapNameConfig.java**：GT配方池ID→中文搜索词缓存
   - `reload()` 从 `RecipeNameUtil.getMappingsView()` 加载映射
   - `resolveSearchKeyword()` 解析搜索关键词
   - 硬编码 `"crafting"` → `"合成"` 映射（第55-57行）

3. **PatternContainer.java**：样板编码小组件
   - `applyRecipeMapMeta()` 方法调用 `RecipeMapNameConfig.resolveSearchKeyword()`
   - `lastMachineName` 存储中文搜索词
   - `lastRecipeMap` 存储配方池ID

4. **ClientState.java**：客户端状态管理
   - `remembered_providers.json` 存储记住的供应器
   - `getRememberedProviderName()` 获取记住的供应器名

5. **ProvidersListS2CPacket.Handler**：供应器列表处理
   - 策略1：只有一个有效供应器时直接上传
   - 策略2：查已记住的Provider名字
   - 策略3：打开搜索界面

#### 可能的问题点
1. **内置映射加载失败**：`loadBuiltinDefaults()` 可能没有正确加载 `/apu/recipe_type_names.json`
2. **配置文件覆盖**：`writeTemplate()` 创建空文件可能覆盖内置映射
3. **缓存未刷新**：`RecipeMapNameConfig.reload()` 可能没有被正确调用
4. **配方名匹配失败**：`normalizeKey()` 可能导致匹配失败

### 已添加调试日志

为定位问题3的具体失效环节，已在以下位置添加详细日志：

1. **ClientProxy.handleProvidersList()**：
   - 输出 `recipeMap`、`rememberedProviders` 大小、匹配结果

2. **ClientState.rememberProvider()**：
   - 保存时输出 recipeMap、providerName、总数

3. **ClientState.getRememberedProviderName()**：
   - 查询时输出 recipeMap、结果、所有key

4. **RecipeNameUtil.loadMappings()**：
   - CONFIG_FILE路径、内置映射加载数量、用户映射数量、最终总数

5. **RecipeMapNameConfig.resolveSearchKeyword()**：
   - 缓存状态、各匹配分支的日志

### 待验证项
1. 检查 `RecipeNameUtil.loadMappings()` 的日志输出，确认内置映射是否加载成功
2. 检查 `config/ae2_qof/recipe_names.json` 文件内容
3. 检查 `RecipeMapNameConfig.resolveSearchKeyword()` 的返回值
4. 检查 `PatternContainer.applyRecipeMapMeta()` 的调用时机

### 状态
🔄 调试日志已添加，待测试验证

---

## 问题4：万能维护仓注册失败

### 问题描述
- 万能维护仓物品显示为`.name`，无法放置
- NEI和创造物品栏中找不到该物品
- `/give`获取后物品为空

### 根因分析
GT的`CommonMetaTileEntity`构造函数在preInit阶段检查`GregTechAPI.sPreloadStarted`，此时为false，抛出`IllegalAccessError: This Constructor has to be called in the load Phase`。

日志确认：
```
[DIAG] AE2MaintenanceHatchUniversal registration FAILED
java.lang.IllegalAccessError: This Constructor has to be called in the load Phase
```

### 修复方案
1. `MyMod.java` — dependencies声明 `after:gregtech`（软依赖，不改变加载顺序）
2. `CommonProxy.java` — 将MTE构造和合成表注册从 `preInit()` 移到 `init()`
3. 在init中添加注册成功日志 `[DIAG] AE2MaintenanceHatchUniversal registered OK`

### 修改文件
1. `src/main/java/com/wztwzt/ae2_qof/MyMod.java` — 添加after:gregtech依赖
2. `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` — MTE注册移至init阶段

### 修复：TST崩溃（required-after:gregtech导致加载顺序变化）

- **根因**：`required-after:gregtech` 改变了mod加载顺序，导致TST的ExtremeCraft配方加载时某物品为null
- **修复**：将 `required-after:gregtech` 改为 `after:gregtech`（软依赖）

### 状态
✅ 已修复，编译通过，待测试验证

---

## 修改文件汇总

### 已修改
1. `src/main/java/com/wztwzt/ae2_qof/tile/TileQuestDetector.java` - 问题1修复
2. `src/main/java/com/wztwzt/ae2_qof/client/event/KeyInputHandler.java` - 问题2修复
3. `src/main/java/com/wztwzt/ae2_qof/ClientProxy.java` - 问题3调试日志
4. `src/main/java/com/wztwzt/ae2_qof/client/ClientState.java` - 问题3调试日志
5. `src/main/java/com/wztwzt/ae2_qof/util/RecipeNameUtil.java` - 问题3调试日志
6. `src/main/java/com/wztwzt/ae2_qof/common/RecipeMapNameConfig.java` - 问题3调试日志
7. `src/main/java/com/wztwzt/ae2_qof/MyMod.java` - 问题4：添加gregtech依赖
8. `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` - 问题4：MTE注册移至init阶段

---

## 测试计划

### 问题1测试
1. 构建jar，替换至测试mods目录
2. 启动游戏，右键点击ME任务检测器
3. 验证显示的是任务检测器GUI，而不是IO端口GUI

### 问题2测试
1. 构建jar，替换至测试mods目录
2. 打开二合一终端
3. 按F键，验证搜索框获得焦点

### 问题3测试
1. 构建jar，替换至测试mods目录
2. 编码处理配方，验证自动搜索名字出现
3. 编码工作台配方，验证映射成"合成"
4. 验证自动上传记忆功能正常
5. 查看日志文件，搜索 `[Upload]` 和 `[APU]` 标签定位失效环节

---

## 接下来的任务

1. **问题3调试**：
   - 启动游戏重现问题
   - 查看 `fml-client-latest.log`，搜索 `[Upload]` 和 `[APU]` 标签
   - 根据日志定位具体失效环节

2. **文档更新**：
   - 更新 `CHANGELOG.md`
   - 更新 `README.md`
   - 更新 `docs/MOD_MAP.md`
