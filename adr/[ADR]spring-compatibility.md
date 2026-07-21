# [ADR] Spring 兼容矩阵

> 状态：已采纳  
> 日期：2026-07-21  
> 关联计划：`[计划-00]基线与兼容矩阵冻结.md`  
> 基线 commit：`5e4df28`

## 决策

本基线唯一支持的 Spring 组合：

| 组件 | 版本 |
|------|------|
| Spring AI | **2.0.0**（`spring-ai-bom`） |
| Spring Boot | **4.0.7**（`spring-boot-dependencies`） |
| Spring Framework / Micrometer | **跟随 Boot 4.0.7 BOM**，禁止在模块中硬编码（本基线解析为 Micrometer **1.16.6**） |

LangChain4j 适配线独立：

| 组件 | 版本 |
|------|------|
| LangChain4j | **1.15.1** |

两条适配线互不引入对方依赖；父 POM 可统一管理版本属性，但 Boot BOM 不会把 Spring 强加到未声明 Spring 依赖的模块。

## 支持与不支持

**支持（已写入 README / 本 ADR）：**

- Spring AI 2.0.0 + Spring Boot 4.0.7 + JDK 17
- LangChain4j 1.15.1 + JDK 17（无 Spring）

**不支持（不得写入 README 为可用组合）：**

- Spring AI 2.0.x + Spring Boot 3.x
- Spring AI 1.x（本基线不承诺）
- 在 Adapter 中硬编码 Boot / Micrometer 版本

Boot 4.1.x 与 Spring AI 2.0 官方兼容，但本基线 **未纳入 CI 验收**，暂不承诺。

## 版本管理规则

- 父 POM 同时 import `spring-ai-bom` 与 `spring-boot-dependencies`。
- `jswarm-adapter-spring-ai` / `jswarm-examples-spring-ai` 依赖不带版本号（或仅继承 `${spring-boot.version}` 用于插件）。
- Starter 拆分与更深依赖治理归 `[计划-06]`。

## 后果

- `mvn dependency:tree` 不得再出现未经决策的 Boot 3.x 或旧 Micrometer 硬编码。
- README badge 与环境要求必须与本表一致。
