// Adapter TCK 场景
package com.jswarm.tck;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record TckFixture(
        List<TckAgent> agents,
        String entryAgentId,
        Map<String, Set<String>> handoffs,
        Map<String, Set<String>> delegates,
        String userMessage,
        Map<String, Object> context,
        int maxTurns,
        boolean nullContext,
        long modelTimeoutMillis) {

    public TckFixture {
        agents = agents != null ? List.copyOf(agents) : List.of();
        handoffs = handoffs != null ? Map.copyOf(handoffs) : Map.of();
        delegates = delegates != null ? Map.copyOf(delegates) : Map.of();
        context = context != null ? Map.copyOf(context) : Map.of();
    }

    public TckFixture(
            List<TckAgent> agents,
            String entryAgentId,
            Map<String, Set<String>> handoffs,
            Map<String, Set<String>> delegates,
            String userMessage,
            Map<String, Object> context,
            int maxTurns,
            boolean nullContext) {
        this(agents, entryAgentId, handoffs, delegates, userMessage, context,
                maxTurns, nullContext, 60_000);
    }

    public static TckFixture text(TckAgent agent) {
        return new TckFixture(
                List.of(agent), agent.id(), Map.of(), Map.of(), "hello", Map.of(), 10, false, 60_000);
    }
}
