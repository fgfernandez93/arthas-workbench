<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# arthas-workbench Changelog

## [Unreleased]

## [0.0.3] - 2026-03-25
### Changed
- 将插件内原本面向会话终端的 `Arthas Console` 概念统一更名为 `Arthas Terminal`
- 同步更新会话 UI、设置项、资源文案、README 与 `docs/*` 中的 `Terminal / Log` 表述
- 移除 `ArthasWorkbenchSettingsService` 与设置面板中遗留的 `autoConnectConsole` 配置字段，统一使用 `autoOpenTerminal`

### Fixed
- 调整相关测试断言，避免因为默认资源语言差异导致测试结果不稳定

## [0.0.2] - 2026-03-24
### Fixed
- 移除 `ArthasTelnetTtyConnector` 对 `Questioner` 和 `TtyConnector.init(Questioner)` 的覆写，消除 `verifyPlugin` 中的 scheduled for removal API 告警
- 显式实现 `TtyConnector.resize(TermSize)`，避免 Telnet 终端继续依赖旧的默认分支
- 明确使用 JDK 21 运行 `verifyPlugin`，规避本地异常 `JAVA_HOME` 带来的 `instrumentCode` 失败问题

## [0.0.1] - 2026-03-23
### Added
- 支持自动发现并 Attach IDEA Run/Debug 启动的 Java 进程
- 基于 Workbench + Sessions Tool Window 的双窗口 UI 结构
- Settings 页面统一管理包来源、端口、Arthas 密码、Gateway 与打开行为
- `PackageSource` 统一抽象 5 种 Arthas 包来源
- `AttachStrategy` 抽象与 `Arthas Boot` 实现
- 使用 JediTerm 直连 Arthas Terminal 的真实终端会话
- MCP Gateway 聚合多个会话并提供稳定入口
- Spotless 格式化链路，接入 `runIde` / `build` / `buildPlugin` / `test` / `publishPlugin`
