# 支持与反馈
如果你在使用 Arthas Workbench 时遇到问题，建议按下面的顺序处理，这样能更快定位问题，也更方便维护者复现。

## 先看文档

优先查看这些文档：

- `README.md`
- `docs/DEVELOPMENT.md`
- `docs/TROUBLESHOOTING.md`
- `docs/ARCHITECTURE.md`

## 提交 Issue 时建议附带的信息

为了提高定位效率，建议至少提供：

- [x] IDEA 版本
- [x] 操作系统版本
- [x] 目标 JVM 的 Java 版本
- [x] Arthas 包来源和 AttachStrategy
- [x] 是否为 IDEA Run/Debug 启动的进程
- [x] Workbench 中的状态提示
- [x] 对应会话的 Log 内容
- [x] 是否能复现，以及复现步骤

## 适合开哪类 Issue

- Bug：功能异常、Attach 失败、界面状态错误、端口或 MCP 行为异常
- Feature：新能力、交互优化、架构调整建议
- Question：使用方式、配置理解、环境排查

## 快速排查建议

- Attach 失败时，先看 `Arthas Sessions` 中该会话的 `Log`
- 如果是 Java 8 目标进程，请重点确认 Attach 使用的 Java 和目标 JVM 是否兼容
- 如果 Console 无法连接，请确认 Telnet 端口是否已被成功拉起
- 如果 MCP 不可用，请检查 Gateway 端口、Token 和对应会话状态

如果问题和敏感信息相关，请在公开内容中先做脱敏处理。
