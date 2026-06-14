<p align="center">
  <img src="./jswarm_bee_icon.png" alt="Jswarm Logo" width="160" />
</p>

<h1 align="center">Jswarm</h1>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/JDK-17%2B-orange.svg" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/LangChain4j-1.15.x-green.svg" alt="LangChain4j" />
</p>

<p align="center">
  <strong>Lightweight Multi-Agent Orchestration Framework for Java</strong>
</p>

<p align="center">
  <a href="README.md">中文</a> | English
</p>

<p align="center">
  <a href="#concepts">Concepts</a> •
  <a href="#modules">Modules</a> •
  <a href="#getting-started">Getting Started</a> •
  <a href="#showcase">Showcase</a> •
  <a href="#api-overview">API Overview</a> •
  <a href="#roadmap">Roadmap</a>
</p>

---

Jswarm handles the **orchestration layer** for multi-agent systems — agent topology, handoff/delegate routing, and request-scoped context. LLM calls, tool execution, and message storage are delegated to LangChain4j (or your own adapter).

**Requirements:** JDK 17+ · Maven 3.8+ · LangChain4j 1.15.x (adapter module)

---

## Concepts

Agent orchestration boils down to two routing primitives:

### Handoff

Transfer conversation control to the target agent. Chat history is preserved, SystemMessage is replaced. The source agent exits the main loop.

```
User → router ──handoff──> tech ──> replies to user
```

### Delegate

The target agent runs a sub-loop to complete a task. The result is returned as a tool result, and the source agent continues.

```
User → router ──delegate──> order ──> result back to router ──> replies to user
```

The framework injects two orchestration tools into the LLM automatically (names are reserved — user `@Tool` methods must not collide):

| Tool | Semantics |
|------|-----------|
| `handoff` | Transfer control, param `target` |
| `delegate` | Dispatch sub-task, params `target` + `task` |

---

## Modules

```
Application Code
     │
     ▼
┌──────────────────────────────────────────┐
│ jswarm-adapter-langchain4j               │  JAgent / SwarmRunner / SwarmFilter
│ LangChain4j ChatModel + @Tool bridge     │
├──────────────────────────────────────────┤
│ jswarm-core                              │  Swarm / Agent / SwarmContext
│ Pure JDK, zero external dependencies     │
└──────────────────────────────────────────┘
     │
     ▼
jswarm-examples          Showcase web demo (optional)
```

| Module | Description |
|--------|-------------|
| `jswarm-core` | Agent abstraction, Swarm topology, SwarmContext `{key}` templates |
| `jswarm-adapter-langchain4j` | JAgent runtime, SwarmRunner, handoff/delegate filtering and tool injection |
| `jswarm-examples` | Showcase: Web UI + customer service demo |

---

## Getting Started

### 1. Build

```bash
git clone <your-repo-url>
cd Jswarm
mvn install -DskipTests
```

### 2. Define Agents and Topology

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

JAgent router = JAgent.builder("router", "Router")
        .description("Analyze intent and dispatch")
        .instructions("You are a router. Handoff tech issues to tech, sales issues to sales.")
        .model(model)
        .build();

JAgent tech = JAgent.builder("tech", "Tech Support")
        .description("Resolve technical issues")
        .instructions("You are tech support. Answer based on conversation history.")
        .model(model)
        .build();

JAgent sales = JAgent.builder("sales", "Sales")
        .description("Product inquiries")
        .instructions("You are a sales agent. Answer pricing and purchasing questions.")
        .model(model)
        .build();

Swarm swarm = Swarm.create("customer-service")
        .agent(router).agent(tech).agent(sales)
        .entry("router")
        .handoff("router", "tech", "sales")
        .build();
```

### 3. Run

`SwarmRunner.run()` executes a single orchestration pass starting from the entry agent. It does not carry history across calls.

```java
SwarmContext ctx = new SwarmContext();
ctx.put("user_name", "Alice");

SwarmRunner runner = SwarmRunner.create(swarm);
String reply = runner.run("My activation code is invalid, please help", ctx);
System.out.println(reply);
```

For multi-turn conversations, maintain a `List<ChatMessage>` history at the application layer. See `ShowcaseSessionEngine` in `jswarm-examples`.

---

## Showcase

`jswarm-examples` includes a web-based customer service demo covering the main capabilities: handoff, delegate, lifecycle hooks, dynamic instructions, `SwarmContext`, `ExternalToolExecutor`, etc.

### Launch

```bash
export DEEPSEEK_API_KEY=sk-...
mvn -pl jswarm-examples exec:java
```

Open **http://localhost:8080** in your browser.

If dependencies are not yet installed:

```bash
mvn -pl jswarm-examples -am install -DskipTests
```

### Agent Topology

```
entry: router
  ├─ handoff → tech / sales / order
  └─ delegate → order → analyst
Swarm-level ExternalToolExecutor: auditLog
```

Session data is persisted in `data/showcase.db` (gitignored, auto-created locally).

---

## API Overview

### jswarm-core

- `Agent`: id / name / description / instructions
- Lifecycle hooks: `onEnter` / `onExit` / `onDelegateEnter` / `onDelegateExit`
- `Swarm` / `SwarmBuilder`: entry, handoff, delegate topology
- `SwarmContext`: request-scoped `{key}` template resolution, ThreadLocal isolation

### jswarm-adapter-langchain4j

- `JAgent.builder()`: build agents with hook lambdas and `@Tool` bean registration
- `JAgent.fromTools()` / `fromAiService()`: bridge existing LangChain4j interfaces
- `JAgent.decorate()`: decorator pattern to layer hooks
- `instructions(Function<SwarmContext, String>)`: dynamic instructions
- `SwarmToolInjector`: inject orchestration tools based on topology
- `SwarmFilter`: intercept tool calls, dispatch handoff / delegate / external tools
- `SwarmRunner`: orchestration main loop
- `SwarmRunOptions`: `maxTurns`, error recovery, `modelTimeout`
- `ExternalToolExecutor`: swarm-level tool fallback

### jswarm-examples

- `ShowcaseApplication`: HTTP server + static frontend
- `ShowcaseSessionEngine`: multi-turn session management
- SQLite session persistence

---

## SwarmContext Timing

| Event | instructions resolved? |
|-------|----------------------|
| Agent enters main loop (first turn) | ✅ |
| Handoff to target agent | ✅ |
| Delegate sub-loop entry | ✅ (`onDelegateEnter` runs before resolve) |
| After tool execution within same agent | ❌ SystemMessage is frozen |

Prefer passing dynamic state via **tool results** or **delegate return values**. Alternatively, write to ctx **before run / in onEnter / before handoff**.

---

## Testing

```bash
mvn test
```

The examples module requires a live LLM and is typically skipped in CI. Unit tests are concentrated in the core and adapter modules.

---

## Roadmap

**Done**

- Core topology and SwarmContext
- LC4j adapter and SwarmRunner
- Handoff / delegate routing and tool injection
- Lifecycle hooks, JAgent extension paths (builder / decorate / fromAiService)
- Dynamic instructions, error recovery, Showcase web demo

**Planned**

- Streaming output and event callbacks
- `jswarm-spring-boot-starter`
- Additional model adapters

---

## License

[MIT License](LICENSE)
