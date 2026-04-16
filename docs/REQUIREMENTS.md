# Arthas Workbench 需求文档

## 1. 文档目的

本文档用于沉淀 Arthas Workbench 的产品需求、交互边界与实现约束，便于后续开源协作、需求评估、版本规划与 PR 对齐。

它关注的是：

- 这个插件为什么存在
- 它解决什么问题
- 当前必须支持哪些核心能力
- UI 和架构有哪些明确边界
- 什么能力当前不做，或者暂时不优先做

## 2. 项目背景

Arthas 在本地 Java 调试与排障中非常常用，但 IDEA 用户在日常使用时通常会遇到几个痛点：

- 需要在 IDEA、终端、浏览器之间频繁切换
- Attach 前还要手工准备 ,不具备安装管理等功能
- 多个 JVM 会话并存时，Terminal、Log、Web UI 与端口信息容易分散
- 面向 AI / IDE 助手接入时，单个会话的 MCP 地址不稳定，不利于长期配置

Arthas Workbench 的目标，就是把这条本地工作链路收进 IntelliJ IDEA，形成更顺手的本地调试工作台。

## 3. 目标用户

主要用户：

- 使用 IntelliJ IDEA 进行本地 Java 开发的工程师
- 需要频繁使用 Arthas 做排障、定位、观测的开发者
- 需要把 Arthas 能力通过 MCP 暴露给 AI 客户端或 IDE 内部助手的用户

典型使用场景：

- 本地运行一个 demo、服务或测试程序后快速 attach
- 在 Run/Debug 场景下直接对当前 IDEA 进程开启 Arthas
- 同时维护多个本地 JVM 的 Arthas 会话
- 把统一 MCP Gateway 配置给 AI 助手执行诊断工具

## 4. 产品目标

Arthas Workbench 当前阶段聚焦以下目标：

1. 在 IDEA 内快速发现并选择目标 Java 进程
2. 提供稳定的一键 Attach 工作流
3. 把 Arthas 包来源抽象成统一配置，而不是依赖手工管理
4. 在 IDE 内提供接近原生终端体验的 Arthas Terminal
5. 用清晰的 UI 分层分别承载设置、Attach 管理、会话与日志
6. 提供统一稳定的 MCP Gateway，而不是暴露易变的单会话 MCP 地址

## 5. 非目标

当前阶段明确不作为主要目标的能力：

- 替代 Arthas 官方命令行
- 重做或取代官方 Web UI
- 管理远程主机上的 Arthas 会话
- 提供分布式运维平台式的统一资产管理
- 把所有 Arthas 认证能力包装成完整的统一安全模型
- 持久化恢复 IDE 重启前的所有历史会话

## 6. 核心需求概览

### 6.1 进程发现与选择

插件必须能够：

- 自动扫描当前机器上可 Attach 的本地 JVM 进程
- 区分 IDEA Run/Debug 启动的进程与普通本地进程
- 在 UI 上按 `IDEA` 与 `本地` 两个分页展示进程
- 提供手动刷新能力
- 监听 IDEA 运行状态变化，自动同步刷新列表

### 6.2 Arthas 包来源管理

插件必须支持统一配置 Arthas 包来源。

当前支持 5 种来源：

- 官方最新版本
- 官方指定版本
- 自定义远程 Zip
- 本地 `arthas-bin.zip` 文件
- 本地 `arthas-bin` 目录

对应要求：

- UI 必须根据来源类型动态切换输入方式
- 官方最新版本固定显示下载链接且不可编辑
- 官方最新版本必须支持手动“更新官方最新版包”
- 指定版本与自定义远程 Zip 支持文本输入
- 本地 Zip 与本地目录支持点击后直接弹选择器
- 包缓存必须统一保存在 `~/.arthas-workbench-plugin/packages`

### 6.3 一键 Attach

插件必须支持一键 Attach，并默认使用稳定优先的 `Arthas Boot` 工作流。

Attach 前必须自动处理：

- Arthas 包准备或下载
- HTTP / Telnet 端口分配
- Agent MCP 密码生成或读取
- 目标 JVM 的 `java.home` 识别
- 更匹配的 Attach Java 选择

当前只保留 `Arthas Boot` 作为正式支持路径，不再暴露多 attach 策略选择。

### 6.4 会话管理

插件必须以“一个 JVM 对应一个会话”的方式管理运行期状态。

需要支持：

- 会话状态：`ATTACHING`、`RUNNING`、`FAILED`、`STOPPED`
- 会话日志累积
- 会话窗口打开状态跟踪
- 当前会话视图类型跟踪
- 通过 PID 找到最新会话

