package com.jswarm.adapter.springai.tool;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.ToolNotHandledException;
import com.jswarm.core.SwarmException;

public final class ToolExecutionMerger {

    private ToolExecutionMerger() {
    }

    public static ExternalToolExecutor merge(ExternalToolExecutor agentExecutor,
                                              ExternalToolExecutor swarmFallback) {
        ExternalToolExecutor merged;
        if (agentExecutor != null) {
            merged = toolCall -> {
                try {
                    return agentExecutor.execute(toolCall);
                } catch (ToolNotHandledException e) {
                    if (swarmFallback != null) {
                        return swarmFallback.execute(toolCall);
                    }
                    throw e;
                }
            };
        } else {
            merged = swarmFallback;
        }
        if (merged == null) {
            throw new SwarmException("No tool executor available");
        }
        return merged;
    }
}
