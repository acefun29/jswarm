# [ADR] Java 基线

> 状态：已采纳  
> 日期：2026-07-21  
> 关联计划：`[计划-00]基线与兼容矩阵冻结.md`  
> 基线 commit：`5e4df28`

## 决策

Jswarm 编译与测试基线为 **JDK 17**。父 POM 使用 `maven.compiler.release=17`（不再仅依赖 `source`/`target`）。

运行环境声明为 **JDK 17+**：可用更高 JDK 运行，但产物字节码目标固定为 17。

## 理由

- 当前源码与依赖（LangChain4j 1.15.1、Spring Boot 4.0.x）均支持 JDK 17。
- 保持 17 可避免无意抬高商业项目运行门槛。
- 若未来改为 JDK 21，必须同步 README、示例、CI 与本 ADR。

## 命令

```text
java -version
mvn -B -DskipTests verify
mvn -B test
mvn -B -DskipTests -Dmaven.compiler.release=17 clean verify
```

## 后果

- CI 与本地验收必须以 JDK 17（或更高，但 `release=17`）通过上述命令。
- 禁止在模块 POM 中覆盖为更高 `release`，除非先修订本 ADR。
