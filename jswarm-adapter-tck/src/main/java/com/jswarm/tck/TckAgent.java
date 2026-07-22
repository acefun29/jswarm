// TCK Agent 脚本
package com.jswarm.tck;

import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;
import java.util.Map;

public record TckAgent(
        String id,
        String instructions,
        List<CanonicalMessage> responses,
        Map<String, String> toolResults,
        long modelDelayMillis,
        boolean failExit) {

    public TckAgent {
        responses = responses != null ? List.copyOf(responses) : List.of();
        toolResults = toolResults != null ? Map.copyOf(toolResults) : Map.of();
    }

    public TckAgent(String id, String instructions, CanonicalMessage... responses) {
        this(id, instructions, List.of(responses), Map.of(), 0, false);
    }

    public TckAgent(
            String id,
            String instructions,
            List<CanonicalMessage> responses,
            Map<String, String> toolResults) {
        this(id, instructions, responses, toolResults, 0, false);
    }
}
