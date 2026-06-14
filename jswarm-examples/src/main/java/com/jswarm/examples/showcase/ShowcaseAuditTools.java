// Swarm 级 auditLog 工具 Bean
package com.jswarm.examples.showcase;

import dev.langchain4j.agent.tool.Tool;

public class ShowcaseAuditTools {

    @Tool(name = "auditLog", value = "记录审计日志，参数 message 为摘要内容")
    public String auditLog(String message) {
        ShowcaseEventCollector c = ShowcaseEventCollector.current();
        if (c != null) {
            c.add(ShowcaseEvent.tool("swarm", "auditLog", message));
        }
        return "audit recorded: " + message;
    }
}
