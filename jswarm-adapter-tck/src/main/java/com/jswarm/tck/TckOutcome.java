// Adapter TCK 标准结果
package com.jswarm.tck;

import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;

public record TckOutcome(
        String reply,
        String currentAgentId,
        List<CanonicalMessage> history,
        List<String> events,
        SwarmErrorCode errorCode) {

    public TckOutcome {
        history = history != null ? List.copyOf(history) : List.of();
        events = events != null ? List.copyOf(events) : List.of();
    }
}
