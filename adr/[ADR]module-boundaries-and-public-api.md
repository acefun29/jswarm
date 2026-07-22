# [ADR] 模块边界与公开能力

> 状态：已采纳
> 日期：2026-07-22
> 关联计划：`[计划-00]基线与兼容矩阵冻结.md`、`[计划-03]共享Runtime状态机与AdapterTCK.md`
> 基线 commit：`f0d8064`

## 正式坐标与仓库

| 项 | 值 |
|----|-----|
| Canonical repository | https://github.com/acefun29/Jswarm |
| groupId | `com.jswarm` |
| 当前版本 | `1.0.0-SNAPSHOT` |
| 发布渠道 | 计划-08 再定（Central 等） |

## 模块边界

| 模块 | 本基线角色 | 是否父 reactor 发布物 |
|------|------------|----------------------|
| `jswarm-core` | 纯拓扑，零外部依赖 | 是 |
| `jswarm-spi` | Canonical message、运行作用域、typed error 与 provider-neutral SPI | 是 |
| `jswarm-runtime` | 唯一模型无关编排状态机；仅依赖 Core/SPI | 是 |
| `jswarm-adapter-tck` | 两个 Adapter 共用的兼容性测试夹具 | 是（测试支持） |
| `jswarm-adapter-langchain4j` | LangChain4j gateway、codec、tool bridge 与兼容 facade | 是 |
| `jswarm-adapter-spring-ai` | Spring AI gateway、codec、tool bridge 与兼容 facade（含实验性 Boot autoconfig） | 是 |
| `jswarm-examples` | LC4j Showcase | 是（示例，非正式库消费者依赖） |
| `jswarm-examples-spring-ai` | Spring AI Showcase | 是（示例） |
| `jswarm-spring-boot-starter` | 计划从 adapter 拆出 | 否（未创建） |
| `compliance-swarm` | 合规旁路示例 | 否（不在父 `<modules>`，非正式坐标） |

Runtime 统一决定 route、lifecycle、recovery 与 terminal 顺序。Adapter 不得复制模型无关主循环，只实现 provider bridge；旧 `SwarmRunner` 保持公开 facade 兼容。

## 公开能力表

| 能力 | 状态 | 说明 |
|------|------|------|
| Canonical SPI / 共享 `RunEngine` | **稳定** | 两 Adapter 共用状态机并执行同一 TCK |
| Canonical `ToolDescriptor` / `ToolRegistry` | **稳定** | 工具必须先注册并通过 scope 授权；旧 executor 仅作为已注册工具的实现 |
| 同步 `SwarmRunner.run()` | **稳定** | 两 adapter |
| `SwarmRunner.runStreaming()` | **稳定** | 两 adapter 均有实现与单元测试 |
| LangChain4j `JAgent.fromAiService` | **稳定** | 有测试覆盖 |
| Spring AI `JAgent.fromAiService` | **未实现** | 抛 `UnsupportedOperationException`；不得写成稳定能力 |
| Spring AI `JAgent.fromTools` / `builder` | **稳定** | |
| `SwarmLoggingAdvisor` / `SwarmMetricsAdvisor` | **实验** | 依赖 Boot 4 矩阵；完整治理归计划-06 |
| `JswarmAutoConfiguration` | **实验** | 同上 |
| Swarm 级 `ExternalToolExecutor` | **稳定** | |
| 跨 `run()` 调用的多轮 history / 恢复 | **应用自管** | 框架单次 `run()` 不持久化会话 |
| JDK 17+ | **稳定** | 编译 `release=17` |

## 文档留痕

- 需进入 Git 的架构决策放在仓库根目录 `adr/`（本目录）。
- 本地工作文档（实施计划、审查稿、路线图草稿等）保留在 `doc/`，该目录 **整目录 gitignore，不提交**。
- 文档版本与代码关联：ADR 注明基线 commit；计划完成记录写在本地 `doc/` 中。
- `AGENTS.md` 可为本地协作文件，不强制发布。

## 后果

- README / Wiki 承诺不得超出本表「稳定」项。
- 「实验」能力须显式标注；「未实现」不得出现在稳定 API 列表。
