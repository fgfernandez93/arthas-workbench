# Arthas Workbench 常见问题排查

## 1. Attach 失败，日志里出现 `Premature EOF`

优先检查：

- 目标 JVM 的 Java 版本
- Attach 使用的 Java 是否和目标 JVM 更匹配

常见原因：

- macOS 下 Java 8 目标进程动态 attach 兼容性较差
- Attach 侧 Java 与目标 JVM 差异太大
- 目标进程本身在 attach 期间退出

建议动作：

- 如果仍失败，确认目标 JVM 的 `java.home`
- 避免让 Attach Java 和目标 JVM 相差过大

## 2. 已经 Attach 成功，但 Console 打不开

优先检查：

- 会话状态是否为 `RUNNING`
- Telnet 端口是否已经拉起
- 当前机器是否存在端口冲突

建议动作：

- 在 `Arthas Sessions` 中先切到 `Log` 看 attach 输出
- 确认端口分配策略不是严格固定且端口已被占用
- 关闭会话后重新 `开启 Arthas`

## 3. Web UI 打不开

当前设计不是内嵌 Web UI，而是通过默认浏览器打开。

请检查：

- 当前会话是否已处于 `RUNNING`
- HTTP 端口是否已经启动
- 当前系统是否允许 IDEA 调用默认浏览器

## 4. MCP 复制出来的配置不可用

优先确认：

- MCP Gateway 是否成功启动
- Gateway 认证模式与 Token 是否和复制出来的配置一致
- 当前是否至少存在一个运行中的 Arthas 会话
- 如果存在多个运行中的会话，调用时是否显式传入了 `pid` 或 `sessionId`

建议动作：

- 先在 Settings 中检查 Gateway 端口、认证模式和 Token
- 确认外部 MCP 客户端使用的是固定统一入口 `http://127.0.0.1:<gateway-port>/gateway/mcp`
- 先调用 `gateway_sessions` 获取当前运行中的会话列表，再把返回的 `pid` 或 `sessionId` 带入后续工具调用
- 如果 `initialize` 和 `gateway_sessions` 正常，但 `jvm` / `memory` / `dashboard` 仍返回 `invalid_json`，通常说明当前运行中的 IDEA 插件实例还停留在旧版本，需要重启插件开发实例或重新加载插件

## 5. Arthas 密码到底作用在哪

Settings 里的 `Agent MCP 密码` 不是 IDEA Console 的连接密码，而是 agent 侧 MCP 使用的密码。

请按下面理解：

- 插件会用它访问 agent 侧 MCP
- 如果你关闭它，IDEA 内 Console / Telnet 仍可照常连接
- 它和 `MCP Gateway` 的认证是两套独立配置，互不替代

## 6. 找不到目标 Java 进程

请检查：

- 目标进程是否仍在运行
- 当前 JVM 是否允许被 attach
- 是否需要点击 `刷新进程`

说明：

- IDEA Run/Debug 启动的 Java 进程会被优先识别和展示
- 非 IDEA 启动的本地 JVM 也支持 attach

## 7. 本地 Arthas 包无法识别

当前支持：

- 官方最新版本
- 官方指定版本
- 自定义远程 Zip
- 本地 `arthas-bin.zip`
- 本地 `arthas-bin` 目录

请确保：

- Zip 中包含 `arthas-boot.jar`
- 本地目录内存在完整 Arthas Home

## 8. 当前 UI 信息太多，不知道先看哪里

建议按这个顺序看：

1. 右侧 `Arthas Workbench` 选中目标进程
2. 查看底部状态栏是否显示已 Attach 或失败
3. 如果失败，打开左下 `Arthas Sessions` 的对应会话 `Log`
4. 如果成功但不能操作，再切到 `Console`
