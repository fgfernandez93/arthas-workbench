<div align="center">

# Arthas Workbench

</div>

<div align="center">
  <img src="src/main/resources/icons/original/arthasWorkbenchDiagnosticWave_128x128.png" alt="Arthas Workbench 插件图标预览" width="128" height="128" />
</div>

<div align="center">

![License](https://img.shields.io/badge/license-Apache%202.0-1677ff)
![Status](https://img.shields.io/badge/status-alpha-f59e0b)
![IDEA](https://img.shields.io/badge/IDEA-2025.1%2B-0a7cff)
![JDK](https://img.shields.io/badge/JDK-21-1f883d)
![Language](https://img.shields.io/badge/language-Java-f57c00)
![Formatting](https://img.shields.io/badge/formatting-Spotless-4c1)

</div>

<div align="center">

在 IntelliJ IDEA 里集中完成 Arthas 包管理、JVM 进程发现、动态 Attach、会话终端、日志查看和 MCP Gateway 接入。

</div>

<!-- Plugin description -->
## English:

Arthas Workbench is an IntelliJ IDEA plugin for local Arthas workflows. It helps you discover JVM processes started from IDEA Run/Debug or found on the local machine, attach Arthas with managed package sources and port allocation, manage Console and Log views for each session, open the Arthas Web UI in your browser, and expose a stable MCP Gateway endpoint for AI tools or IDE assistants.

Key features:

- Discover JVM processes from both IDEA Run/Debug and the local machine.
- Attach Arthas with managed package sources, Java selection, and port allocation.
- Manage Console and Log views for multiple sessions in dedicated tool windows.
- Open the Arthas Web UI and copy a stable MCP Gateway configuration from the IDE.

Detailed operation documents: [https://github.com/wl2027/arthas-workbench](https://github.com/wl2027/arthas-workbench)

## 中文:

Arthas Workbench 是一个面向本地 Arthas 工作流的 IntelliJ IDEA 插件。它可以帮助你发现 IDEA Run/Debug 和本机上的 JVM 进程，用一致的流程完成 Arthas Attach，在独立工具窗口里管理多会话的 Console / Log，并提供稳定的 MCP Gateway 入口，方便 AI 工具或 IDE 助手接入。

核心能力：

- 同时发现 IDEA Run/Debug 与本机 JVM 进程。
- 统一管理 Arthas 包来源、Attach Java 选择和端口分配。
- 在独立工具窗口中管理多会话的 Console / Log。
- 直接打开 Arthas Web UI，并一键复制稳定的 MCP Gateway 配置。

详细操作文档: [https://github.com/wl2027/arthas-workbench](https://github.com/wl2027/arthas-workbench)


<!-- Plugin description end -->

## 项目概览

Arthas Workbench 是一个独立维护的 IntelliJ IDEA 插件开源项目，目标是把本地 JVM 调试、Arthas Attach、终端会话、日志查看和 MCP 接入收敛到一个更顺手的 IDE 工作流里。

- 仓库地址：[wl2027/arthas-workbench](https://github.com/wl2027/arthas-workbench)
- 当前版本：`0.0.1`
- 项目状态：`Alpha / 可用但持续演进`
- 目标平台：`IntelliJ IDEA Community 2025.1+`
- 开源协议：[`Apache-2.0`](LICENSE)

它不是对 Arthas 原生命令行或官方 Web UI 的替代，而是一个更贴近日常本地开发与调试场景的工作台。

## 界面总览

![Arthas Workbench 全景预览](docs/assets/whole.png)

## 为什么使用 Arthas Workbench

**常见痛点**

- 本地调试时，需要在 IDEA、终端、浏览器和配置文件之间频繁切换。
- Attach 过程中经常要手动处理包来源、Java 版本、端口和会话入口。
- 当需要让 AI 客户端接入 Arthas MCP 时，会话地址和认证信息不够稳定。

**这个项目提供的方案**

- 直接在 IDEA 中发现并优先展示 Run/Debug 启动的 JVM。
- 统一收敛 Arthas 包下载、解析、Attach 和会话管理流程。
- 把 Console、Log、Web UI 和 MCP Gateway 都放进同一个工作流。

| 能力 | 说明 |
| --- | --- |
| JVM 进程发现 | 自动扫描可 Attach 的本地 JVM，并优先展示 IDEA Run/Debug 进程 |
| Arthas 包来源管理 | 统一支持官方最新版、官方指定版本、自定义远程 Zip、本地 Zip、本地目录 |
| 一键 Attach | 自动完成包解析、HTTP/Telnet 端口分配、Agent MCP 密码处理和 Attach Java 选择 |
| 多会话管理 | 在独立 `Arthas Sessions` 窗口中切换 `Console / Log`，并避免误关运行中会话 |
| MCP Gateway | 聚合多个会话的 MCP 能力，对外暴露稳定入口 `/gateway/mcp` |

## 适用场景与边界

**适合**

- 在 IDEA 内直接对本地 Java 进程执行 Arthas Attach。
- 同时维护多个 Arthas 会话，并快速查看 Console 与 Log。
- 需要为 AI 客户端或 IDE 助手提供稳定 MCP 入口的本地开发流程。

**当前不聚焦**

- 远程服务器批量运维或集群化管理。
- 替代 Arthas 官方命令行全部能力或全部使用习惯。
- 通用型 Java 诊断平台的完整运营后台。

## 安装

### 方式一：使用 GitHub Releases

从 [Releases](https://github.com/wl2027/arthas-workbench/releases/latest) 下载最新插件包后，在 IDEA 中执行：

`Settings/Preferences` -> `Plugins` -> `⚙` -> `Install Plugin from Disk...`

### 方式二：本地构建安装

请先确保本地 `JAVA_HOME` 指向 JDK 21，然后执行：

```bash
./gradlew buildPlugin -x buildSearchableOptions -x jarSearchableOptions
```

构建产物位于：

`build/distributions/*.zip`

随后在 IDEA 中使用 `Install Plugin from Disk...` 安装对应 zip 包。

## 快速开始

### 1. 配置 Arthas 与 Gateway

先打开 `Settings -> Tools -> Arthas Workbench`，确认 Arthas 包来源、端口分配策略、Agent MCP 密码和 MCP Gateway 配置。

![Arthas Workbench 设置页](docs/assets/arthas-setting.png)

当前支持 5 种 Arthas 包来源：

- 官方最新版本
- 官方指定版本
- 自定义远程 Zip
- 本地 `arthas-bin.zip` 文件
- 本地 `arthas-bin` 目录

默认缓存目录：

`~/.arthas-workbench-plugin/packages`

### 2. 发现进程并开启 Arthas

打开右侧 `Arthas Workbench` Tool Window，选择要 Attach 的 JVM 进程，然后执行 `开启 Arthas`、`打开会话`、`打开 Web UI` 或 `复制 MCP`。

![Arthas Workbench 主界面](docs/assets/arthas-workbench.png)

插件会自动完成：

- Arthas 包解析或下载
- HTTP / Telnet 端口分配
- Agent MCP 密码生成或读取
- 尝试识别目标 JVM 的 `java.home` 并选择更匹配的 Attach Java

### 3. 在会话里执行命令并查看日志

Attach 成功后，使用左下角 `Arthas Sessions` Tool Window 进入对应会话。每个会话都有独立 tab，可在 `Console` 和 `Log` 之间切换。

![Arthas Sessions 会话窗口](docs/assets/arthas-session.png)

会话能力包括：

- 基于 `JediTerm + Telnet` 的 Arthas Console 交互体验
- 同一会话内查看 Attach 日志与运行日志
- 多 JVM 会话并存管理
- 运行中的会话禁止误关闭，停止或失败后才允许关闭

## 核心工作流

1. 在 IDEA 中运行或打开目标 Java 应用。
2. 在 `Arthas Workbench` 中选择目标 JVM。
3. 点击 `开启 Arthas` 完成 Attach。
4. 通过 `打开会话` 进入 `Arthas Sessions` 执行命令或查看日志。
5. 需要浏览器图形界面时，使用 `打开 Web UI`。
6. 需要 AI 客户端接入时，使用 `复制 MCP` 获取统一 Gateway 配置。

## MCP Gateway

插件内置一个本地 `MCP Gateway`，用于统一聚合多个 Arthas agent 的 MCP 能力，对外暴露固定入口：

`http://127.0.0.1:<gateway-port>/gateway/mcp`

推荐使用方式：

1. 在 Settings 中配置 `MCP Gateway` 的端口和认证策略。
2. 在 Workbench 中点击 `复制 MCP`。
3. 将复制出的配置粘贴到 AI 客户端或 IDE 助手中。

说明：

- `Agent MCP 密码` 只用于插件访问 agent 侧 MCP。
- `Gateway Token` 只用于外部客户端访问插件内置 Gateway。
- 当只有一个运行中的会话时，网关可自动路由；多会话并存时，调用方需要显式指定 `pid` 或 `sessionId`。

## 开发与构建

### 本地运行插件

```bash
./gradlew runIde
```

### 代码格式化

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

### 测试与打包

```bash
./gradlew test
./gradlew buildPlugin -x buildSearchableOptions -x jarSearchableOptions
```

当前 Gradle 生命周期约定如下：

- `runIde` 与所有 `runIde*` 任务会先执行 `spotlessApply`
- `build`、`test`、`buildPlugin`、`publishPlugin` 会先执行 `spotlessCheck`

## 仓库结构

- `src/main/java/com/alibaba/arthas/idea/workbench/model`：领域模型与枚举
- `src/main/java/com/alibaba/arthas/idea/workbench/service`：包管理、进程发现、Attach、会话状态、MCP Gateway 等服务
- `src/main/java/com/alibaba/arthas/idea/workbench/service/attach`：AttachStrategy 抽象与具体实现
- `src/main/java/com/alibaba/arthas/idea/workbench/ui`：Workbench、Sessions、Console 等 UI 组件
- `src/main/java/com/alibaba/arthas/idea/workbench/settings`：IDEA Settings 页面实现
- `src/main/java/com/alibaba/arthas/idea/workbench/util`：端口、文件、MCP 配置、UI 辅助工具

## 文档索引

- [架构说明](docs/ARCHITECTURE.md)
- [使用说明](docs/USAGE.md)
- [开发说明](docs/DEVELOPMENT.md)
- [排障指南](docs/TROUBLESHOOTING.md)
- [路线图](docs/ROADMAP.md)
- [发布说明](docs/RELEASE.md)
- [变更日志](CHANGELOG.md)

## 参与开源协作

欢迎通过 Issue、Discussion 和 Pull Request 一起完善这个项目。对较大的功能改动、UI 调整或架构重构，建议先开 Issue 对齐目标和边界。

开始贡献前建议先阅读：

- [贡献指南](CONTRIBUTING.md)
- [支持与反馈](SUPPORT.md)
- [行为准则](CODE_OF_CONDUCT.md)

问题反馈建议附上：

- IDEA 版本与操作系统版本
- 目标 JVM 的 Java 版本
- Arthas 包来源与 Attach 方式
- `Arthas Sessions` 中对应会话的 `Log`
- 可复现步骤与预期结果

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

---

Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