### 6.5 Terminal 与 Log

插件必须提供独立的会话窗口，并在其中管理 Terminal 与 Log。

要求：

- `Arthas Sessions` 作为独立 Tool Window 存在
- 每个会话对应一个 tab
- tab 内切换 `Terminal / Log`
- Terminal 使用真正的终端链路，而不是只读文本区
- Log 用于展示 Attach 日志和运行日志
- 运行中的会话 tab 不允许误关闭
- 停止或失败后的会话 tab 允许关闭

### 6.6 Web UI

插件必须支持打开 Arthas Web UI，但不再要求内嵌渲染。

当前方案：

- 在默认浏览器打开 Web UI
- Workbench 和右键菜单均可提供入口

### 6.7 MCP Gateway

插件必须提供统一稳定的 Gateway MCP 服务。

要求：

- 对外暴露固定的 `/gateway/mcp` 入口
- 聚合多个会话的 MCP 能力
- 提供 `gateway_sessions`
- 支持按 `pid` 或 `sessionId` 路由
- 支持复制固定 Gateway MCP 配置
- 外部客户端优先使用 Gateway，而不是直接保存单会话 MCP 地址

### 6.8 认证模型

当前阶段认证模型拆成两套独立配置：

- `Agent MCP 密码`
  只用于插件访问 agent 侧 MCP
- `MCP Gateway 认证`
  只用于插件内置 Gateway 的固定 `/gateway/mcp` 入口

两者都支持：

- 随机生成
- 设置密码
- 关闭认证

边界说明：

- `Agent MCP 密码` 不是 IDEA Terminal 的连接密码
- `Agent MCP 密码` 也不是浏览器 Web UI 的统一密码
- 当前不实现更大范围的“Arthas agent 统一密码管理”

## 7. UI 设计要求

### 7.1 Settings

Settings 页只负责全局配置，不承担运行期操作。

应包含以下模块：

- Arthas 包
- Attach 与端口
- Agent MCP 密码
- MCP Gateway
- 打开行为

设计要求：

- 文案要偏产品语言，避免暴露内部实现术语
- 说明文字简洁，避免大段冗长描述
- 跟具体包来源绑定的动作，尽量放在包设置模块内解决

### 7.2 Arthas Workbench

Workbench 只负责进程选择和当前进程的高频操作。

要求：

- 以 Java 进程列表为主视图
- 有明显的当前选择信息区域
- 底部保留高频核心动作
- 右键菜单和“其他操作”只保留当前进程相关动作
- 全局配置入口放到 Settings，而不是继续堆叠在进程菜单里

### 7.3 Arthas Sessions

Sessions 负责运行中的会话内容，而不是全局配置。

要求：

- 位于左下方
- 支持多 tab
- 支持在同一 tab 内切换 `Terminal / Log`
- 颜色与 IDEA 主题保持一致，不再提供单独配色设置

## 8. 文档需求

项目必须保持完整的文档体系，至少包括：

- `README.md`
- `CHANGELOG.md`
- `docs/ARCHITECTURE.md`
- `docs/USAGE.md`
- `docs/TROUBLESHOOTING.md`
- `docs/DEVELOPMENT.md`
- `docs/ROADMAP.md`

文档要求：

- README 负责总览
- ARCHITECTURE 负责系统分层与关键抽象
- USAGE 面向终端用户
- TROUBLESHOOTING 面向常见故障
- DEVELOPMENT 面向贡献者
- CHANGELOG 面向版本结果记录

## 9. 测试与质量要求

每次较大功能变更后，至少应覆盖：

- 设置页状态读写
- 包来源切换交互
- Agent MCP 与 Gateway 认证模式切换
- MCP 配置生成
- 插件构建链路可通过

当前验证基线：

- `spotlessApply`
- `test`
- `buildPlugin -x buildSearchableOptions -x jarSearchableOptions`

## 10. 后续演进方向

当前需求已经基本覆盖本地开发工作台的核心路径，后续演进方向可包括：

- 更完整的 Attach 失败诊断
- 更清晰的会话历史与恢复策略
- 更完善的 MCP 客户端接入模板
- 更系统的 UI 自动化测试
- 更完整的 Marketplace 发布与签名流程

## 11. 需求结论

Arthas Workbench 的当前定位应保持稳定：

- 它是“本地 Arthas IDE 工作台”
- 不是“远程运维平台”
- 不是“官方 Web UI 替代品”
- 也不是“Arthas 核心能力的重新包装”

所有后续功能迭代，都应优先服务于这条主线：

**让 IDEA 内的本地 Arthas 使用路径更顺手、更集中、更稳定。**
