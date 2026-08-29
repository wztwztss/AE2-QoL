# GTNH 构建与代码参考

> 来源：ExampleMod1.7.10

---

## 一、gradle.properties 核心配置

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

**推荐**：`enableModernJavaSyntax = jabel`（现代语法编译为 J8 字节码）

### Mixin 配置

| 配置项 | 说明 |
|--------|------|
| `usesMixins` | 启用 Mixin 支持 |
| `separateMixinSourceSet` | 独立 Mixin 源码集（加速编译） |
| `usesMixinDebug` | Mixin 调试模式 |
| `mixinPlugin` | IMixinConfigPlugin 实现类 |
| `mixinsPackage` | Mixin 包路径 |
| `coreModClass` | CoreMod 入口类 |

### 依赖/发布配置

| 配置项 | 说明 |
|--------|------|
| `usesShadowedDependencies` | 启用 Shadow 依赖 |
| `usesMavenPublishing` | Maven 发布 |
| `modrinthProjectId` | Modrinth 项目 ID |
| `curseForgeProjectId` | CurseForge 项目 ID |

---

## 二、dependencies.gradle 依赖类型

| 类型 | 说明 | 对依赖模组可见性 |
|------|------|-----------------|
| `api` | 公开 API 依赖 | 编译✓ 运行✓ |
| `implementation` | 内部实现依赖 | 编译✗ 运行✓ |
| `compileOnly` | 编译时依赖 | 编译✗ 运行✗ |
| `runtimeOnly` | 运行时依赖 | 编译✗ 运行✓ |
| `devOnlyNonPublishable` | 仅开发时依赖 | 编译✓ 运行✓（不发布） |

---

## 三、Spotless 代码格式化

### 使用方法

```bash
# 手动格式化
./gradlew spotlessApply

# 检查格式化（CI 使用）
./gradlew spotlessCheck
```

### 代码中切换

```java
// spotless:off
// 这部分代码不格式化
// spotless:on
```

### Import 排序

```
0=java  1=javax  2=net  3=org  4=com
```

### 常见问题

- **Q: 格式化后代码变了？** — Spotless 使用 Eclipse 格式化规则，与 IDE 默认可能不同，属正常现象
- **Q: 如何禁用？** — `gradle.properties` 中添加 `disableSpotless = true`（不推荐）
- **Q: 新文件没格式化？** — 需先 `git add`，Spotless 只处理已跟踪文件

---

## 四、模板代码参考

### MyMod.java - 入口类

```java
@Mod(modid = MyMod.MODID, version = Tags.VERSION, name = "MyMod", acceptedMinecraftVersions = "[1.7.10]")
public class MyMod {
    public static final String MODID = "mymodid";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.myname.mymodid.ClientProxy", serverSide = "com.myname.mymodid.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) { proxy.preInit(event); }
    @Mod.EventHandler public void init(FMLInitializationEvent event) { proxy.init(event); }
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event) { proxy.postInit(event); }
    @Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) { proxy.serverStarting(event); }
}
```

### CommonProxy.java - 通用代理

```java
public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
    }
    public void init(FMLInitializationEvent event) {}
    public void postInit(FMLPostInitializationEvent event) {}
    public void serverStarting(FMLServerStartingEvent event) {}
}
```

### Config.java - 配置类

```java
public class Config {
    public static String greeting = "Hello World";
    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);
        greeting = configuration.getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "How shall I greet?");
        if (configuration.hasChanged()) { configuration.save(); }
    }
}
```

### mcmod.info 模板

```json
{
  "modid": "${modId}",
  "name": "${modName}",
  "description": "Example mod template.",
  "version": "${modVersion}",
  "mcversion": "${minecraftVersion}",
  "url": "",
  "authorList": ["Author1"],
  "dependencies": []
}
```

---

## 五、本项目配置

AE2-QoL-1.7.10-GTNH 项目 `gradle.properties` 关键配置：

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
