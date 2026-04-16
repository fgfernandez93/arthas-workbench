# 贡献指南
感谢你关注 Arthas Workbench。

这个项目目前定位为独立维护的 IntelliJ IDEA 插件开源项目，欢迎提交 issue、文档修正、测试补充、Bug 修复和功能改进。

## 开始之前

提交较大的改动前，建议先开 issue 对齐以下内容：

- 目标问题是什么
- 预期交互或行为是什么
- 是否会影响现有 Settings、Workbench 或 Sessions 的职责边界
- 是否涉及 Attach、MCP、端口分配或终端交互

## 本地开发环境

- JDK：`21`
- IDE：`IntelliJ IDEA Community 2025.1+`
- 构建工具：`Gradle 9.4.1`

## 常用命令

```bash
cd idea-plugin/arthas-workbench
./gradlew spotlessApply
./gradlew test
./gradlew buildPlugin -x buildSearchableOptions -x jarSearchableOptions
./gradlew runIde
```

## 代码约定

- 主代码与测试统一使用 Java。
- 新增代码注释统一使用中文。
- 用户可见文本优先放入 `messages/MyBundle*.properties`。
- UI 结构遵循 `Settings / Arthas Workbench / Arthas Sessions` 三层职责分工。

## 提交前检查

提交 PR 前，请至少完成以下检查：

- 运行 `./gradlew spotlessApply`
- 运行 `./gradlew test`
- 如涉及插件行为、资源或元数据变更，运行 `./gradlew buildPlugin -x buildSearchableOptions -x jarSearchableOptions`
- 更新 README、CHANGELOG 或相关文档
- 如涉及 Attach、MCP、端口或 Terminal 行为，补充相应测试或排障说明

## PR 建议

- 标题尽量直接说明改动目标
- 描述里说明为什么改、怎么改、怎么验证
- UI 变更建议附截图或录屏
- 涉及交互变化时，说明是否影响现有用户路径

## 文档同步

如果你的改动影响以下任一方面，请同步更新文档：

- 项目定位或功能边界
- Settings 字段或默认行为
- Attach 策略或包来源
- MCP Gateway 使用方式
- 常见故障排查路径

推荐优先检查这些文件：

- `README.md`
- `CHANGELOG.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT.md`
- `docs/TROUBLESHOOTING.md`
- `docs/ROADMAP.md`
