// Runtime 执行输入
package com.jswarm.runtime.run;

import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;

public record RunInput(
        String userMessage,
        List<CanonicalMessage> priorHistory,
        String startAgentId,
        boolean skipEntryHook,
        boolean streaming) {

    public RunInput {
        priorHistory = priorHistory != null ? List.copyOf(priorHistory) : List.of();
    }

    public static RunInput fresh(String userMessage, String startAgentId) {
        return new RunInput(userMessage, List.of(), startAgentId, false, false);
    }
}
