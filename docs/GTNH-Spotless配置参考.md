# GTNH Spotless 配置参考

> 来源：ExampleMod1.7.10/gtnhShared/

## 概述

Spotless 是 GTNH 使用的代码格式化工具，确保代码风格统一。

## Spotless 配置文件

### spotless.gradle

```groovy
apply plugin: 'com.diffplug.spotless'

spotless {
    encoding 'UTF-8'

    // .gitignore 等杂项文件
    format 'misc', {
        target '.gitignore'
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
    
    // Java 格式化
    java {
        target 'src/*/java/**/*.java', 'src/*/scala/**/*.java'
        toggleOffOn()
        importOrderFile(Blowdryer.file('spotless.importorder'))
        removeUnusedImports()
        eclipse('4.19').configFile(Blowdryer.file('spotless.eclipseformat.xml'))
    }
    
    // Kotlin 格式化
    kotlin {
        target 'src/*/kotlin/**/*.kt', 'src/*/java/**/*.kt'
        toggleOffOn()
        trimTrailingWhitespace()
        endWithNewline()
        ktlint('1.7.1').editorConfigOverride([
            'ktlint_code_style': 'intellij_idea'
        ])
    }
    
    // Scala 格式化
    scala {
        target 'src/*/scala/**/*.scala'
        scalafmt('3.7.15')
    }
    
    // JSON 格式化
    json {
        ratchetFrom 'origin/master'
        target(
            'src/**/mcmod.info',
            'src/**/*.json',
            'src/**/*.mcmeta'
        )
        prettier().config([
            parser: 'json',
            printWidth: 100,
            tabWidth: 2,
            objectWrap: "collapse",
            useTabs: false,
            endOfLine: 'lf'
        ])
        endWithNewline()
    }
}
```

### spotless.importorder

```
# Import 排序规则
0=java
1=javax
2=net
3=org
4=com
```

### spotless.eclipseformat.xml

Eclipse Java 格式化规则（约400行），定义：
- 缩进规则（4空格）
- 换行规则
- 空格规则
- 括号规则

---

## 使用方法

### 手动格式化

```bash
./gradlew spotlessApply
```

### 检查格式化（CI 使用）

```bash
./gradlew spotlessCheck
```

### IDE 中切换

在代码中添加注释切换格式化：
```java
// spotless:off
// 这部分代码不格式化
// spotless:on
```

---

## 本项目配置

AE2-QoL-1.7.10-GTNH 项目已配置 Spotless：

1. **Java 格式化**：使用 Eclipse 格式化规则
2. **Import 排序**：java → javax → net → org → com
3. **自动移除未使用导入**
4. **JSON 格式化**：mcmod.info 等文件

### 格式化检查流程

1. 修改代码后运行 `./gradlew spotlessApply`
2. 提交前确认 `./gradlew spotlessCheck` 通过
3. 如需跳过格式化，在 commit message 中添加 `[skip spotless]`

---

## 常见问题

### Q: 为什么我的代码格式化后变了？

A: Spotless 使用 Eclipse 格式化规则，与 IDE 默认格式化可能不同。这是正常的，统一格式化确保代码风格一致。

### Q: 如何禁用 Spotless？

A: 在 `gradle.properties` 中添加：
```properties
disableSpotless = true
```
**不推荐**：仅在与上游同步时使用。

### Q: 新增的文件没有被格式化？

A: Spotless 默认只格式式化已跟踪的文件。确保文件已添加到 Git：
```bash
git add <file>
```
