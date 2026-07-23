<div align="center">

![Jswarm](jswarm.png)

</div>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License" /></a>
  <a href="https://jitpack.io/#acefun29/jswarm"><img src="https://jitpack.io/v/acefun29/jswarm.svg" alt="JitPack" /></a>
  <img src="https://img.shields.io/badge/JDK-17%2B-orange.svg" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/LangChain4j-1.15.1-green.svg" alt="LangChain4j" />
  <img src="https://img.shields.io/badge/Spring_AI-2.0.0-blue.svg" alt="Spring AI" />
  <img src="https://img.shields.io/badge/Spring_Boot-4.0.7-brightgreen.svg" alt="Spring Boot" />
</p>

<p align="center">
  <strong>轻量级 Java 多 Agent 编排框架</strong>
</p>

<p align="center">
  中文 | <a href="README_EN.md">English</a>
</p>

<p align="center">
  <a href="#核心概念">核心概念</a> •
  <a href="#模块结构">模块结构</a> •
  <a href="[%E6%A8%A1%E5%9D%97]%E8%81%8C%E8%B4%A3%E4%B8%8E%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97.md">模块使用指南</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#showcase">Showcase</a> •
  <a href="#api-概览">API 概览</a> •
  <a href="#路线图">路线图</a>
</p>

---

Jswarm 专注于多 Agent 场景下的**编排层**——定义 Agent 拓扑、处理 handoff/delegate 路由、管理请求级上下文。LLM 调用、工具执行、消息存储等运行时职责由 LangChain4j、Spring AI 或其他适配层承担，Jswarm 不介入。

**环境要求：** JDK 17+（编译 `release=17`）· Maven 3.8+ · LangChain4j **1.15.1** 或 Spring AI **2.0.0** + Spring Boot **4.0.7**

