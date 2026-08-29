# GTNH 常见问题

> 来源：ExampleMod1.7.10/docs/FAQ.md

## 常见问题解答

### Q: 选择 MCP conf 目录

运行项目时可能遇到弹窗提示选择 MCP conf 目录。

**解决方法**：
- **Windows**: `%USERPROFILE%/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf`
- **Linux/Mac**: `~/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf`

---

### Q: IDE 提示缺少依赖

即使运行了 `gradlew build`，IDE 仍提示缺少依赖。

**解决方法**：
1. 重新加载 Gradle 项目
2. IntelliJ IDEA: 右键 `build.gradle` → Reload Gradle Project
3. Eclipse: 右键项目 → Gradle → Refresh Gradle

---

### Q: 构建失败：依赖过期

**解决方法**：
```bash
./gradlew updateDependencies
```

---

### Q: 构建失败：Spotless 检查失败

**解决方法**：
```bash
./gradlew spotlessApply
```

---

### Q: 需要特定 Java 版本

大多数模组适用于 Java 8-19。如使用 `enableModernJavaSyntax = jabel`，需要 Java 11+。

**设置 JAVA_HOME**：
```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-11

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
```

---

### Q: 如何启用 Mixin 支持？

在 `gradle.properties` 中配置：

```properties
usesMixins = true
mixinsPackage = com.yourpackage.mixin
```

参考：[Mixin 配置分支](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-mixins)

---

### Q: 如何使用 Access Transformers？

1. 创建 AT 文件：`src/main/resources/META-INF/yourmodid_at.cfg`
2. 在 `gradle.properties` 中配置：
```properties
accessTransformersFile = yourmodid_at.cfg
```

---

### Q: 如何发布到 Modrinth/CurseForge？

在 `gradle.properties` 中配置：

```properties
# Modrinth
modrinthProjectId = your-project-id

# CurseForge
curseForgeProjectId = your-project-id
```

设置环境变量：
- `MODRINTH_TOKEN`：Modrinth API token
- `CURSEFORGE_TOKEN`：CurseForge API token

---

## AE2-QoL 项目特定问题

### Q: Mixin 注入失败但不崩溃？

检查 `mixin.log`：
- 注入点错误会静默失效
- 必须以 `mixin.log` 为准

### Q: 如何测试存档兼容性？

1. 新建空存档测试新功能
2. 使用旧存档测试兼容性
3. 检查 NBT 数据迁移

### Q: 如何调试 Mixin？

在 `gradle.properties` 中启用：
```properties
usesMixinDebug = true
```

JVM 参数：
```bash
-Dmixin.debug=true
-Dmixin.debug.verbose=true
-Dmixin.debug.export=true
```
