# AE2‑QoL‑1.7.10‑GTNH 精简开发约束（System Prompt版）

> 
> 用途：替代完整 RepositoryGuidelines.md，大幅降低token占用；保留全部硬性强制规则，删减解释性长文本，推理思考全程中文。
> 目标环境：GTNH 2.9.0‑beta‑1，Minecraft 1.7.10。

---

## 🧠思考与方案约束

1. 思考过程全部中文，仅代码、注解、专业术语可用英文；**修改方案必须先确认，禁止直接提交改动**。
2. 拿不准AE2/GTNH/Mixin行为直接提问，不要猜测；多实现路径要全部列出并对比利弊；信息不足立刻停止等待确认。**提问必须使用question工具**。
3. 权衡优先级：**稳定性 > 兼容性 > 可回退 > 开发速度**。简单任务可自行判断。
4. 参考源码只读外部目录 `E:\wzt\MC\modcreater\reference_src`，禁止直接复制源码到项目。
5. 拒绝过度封装、多余功能、不必要配置；允许一次性专用补丁代码。

## 🔧构建环境

1. **编译用Java**：`E:\java17`（Java 17），通过 `$env:JAVA_HOME = "E:\java17"` 设置。
2. **构建命令**：`$env:JAVA_HOME = "E:\java17"; .\gradlew.bat build -x spotlessJavaCheck -x spotlessCheck`
3. **Java 25不兼容**：当前系统默认Java为25.0.1，会导致构建失败，必须显式指定Java 17。
4. **可用Java版本**：`E:\java17`、`E:\java21`、`E:\java25`，其中仅17验证可用。

## ✏️编码修改规则

1. **只修改本次需求相关代码**，禁止顺便优化/重构无关遗留代码；跟随原有代码风格，不做全局格式化美化。
2. 仅删除本次改动产生的无用import、变量、方法；原有死代码仅标记提醒，不得擅自删除。
3. 旧注释原样保留，新增注释优先中文。

## 📂文件与权限

1. 修改仅限项目根目录：`E:\wzt\MC\modcreater\AE2‑QoL‑1.7.10‑GTNH`。
2. 项目外仅允许读取；新增/修改/删除外部文件必须申请许可；向测试mods目录部署jar属于允许操作。

## 🔬Mixin专项强制

1. 核对目标类、方法、SRG签名，适配GTNH 2.9.0‑beta‑1；AE2类已被GTNH修改，不能照搬原版AE2 Mixin。
2. 尽量少用`@Overwrite`；必须使用时记录风险到`docs/mixin_notes.md`。
3. 修改后必须查看`mixin.log`，无报错、无警告；Mixin目标写错会静默失效，不以游戏是否崩溃作为唯一判断。

## 🧪验证流程（硬性）

1. Gradle构建，编译无报错；使用`build/libs`产出jar，**删除测试mods目录旧jar，新旧不能共存**。
2. 两层测试：新建空存档 + 旧存档，验证功能与存档兼容性。
3. IDE Run仅用于调试，最终验证必须使用打包后的jar。
4. 崩溃务必保留：`fml‑client‑latest.log`、`mixin.log`、crash报告。

## 📝文档更新

> 
> 代码完成文档必须同步更新，否则任务未完成

1. 新增/改动Java/Mixin类：更新`docs/MOD_MAP.md`。
2. 修改Mixin注入点：更新`docs/mixin_notes.md`。
3. 行为/配方变更：更新对应文档；对外可见变更写入`CHANGELOG.md`。
4. 移植迁移改动更新：`docs/GTNH-迁移移植指南.md`。
5. 文档记录**改动原因Why**，不要复述代码；路径使用项目根目录相对路径。

## 📌Git & 版本迭代

1. 一个功能/bug修复一个commit；代码、文档、版本号放同一个commit，禁止拆分。
2. 禁止提交构建产物（`build/` `.gradle`等）；优先特性分支，不直接push main。
3. 存档破坏性改动：必须NBT迁移，同时在`CHANGELOG.md`、`docs/GTNH-迁移移植指南.md`标记提示。
4. 发布迭代四件事同一commit：
   - 更新`gradle.properties`与mods.toml版本号（语义化版本）
   - 更新`CHANGELOG.md`
   - 更新`README.md`，关键变更同步`README.en.md`

## 📤任务输出模板（任务结束固定输出）

```
版本：GTNH 2.9.0‑beta‑1 | MC1.7.10
时间戳：yyyy‑MM‑dd
修改文件：[文件列表]
Git 回退方案：git revert <commitHash> / git checkout <commitHash> -- 文件路径
测试操作：构建jar部署到测试mods目录
验证结果：编译通过，游戏运行，mixins日志无报错
版本迭代：旧版本 → 新版本
问题出现的原因：XXX
解决的方法：XXX
需要测试的内容：XXX
接下来的任务：XXX
```

## ✅编码前快速检查要点

- 确认目标版本 GTNH 2.9.0‑beta‑1 / MC1.7.10
- 查阅`docs/MOD_MAP.md`定位类，输出修改方案等待确认
- Mixin核对SRG签名
- 不注释代码，无用代码直接删除
提交前：编译通过、替换测试jar、新旧存档测试、mixin.log无告警、文档更新完毕、版本号按需迭代。

---
