# GTNH 开发指南

> 来源: https://gtnh.huijiwiki.com/wiki/开发#ModularUI

## 项目入门

### 从现有仓库开始
1. 克隆仓库
2. 运行 `./gradlew updateBuildScript` 更新构建脚本

### 从零开始
1. 解压[项目启动器](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/starter.zip)
2. 替换 LICENSE-template 中的占位符并重命名为 LICENSE
3. 初始化 git: `git init; git commit --message "initialized repository"`
4. 编辑 `gradle.properties` 替换占位符
5. 运行 `./gradlew build`

### 构建命令
```bash
./gradlew build                    # 构建项目
./gradlew build --build-cache      # 启用缓存加速
./gradlew spotlessApply            # 修复代码格式
./gradlew updateDependencies       # 更新依赖
./gradlew build --offline          # 离线构建（加速）
```

### 常见问题
- IDE 提示缺少依赖 → 重新加载 Gradle
- 需要 64 位 Java，大多数模组适用 Java 8-19
- 使用 `enableModernJavaSyntax` 时需要 Java 11+

## 加速构建（中国区代理）

在 `C:\Users\[用户名]\.gradle\gradle.properties` 中添加：

```properties
systemProp.socks.proxyHost=127.0.0.1
systemProp.socks.proxyPort=7890

systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890

systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

**Gradle 国内镜像**（修改 `gradle-wrapper.properties`）：
- 腾讯云: `https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip`
- 阿里云: `https://mirrors.aliyun.com/gradle/gradle-8.6-bin.zip`

## 贡献流程

1. Fork 仓库 → 克隆到本地
2. 彻底测试更改
3. 推送代码到分支
4. 创建 PR 并说明更改内容
5. 等待审查和合并

**PR 审查负责人：**
- Java/Scala/Kotlin 代码：GTNewHorizons/developers team
- 任务或配方：chochom 或 DreamMasterXXL
- NEI 模组：mitchej123

**重要：** 即使获得批准，也不要自行合并 PR！

## IntelliJ IDEA 有用的插件

| 插件 | 用途 |
|------|------|
| Minecraft Development | NBT 编辑器、自动补全（使用 [eigenraven 分支](https://github.com/eigenraven/MinecraftDev)） |
| ASM Bytecode Viewer | ASM/Mixin 开发辅助 |

## 调试

### Hotswap（热重载）
使用 `1b. Run Client (Java 17, Hotswap)` 启动，然后 `Run > Debugging Actions > Reload Changed Classes`

### Eclipse 远程调试
1. 配置远程 Java 应用程序，端口 5005
2. 使用 `--debug-jvm` 参数启动游戏
3. 等待 "Listening for transport dt_socket at address: 5005"
4. 使用调试配置连接

## 依赖类型

| 类型 | 你的模组 | 依赖模组 |
|------|---------|---------|
| api | 编译✓ 运行✓ | 编译✓ 运行✓ |
| implementation | 编译✓ 运行✓ | 编译✗ 运行✓ |
| compileOnly | 编译✓ 运行✗ | 编译✗ 运行✗ |
| runtimeOnly | 编译✗ 运行✓ | 编译✗ 运行✓ |

## 常用 JVM 调试参数

```bash
-Dmixin.debug=true                    # 启用 Mixin 调试
-Dmixin.debug.verbose=true            # 详细调试消息
-Dmixin.debug.export=true             # 导出 mixin 后的类
-Dlegacy.debugClassLoading=true       # 类加载调试
-Dfml.dumpPatchedClasses=true         # 转储补丁后的类
-Dfml.dumpRegistry=true               # 转储注册表
```

## ModularUI

如果在开发 GUI 功能，可参考 [ModularUI 文档](https://www.gtnewhorizons.com/ModularUI/?version=master)（待补充）

## 参考资源

- [ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10)
- [StructureLib 文档](https://www.gtnewhorizons.com/StructureLib/?version=master)
- [代码风格规范](./GTNH-代码风格.md)