**正式仓库：** [github.com/acefun29/jswarm](https://github.com/acefun29/jswarm) · 发布渠道 [JitPack](https://jitpack.io/#acefun29/jswarm)

兼容矩阵与模块边界见 `adr/`。不支持：Spring AI 2.0 + Boot 3.x。Java 包名仍为 `com.jswarm.*`。

---

## 核心概念

多 Agent 协同的编排层归结为两种路由原语：

### Handoff（接管）

对话控制权转交目标 Agent，保留历史消息，替换 SystemMessage。原 Agent 退出主循环。

```
用户 → router ──handoff──> tech ──> 直接回复用户
```

### Delegate（委派）

目标 Agent 在子循环中执行任务，结果作为 tool result 返回给原 Agent，原 Agent 继续主循环。

```
用户 → router ──delegate──> order ──> 结果回 router ──> 回复用户
```

框架向 LLM 自动注入两个编排工具（名称固定，用户 `@Tool` 不可同名）：

| 工具 | 语义 |
|------|------|
| `handoff` | 接管，参数 `target` |
| `delegate` | 委派，参数 `target` + `task` |

---

## 模块结构

```
开发者代码
     │
     ▼
┌──────────────────────────────────────────┐
│ jswarm-adapter-langchain4j / spring-ai   │  Provider gateway / codec / tool bridge
├──────────────────────────────────────────┤
│ jswarm-runtime                           │  唯一模型无关编排状态机
├──────────────────────────────────────────┤
│ jswarm-spi                               │  Canonical message / scope / error
├──────────────────────────────────────────┤
│ jswarm-core                              │  Swarm / Agent / SwarmContext
└──────────────────────────────────────────┘
     │
     ▼
jswarm-examples          基于 LangChain4j 的 Showcase Web 演示
jswarm-examples-spring-ai 基于 Spring AI 的 Showcase Web 演示
```

| 模块 | 说明 |
|------|------|
| `jswarm-core` | Agent 抽象、Swarm 拓扑、SwarmContext `{key}` 模板（纯 JDK，零外部依赖） |
| `jswarm-spi` | Canonical message、RunScope、预算/取消、typed error 与 provider-neutral SPI |
| `jswarm-runtime` | handoff、delegate、recovery、生命周期与 terminal event 的唯一共享状态机 |
| `jswarm-adapter-tck` | 两个 Adapter 共用的兼容性测试夹具 |
| `jswarm-adapter-langchain4j` | LangChain4j gateway、codec、工具桥接与兼容 `SwarmRunner` facade |
| `jswarm-adapter-spring-ai` | Spring AI gateway、codec、工具桥接与兼容 `SwarmRunner` facade；含实验性自动配置 |
| `jswarm-observability-micrometer` | 可选的 Micrometer Advisor、脱敏日志与调用指标 |
| `jswarm-spring-boot-starter` | 可选 Spring Boot auto-configuration、properties 校验与单 Swarm 条件 |
| `jswarm-examples` | LC4j Showcase Web（**不发布到 JitPack**） |
| `jswarm-examples-spring-ai` | Spring AI + Boot + SSE Showcase（**不发布到 JitPack**） |

各模块职责、Maven 坐标与推荐用法见 **[模块使用指南]([模块]职责与使用指南.md)**。

---

## 快速开始

### 1. 引入依赖

Jswarm 已发布至 [JitPack](https://jitpack.io/#acefun29/jswarm)，支持 Maven 和 Gradle 引入。业务项目通常引入 **adapter**（或 Boot starter）；`core` / `spi` / `runtime` 会作为传递依赖进入。完整说明见 [[模块]职责与使用指南.md]([模块]职责与使用指南.md)。

**Maven（LangChain4j 推荐）：**
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.acefun29.jswarm</groupId>
  <artifactId>jswarm-adapter-langchain4j</artifactId>
  <version>1.0.1</version>
</dependency>
```

**Gradle:**
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.acefun29.jswarm:jswarm-adapter-langchain4j:1.0.1'
}
```

> Spring AI / Boot 请改用 `jswarm-adapter-spring-ai` 或 `jswarm-spring-boot-starter`。  
> 如需获取最新代码或自行构建：`git clone https://github.com/acefun29/jswarm.git && cd jswarm && mvn install -DskipTests`。

### 2. 定义 Agent 与拓扑

你可以选用 **LangChain4j** 或 **Spring AI** 适配层。以下是两者的定义方式：

#### 选项 A：使用 LangChain4j 适配层
```java
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.run.SwarmRunner;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

ChatModel model = OpenAiChatModel.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .modelName("deepseek-chat")
        .build();

JAgent router = JAgent.builder("router", "路由专员")
        .description("分析意图并分发")
        .instructions("你是路由专员。技术问题 handoff 到 tech，销售问题 handoff 到 sales。")
        .model(model)
        .build();

JAgent tech = JAgent.builder("tech", "技术支持")
        .description("解决技术问题")
        .instructions("你是技术支持，结合对话历史回答用户。")
        .model(model)
        .build();

JAgent sales = JAgent.builder("sales", "销售专员")
        .description("产品咨询")
        .instructions("你是销售专员，解答价格与购买问题。")
        .model(model)
        .build();

Swarm swarm = Swarm.create("customer-service")
        .agent(router).agent(tech).agent(sales)
        .entry("router")
        .handoff("router", "tech", "sales")
        .build();
```

#### 选项 B：使用 Spring AI 适配层
```java
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.run.SwarmRunner;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import org.springframework.ai.chat.model.ChatModel;

// 此处以使用 Spring AI ChatModel 注入为例
ChatModel model = ...; 

JAgent router = JAgent.builder("router", "路由专员")
        .description("分析意图并分发")
        .instructions("你是路由专员。技术问题 handoff 到 tech，销售问题 handoff 到 sales。")
        .model(model)
        .build();

JAgent tech = JAgent.builder("tech", "技术支持")
        .description("解决技术问题")
        .instructions("你是技术支持，结合对话历史回答用户。")
        .model(model)
        .build();

JAgent sales = JAgent.builder("sales", "销售专员")
        .description("产品咨询")
        .instructions("你是销售专员，解答价格与购买问题。")
        .model(model)
        .build();

Swarm swarm = Swarm.create("customer-service")
        .agent(router).agent(tech).agent(sales)
        .entry("router")
        .handoff("router", "tech", "sales")
        .build();
```

### 3. 运行

#### 同步运行方式 (LangChain4j / Spring AI)

`SwarmRunner.run()` 执行一次编排流程，从入口 Agent 开始，不维护跨轮历史：

```java
SwarmContext ctx = new SwarmContext();
ctx.put("user_name", "张三");

SwarmRunner runner = SwarmRunner.create(swarm);
String reply = runner.run("我的激活码无效，请帮我排查", ctx);
System.out.println(reply);
```

#### 流式运行方式 (仅限 Spring AI)

通过 `SwarmRunner.runStreaming()` 接收编排流式事件（如 Token、Handoff 切换等）：

```java
SwarmContext ctx = new SwarmContext();
ctx.put("user_name", "张三");

SwarmRunner runner = SwarmRunner.create(swarm);
RunHandle handle = runner.runStreaming("我的激活码无效，请帮我排查", ctx, event -> {
    if (event instanceof SwarmEvent.Token token) {
        System.out.print(token.text()); // 实时流式输出
    }
});
// 需要中断时：handle.cancel();
handle.await();
```

多轮对话可继续使用 `runWithHistory`，或通过 `HistoryStore` 重载保存 canonical history、版本和 checksum；应用层也可维护 `List<ChatMessage>` 兼容旧 API。

---

## Showcase

Jswarm 提供了两个 Web 客服演示模块，分别展示不同的底层适配能力：

### 1. LangChain4j Web Showcase (jswarm-examples)

基于 LangChain4j，覆盖 handoff、delegate、生命周期钩子、动态 instructions、`SwarmContext`、`ExternalToolExecutor` 等主要能力。

**启动方式：**
```bash
export DEEPSEEK_API_KEY=sk-...
mvn -pl jswarm-examples exec:java
```

示例默认连接 DeepSeek；不设置密钥时不会启动真实模型。CI 可使用 `mvn -pl jswarm-examples,jswarm-examples-spring-ai -am test` 执行无网络的确定性回归测试。会话按用户隔离，SQLite 会话带 TTL 与乐观版本号，SSE 客户端断开会取消运行。
浏览器打开 **http://localhost:8080**

首次运行若依赖未安装：
```bash
mvn -pl jswarm-examples -am install -DskipTests
```

### 2. Spring AI Web Showcase (jswarm-examples-spring-ai)

结合 Spring Boot + Spring AI，并提供 SSE (Server-Sent Events) 流式对话效果与 Spring Boot 自动装配。

**启动方式：**
```bash
export DEEPSEEK_API_KEY=sk-...
mvn -pl jswarm-examples-spring-ai spring-boot:run
```
浏览器打开 **http://localhost:8080**

### Agent 拓扑（以智能客服为例）

```
entry: router
  ├─ handoff → tech / sales / order
  └─ delegate → order → analyst
Swarm 级 ExternalToolExecutor: auditLog
```

会话数据持久化在 `data/showcase.db`（`.gitignore` 已忽略，本地自动生成）。

---

## API 概览

### jswarm-core

- `Agent`：id / name / description / instructions
- 生命周期钩子：`onEnter` / `onExit` / `onDelegateEnter` / `onDelegateExit`
- `Swarm` / `SwarmBuilder`：entry、handoff、delegate 拓扑声明
- `SwarmContext`：请求级 `{key}` 模板替换，ThreadLocal 隔离

### jswarm-spi / jswarm-runtime

- `RunScope` / `RunBudget`：父子 run 共享预算、截止时间与取消信号
- `CanonicalMessage` / `ModelGateway`：隔离 provider SDK 的消息与模型契约
- `RunEngine` / `RunState` / `RunEvent`：统一 route、lifecycle、recovery 与 terminal 顺序
- `ToolDescriptor` / `ToolRegistry`：注册快照、scope 授权、幂等、副作用、结果大小和安全审计预览

### jswarm-adapter-langchain4j

- `JAgent.builder()`：构建 Agent，支持钩子 lambda 和 `@Tool` Bean 注册
- `JAgent.fromTools()` / `fromAiService()`：桥接已有 LangChain4j 接口
- `JAgent.decorate()`：装饰器模式叠加钩子
- `instructions(Function<SwarmContext, String>)`：动态 instructions
- `SwarmToolInjector`：按拓扑注入编排工具
- `SwarmFilter`：旧 API 兼容包装，路由语义委托共享 Runtime
- `SwarmRunner`：共享 `RunEngine` 的兼容 facade
- `SwarmRunOptions`：`maxTurns`、错误恢复、`modelTimeout`
- `ExternalToolExecutor`：Swarm 级工具回落

工具执行迁移规则：`ExternalToolExecutor` 只执行已注册且模型可见的工具。模型猜测出的未注册名称不会再进入 fallback；请通过 `JAgent.builder().tools(...)`、`fromTools(...)` 或 provider-specific descriptor bridge 注册工具，再将旧 executor 作为执行实现。

### jswarm-adapter-spring-ai

- `JAgent.builder()` / `fromTools()` / `decorate()`：构建与桥接（稳定）
- `SwarmRunner.run()` / `runStreaming()`：同步与流式（稳定）
- `RunHandle`：流式完成、取消与 canonical event 快照
- Spring AI `fromAiService()`：**未实现**（抛异常，请用 `builder` / `fromTools`）
- `jswarm-spring-boot-starter` 的 `JswarmAutoConfiguration`：可选 Boot 4.0.7 自动配置
- `jswarm-observability-micrometer` 的 `SwarmLoggingAdvisor` / `SwarmMetricsAdvisor`：可选且默认脱敏

### 示例模块

- `jswarm-examples`：基于 LangChain4j 的 HTTP 服务 + 静态前端
- `jswarm-examples-spring-ai`：基于 Spring AI + Spring Boot + SSE 的对话服务

### 已知限制

- 单次 `run()` / `runStreaming()` **不**跨调用持久化多轮 history；会话由应用自管。
- 跨 `run()` 的恢复能力不在本基线公开承诺内。

---

## SwarmContext 时序

| 时机 | instructions resolve |
|------|---------------------|
| Agent 首轮进入主循环 | ✅ |
| handoff 到目标 Agent | ✅ |
| delegate 子循环入口 | ✅（`onDelegateEnter` 先于 resolve 执行） |
| 同 Agent 内 tool 执行后 | ❌ SystemMessage 已冻结 |

动态状态建议通过 **tool result** 或 **delegate 返回值** 传递；也可在 **run 前 / onEnter / handoff 前**写入 ctx。

---

## 测试

```bash
mvn test
```

Examples 模块依赖真实 LLM，CI 通常跳过；模型无关契约集中在 Core/SPI/Runtime，两个 Adapter 还必须通过 `jswarm-adapter-tck` 的同一测试集合。

---

## 路线图

**已完成**

- Core 拓扑与 SwarmContext
- LangChain4j 适配层与 SwarmRunner
- Spring AI 适配层与 SwarmRunner
- handoff / delegate 路由与工具注入
- 生命周期钩子、JAgent 扩展路径（builder / decorate；LC4j `fromAiService`）
- 动态 instructions、错误恢复
- 两 adapter 的 `runStreaming` 与 Spring AI Showcase SSE
- Spring Boot 自动装配（实验，仅 Spring AI 适配层）
- Canonical SPI、共享 Runtime 状态机与跨 Adapter TCK

**规划中**

- 更多模型与框架适配器
- Spring starter 拆分与可观测性治理
- Spring AI `fromAiService` 实现或正式移除

---

## 许可证

[MIT License](LICENSE)
