# Arthas Workbench 路线图

## 当前已完成

- Settings 页面与运行期 UI 解耦
- Workbench 负责进程发现、Attach 和统一操作
- Sessions Tool Window 负责多会话 tab 管理
- Console / Log 合并到同一会话页签内部切换
- `PackageSource` 统一抽象 5 种 Arthas 包来源
- `AttachStrategy` 当前默认收敛到 `Arthas Boot`
- MCP Gateway 聚合多个会话并提供稳定入口
- Console 已切换为真实终端交互，而不是只读文本输出
- Web UI 已从插件内嵌改为默认浏览器打开
- Gateway 认证模式、Agent MCP 密码和自动打开行为已支持配置化

## 近期方向

### 1. Attach 稳定性与诊断

- 补充更多平台差异提示
- 提升 Java 8 / Java 21 混合环境下的失败诊断
- 持续打磨 `Arthas Boot` 路径下的诊断质量和错误提示

### 2. 文档与开源工程化

- 持续完善 README、架构文档和开发文档
- 补齐开源项目发布所需的元数据与说明
- 为后续 PR 和社区协作建立稳定的开发约定

### 3. UI 与交互细化

- 继续优化进程列表的大量数据展示体验
- 继续打磨状态提示、日志可读性和上下文操作
- 评估会话历史、过滤与搜索能力

### 4. MCP 生态增强

- 输出面向不同 AI 客户端的 MCP 配置模板
- 进一步完善 Gateway 入口说明和调试体验
- 评估更细粒度的会话筛选和路由能力

## 中期方向

- 会话持久化与 IDE 重启后的恢复策略
- 更完整的 UI 自动化测试
- 更标准的签名、发布和 Marketplace 流程
- 更清晰的插件 ID、包命名空间与仓库元数据收敛策略
