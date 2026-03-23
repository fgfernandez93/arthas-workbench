# Arthas Workbench 开发说明

## 环境要求

- JDK：`21`
- IDE：`IntelliJ IDEA Community 2025.1+`
- 构建工具：`Gradle 9.4.1`

## 常用命令

### 格式化

```bash
cd idea-plugin/arthas-workbench
./gradlew spotlessApply
./gradlew spotlessCheck
```

当前 Spotless 负责：

- Java 源码格式化
- Gradle Kotlin DSL 脚本格式化
- Markdown / Properties 等基础文本文件的尾空格与换行统一

### 本地启动插件沙箱

```bash
cd idea-plugin/arthas-workbench
./gradlew runIde
```

### 测试

```bash
cd idea-plugin/arthas-workbench
./gradlew test
```

### 打包

```bash
cd idea-plugin/arthas-workbench
./gradlew buildPlugin -x buildSearchableOptions -x jarSearchableOptions
```

## Gradle 生命周期约定

为了把格式化真正接入开发流程，当前任务链已经做了下面的约定：

- `runIde` 与所有 `runIde*` 任务：
  先执行 `spotlessApply`
- `build`、`test`、`buildPlugin`、`publishPlugin`：
  先执行 `spotlessCheck`

这意味着通过 IDEA 的 Gradle 面板执行这些任务时，格式化会自动参与运行和构建流程。

需要注意：

- IDEA 原生的 `Build Project` 菜单不一定经过 Gradle。
- 如果希望 IDE 内“运行 / 构建”尽量与 Gradle 完全一致，建议把 IDEA 的 Build/Run 委托给 Gradle。

## 目录结构

### 主代码

- `src/main/java/com/alibaba/arthas/idea/workbench/model`
- `src/main/java/com/alibaba/arthas/idea/workbench/service`
- `src/main/java/com/alibaba/arthas/idea/workbench/service/attach`
- `src/main/java/com/alibaba/arthas/idea/workbench/settings`
- `src/main/java/com/alibaba/arthas/idea/workbench/ui`
- `src/main/java/com/alibaba/arthas/idea/workbench/util`

### 资源

- `src/main/resources/META-INF`
- `src/main/resources/messages`
- `src/main/resources/icons`

### 测试

- `src/test/java/com/alibaba/arthas/idea/workbench/...`

## 当前开发约定

- 所有主代码与测试使用 Java 实现。
- 所有新增注释统一使用中文。
- 所有用户可见文本优先放到 `messages/MyBundle*.properties` 中统一管理。
- UI 结构遵循“Settings / Workbench / Sessions”三层分工，不再回到把多个职责塞进一个面板的模式。

## 产物与缓存

### 插件产物

默认输出：

`build/distributions/*.zip`

### Arthas 包缓存

默认缓存目录：

`~/.arthas-workbench-plugin/packages`

## 推荐提交流程

```bash
cd idea-plugin/arthas-workbench
./gradlew spotlessApply
./gradlew test
./gradlew buildPlugin -x buildSearchableOptions -x jarSearchableOptions
```

如果这 3 步都通过，再准备提交 PR。

## 开源协作建议

- 提交较大功能前，建议先开 issue 说明目标、UI 变化和兼容性影响。
- 提交 PR 前，请确保 README、CHANGELOG 与相关文档同步更新。
- 如果改动涉及 Attach、端口、MCP 或终端交互，请补充相应测试或排障说明。
