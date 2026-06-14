package com.jswarm.adapter.lc4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

@FunctionalInterface
public interface ExternalToolExecutor {
    String execute(ToolExecutionRequest request);

    default String execute(ToolExecutionRequest request, Object memoryId) {
        return execute(request);
    }
}
