# GTNH 迁移指南

> 来源：ExampleMod1.7.10/docs/migration.md

## 通用迁移步骤

适用于不使用特殊功能（仅有 Forge 和库依赖）的普通模组。若涉及 CoreMod、Mixin、Shadow、Access Transformer、ASM 等，需额外步骤。

### 8步迁移流程

1. 从[模板](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/migration.zip)复制并替换所有文件到你的仓库，**保留原有的 `build.gradle`**
2. 将 `build.gradle(.kts)` 中的所有 `repositories` 复制到 `repositories.gradle`
3. 将 `build.gradle(.kts)` 中的所有 `dependencies` 复制到 `dependencies.gradle`
4. 用模板中的 `build.gradle` 替换你的 `build.gradle(.kts)`
   - 如有自定义任务/配置，迁移到 `addon.gradle`
5. 根据你的模组调整 `gradle.properties`
6. 确保 `src/main/resources/mcmod.info` 包含占位符：
   - `${modId}`
   - `${modName}`
   - `${modVersion}`
   - `${minecraftVersion}`
7. 重新导入项目到 IDE（重启并清除缓存）
8. 运行 `./gradlew clean setupDecompWorkspace`

## Mixin 配置

参考模板的 [example mixin configuration branch](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-mixins)：

1. 从 `mixins.yourModId.json` 提取 mixins 和 plugin 配置到 `gradle.properties`
2. 按参考示例实现 MixinPlugin
3. 删除 `mixins.mymodid.json`

---

## 本项目迁移注意事项

针对 AE2-QoL-1.7.10-GTNH 项目：

1. 已有 Mixin 配置，修改时需核对：
   - `usesMixins = true`
   - `mixinsPackage` 配置
   - `separateMixinSourceSet` 配置
2. 构建使用 GTNH 的 `gtnhconvention` 插件
3. 参考 `gradle.properties` 中的完整配置项
