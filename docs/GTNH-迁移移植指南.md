# GTNH 迁移与移植指南

> 来源：ExampleMod1.7.10/docs/migration.md + porting.md

---

## 一、迁移（Migration）

适用于不使用特殊功能（仅有 Forge 和库依赖）的普通模组。若涉及 CoreMod、Mixin、Shadow、Access Transformer、ASM 等，需额外步骤。

### 8步迁移流程

1. 从[模板](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/migration.zip)复制并替换所有文件到你的仓库，**保留原有的 `build.gradle`**
2. 将 `build.gradle(.kts)` 中的所有 `repositories` 复制到 `repositories.gradle`
3. 将 `build.gradle(.kts)` 中的所有 `dependencies` 复制到 `dependencies.gradle`
4. 用模板中的 `build.gradle` 替换你的 `build.gradle(.kts)`
   - 如有自定义任务/配置，迁移到 `addon.gradle`
5. 根据你的模组调整 `gradle.properties`
6. 确保 `src/main/resources/mcmod.info` 包含占位符：`${modId}`、`${modName}`、`${modVersion}`、`${minecraftVersion}`
7. 重新导入项目到 IDE（重启并清除缓存）
8. 运行 `./gradlew clean setupDecompWorkspace`

### Mixin 配置

参考模板的 [example mixin configuration branch](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-mixins)：

1. 从 `mixins.yourModId.json` 提取 mixins 和 plugin 配置到 `gradle.properties`
2. 按参考示例实现 MixinPlugin
3. 删除 `mixins.mymodid.json`

---

## 二、移植（Porting）

将高版本模组移植到 MC 1.7.10 的完整指南。

### 阶段1：设置仓库和构建系统

1. 查阅原模组 README/Wiki/Docs，了解特殊构建需求
2. Fork 原仓库（保留提交历史）
3. 按上面的迁移指南应用构建迁移

### 阶段2：清理依赖

- 移除对具体 jar 文件的依赖（通常是 `lib` 文件夹）
- 检查依赖是否在 Maven 仓库中：
  - 查看项目 README 获取仓库信息
  - 如不在仓库但有宽松许可证（如 MIT），可自行发布：
    1. Fork 依赖仓库
    2. 删除 `jitpack.yml` 和 `.github/workflows/gradle.yml`
    3. 运行 `./gradlew clean setupCIWorkspace` 确认构建
    4. 创建 tag 触发 GitHub 构建
    5. 在 `https://jitpack.io/` 查找发布
- 如无在线依赖，可上传 jar 到 JitPack（确保有权限）
- 如依赖其他模组，需先移植依赖模组

### 阶段3：准备移植

构建项目，识别错误类型：

| 错误类型 | 说明 |
|---------|------|
| 缺失引用 | 包/类/方法/字段被重命名、移动、重构或不存在 |
| 构建错误 | 需要添加新依赖或调整构建配置 |

先修复所有构建错误，再处理代码移植。

### 阶段4：实际移植

推荐策略：从简单到复杂

1. **修复导入**：删除错误导入，用 IDE 重新导入
2. **创建桩代码**：移除无法快速修复的代码，用空方法替代（用 TODO 标记）
3. **构建并运行**
4. **修复崩溃和关键错误**
5. **逐步修复小问题**
6. **评估功能价值**：不值得修复的功能考虑移除
7. **修复引入的 bug**：测试并验证功能

### 阶段5：最终测试

- 测试所有功能是否正常
- 检查是否有隐藏的依赖和 workaround
- 修复移植过程中引入的 bug

---

## 三、本项目注意事项

针对 AE2-QoL-1.7.10-GTNH 项目：

1. 已有 Mixin 配置，修改时需核对 `usesMixins`、`mixinsPackage`、`separateMixinSourceSet`
2. 构建使用 GTNH 的 `gtnhconvention` 插件
3. AE2 部分类被 GTNH 修改，不能直接照搬原版 AE2
4. Mixin 注入点需适配 GTNH 2.9.0-beta-1
5. 移植后必须检查 `mixin.log`
6. 参考 `reference_src` 中的其他模组代码
