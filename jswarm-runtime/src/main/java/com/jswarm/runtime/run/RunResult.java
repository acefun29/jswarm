// Runtime 执行结果
package com.jswarm.runtime.run;

import com.jswarm.runtime.state.RunState;
import com.jswarm.spi.id.RunId;
import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;

public record RunResult(
        String reply,
        String currentAgentId,
        List<CanonicalMessage> history,
        RunState finalState,
        RunId runId) {

    public RunResult(String reply, String currentAgentId,
                     List<CanonicalMessage> history, RunState finalState) {
        this(reply, currentAgentId, history, finalState, null);
    }

    public RunResult {
        reply = reply != null ? reply : "";
        history = history != null ? List.copyOf(history) : List.of();
    }
}
