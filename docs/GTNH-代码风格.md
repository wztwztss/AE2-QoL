# GTNH Java 代码风格规范

> 来源: https://gtnh.huijiwiki.com/wiki/代码风格
> 参考: [Code Style](https://gtnh.miraheze.org/wiki/Code_Style)

## 核心原则

- 代码被阅读的次数远多于被编写的次数
- 一致的代码风格能让多人开发更轻松
- **可读性优先于指南**，但如果"更可读"的变体存在隐患，可读性可以被牺牲

## 文件组织

- 超过 2000 行的文件应尽量避免
- 类声明顺序：
  1. 类/接口文档注释（可选）
  2. `class` 或 `interface` 语句
  3. 类/接口实现注释（可选）
  4. 类 `static` 变量（public → protected → package → private）
  5. 实例变量（public → protected → package → private）
  6. 构造方法
  7. 方法（按功能分组，非作用域分组）

## 注释规范

- 代码不应该被注释掉——直接删掉它，Git 历史记录里会有
- 避免随代码演变容易过时的注释
- 注释不应被包裹在大量星号框中

**单行注释：**
```java
if (foo > 1) {
    // Do a double-flip.
    return bar.performDoubleFlip();
}
```

**多行注释：**
```java
codeGoesHere();

/*
 * Here is a block comment.
 */
moreCode();
```

**@author 标签：** 不强制要求也不禁止

## 变量声明

- 一行只声明一个变量（紧密关联的除外，如3D坐标 `int x, y, z;`）
- 不要在同一行放置不同类型的变量
- 在声明局部变量时就进行初始化
- 只在块的开头放置声明

```java
void myMethod() {
    int int1 = 0;      // beginning of method block

    if (condition) {
        int int2 = 0;  // beginning of "if" block
        ...
    }
}
```

## switch 语句（现代语法）

```java
switch (condition) {
    case ABC, DEF, KLM -> {
        statements;
    }
    case XYZ -> {
        statements;
    }
    default -> {
        statements;
    }
}
```

## 命名约定

| 标识符类型 | 命名规则 | 示例 |
|-----------|---------|------|
| 类 | 名词，混合大小写，首字母大写，避免缩写 | `class Raster`, `class ImageSprite` |
| 接口 | 与类相同，**不使用 "I" 前缀** | `interface Storage` (非 IStorage) |
| 方法 | 动词，混合大小写，首字母小写 | `run()`, `getBackgroundColor()` |
| 变量 | 混合大小写，首字母小写，以字母开头 | `String currentAccountKey` |

## 编程实践

### 访问修饰符
- 不要在没有充分理由的情况下将任何实例变量或类变量设为 `public`
- 通常通过方法调用访问，而非直接访问

### 常量
```java
// AVOID
methodName(16281);

// OK
int CONSTANT_NAME = 16281;
methodName(CONSTANT_NAME);
```

### 变量赋值
避免在单个语句中给多个变量赋相同的值：
```java
// AVOID
fooBar.fChar = barFoo.lchar = 'c';
```

### 废弃（Deprecation）
使用 @Deprecated 注解时，必须提供注释说明替代方案：
```java
/**
 * @deprecated use {@link DBHelper#update(java.lang.String, java.util.Map)}
 */
@Deprecated(forRemoval = true)
public int insert(String request, Map<String, ?> params) {
    // <existing code>
}
```

### 括号
混合运算符表达式中大量使用括号：
```java
// OK
if ((a == b) && (c == d))

// AVOID
if (a == b && c == d)
```

## 日志格式

大多数日志消息每行应使用一次写入调用。单句消息不需要大写首字母。如果有多句话，则首字母大写。
