package com.jswarm.adapter.lc4j.tool;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

public final class ToolExecutionMerger {

    private ToolExecutionMerger() {
    }

    public static ExternalToolExecutor merge(ExternalToolExecutor agent, ExternalToolExecutor swarmFallback) {
        return req -> {
            try {
                if (agent != null) {
                    return agent.execute(req);
                }
                throw new ToolNotHandledException(req.name());
            } catch (ToolNotHandledException e) {
                if (swarmFallback != null) {
                    return swarmFallback.execute(req);
                }
                throw e;
            }
        };
    }
}
