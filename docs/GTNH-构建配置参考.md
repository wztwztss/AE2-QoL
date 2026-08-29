# GTNH 构建配置参考

> 来源：ExampleMod1.7.10/gradle.properties, dependencies.gradle, repositories.gradle

## gradle.properties 核心配置

### 模组基本信息

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `modName` | 模组显示名称 | `MyMod` |
| `modId` | 模组ID（区分大小写，用于 Mixin JSON 自动生成） | `mymodid` |
| `modGroup` | 根包名 | `com.myname.mymodid` |
| `minecraftVersion` | MC 版本 | `1.7.10` |
| `forgeVersion` | Forge 版本 | `10.13.4.1614` |

### MCP 映射

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `channel` | MCP 映射通道 | `stable` |
| `mappingsVersion` | MCP 映射版本 | `12` |

### Java/编译配置

| 配置项 | 说明 | 选项 |
|--------|------|------|
| `enableModernJavaSyntax` | 现代 Java 语法支持 | `false`/`jabel`/`jvmDowngrader`/`modern` |
| `enableGenericInjection` | 注入泛型参数 | `true`/`false` |

**推荐配置**：`enableModernJavaSyntax = jabel`（现代语法编译为 J8 字节码）

### 版本管理

| 配置项 | 说明 |
|--------|------|
| `generateGradleTokenClass` | 自动生成版本常量类路径 |
| `gradleTokenVersion` | 版本字段名 |

### Mixin 配置

| 配置项 | 说明 |
|--------|------|
| `usesMixins` | 启用 Mixin 支持 |
| `separateMixinSourceSet` | 独立 Mixin 源码集（加速编译） |
| `usesMixinDebug` | Mixin 调试模式 |
| `mixinPlugin` | IMixinConfigPlugin 实现类 |
| `mixinsPackage` | Mixin 包路径 |
| `coreModClass` | CoreMod 入口类 |

### 依赖配置

| 配置项 | 说明 |
|--------|------|
| `usesShadowedDependencies` | 启用 Shadow 依赖 |
| `minimizeShadowedDependencies` | 最小化 Shadow 依赖 |
| `relocateShadowedDependencies` | 重定位 Shadow 依赖 |
| `includeWellKnownRepositories` | 自动添加知名仓库 |

### 发布配置

| 配置项 | 说明 |
|--------|------|
| `usesMavenPublishing` | Maven 发布 |
| `modrinthProjectId` | Modrinth 项目 ID |
| `curseForgeProjectId` | CurseForge 项目 ID |

---

## dependencies.gradle 依赖配置

### 依赖类型

| 类型 | 说明 | 对依赖模组可见性 |
|------|------|-----------------|
| `api` | 公开 API 依赖 | 编译✓ 运行✓ |
| `implementation` | 内部实现依赖 | 编译✗ 运行✓ |
| `compileOnly` | 编译时依赖 | 编译✗ 运行✗ |
| `runtimeOnly` | 运行时依赖 | 编译✗ 运行✓ |
| `devOnlyNonPublishable` | 仅开发时依赖 | 编译✓ 运行✓（不发布） |

### 示例

```groovy
dependencies {
    // 公开 API 依赖
    api("com.example:lib:1.0.0")
    
    // 内部实现依赖
    implementation("com.example:utils:1.0.0")
    
    // 编译时依赖（如可选模组）
    compileOnly("com.example:optional:1.0.0")
    
    // 运行时依赖
    runtimeOnly("com.example:runtime:1.0.0")
    
    // 开发时依赖（不发布）
    devOnlyNonPublishable("com.example:dev:1.0.0")
}
```

---

## repositories.gradle 仓库配置

```groovy
repositories {
    // 添加额外的 Maven 仓库
    maven { url "https://maven.example.com" }
}
```

---

## 本项目配置示例

AE2-QoL-1.7.10-GTNH 项目的 `gradle.properties` 关键配置：

```properties
modName = AE2-QoL
modId = ae2qol
modGroup = com.example.ae2qol
minecraftVersion = 1.7.10
forgeVersion = 10.13.4.1614
enableModernJavaSyntax = jabel
usesMixins = true
mixinsPackage = com.example.ae2qol.mixin
```
