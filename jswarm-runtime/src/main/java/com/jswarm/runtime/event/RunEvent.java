// 统一运行事件
package com.jswarm.runtime.event;

import com.jswarm.spi.id.ParentRunId;
import com.jswarm.spi.id.RunId;

import java.time.Instant;
import java.util.Map;

public record RunEvent(
        RunId runId,
        ParentRunId parentRunId,
        long seq,
        int turn,
        int depth,
        String agentId,
        String callId,
        Instant timestamp,
        RunEventType type,
        Map<String, Object> payload) {

    public RunEvent {
        if (runId == null) {
            throw new IllegalArgumentException("runId must not be null");
        }
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        if (timestamp == null || type == null) {
            throw new IllegalArgumentException("timestamp and type must not be null");
        }
        parentRunId = parentRunId != null ? parentRunId : ParentRunId.ROOT;
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }
}
