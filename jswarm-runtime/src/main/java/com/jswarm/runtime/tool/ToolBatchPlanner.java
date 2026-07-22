// Provider-neutral 工具批次规划
package com.jswarm.runtime.tool;

import com.jswarm.runtime.route.RouteDecision;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.ToolCall;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ToolBatchPlanner {

    private ToolBatchPlanner() {
    }

    public static Plan plan(
            List<ToolCall> calls,
            boolean delegate,
            Function<ToolCall, RouteDecision> routeResolver) {
        String validationError = validate(calls);
        if (validationError != null) {
            return rejected(calls, validationError);
        }
        List<ToolCall> routing = calls.stream()
                .filter(call -> OrchestrationTools.routing(call.name()))
                .toList();
        if (routing.size() > 1 || (!routing.isEmpty() && routing.size() != calls.size())) {
            return rejected(calls, "Jswarm: only one routing tool call is allowed per turn.");
        }
        if (routing.isEmpty()) {
            return new Plan(calls, List.of(), new RouteDecision.Continue(), null);
        }
        ToolCall call = routing.get(0);
        if (delegate) {
            return rejected(calls, "Jswarm: routing tools are not allowed inside delegate sub-runs.");
        }
        RouteDecision decision;
        try {
            decision = routeResolver.apply(call);
        } catch (RuntimeException failure) {
            return rejected(calls, "Jswarm recovery: invalid routing arguments.");
        }
        if (decision instanceof RouteDecision.Reject reject) {
            return rejected(calls, reject.modelSafeMessage());
        }
        if (decision instanceof RouteDecision.Handoff handoff) {
            CanonicalMessage result = CanonicalMessage.toolResult(
                    call.id(), call.name(),
                    "Jswarm: transferred to agent '" + handoff.targetAgentId() + "'.");
            return new Plan(List.of(), List.of(result), decision, null);
        }
        return new Plan(List.of(), List.of(), decision, null);
    }

    public static String externalFailure(String toolName, boolean delegate) {
        if (delegate) {
            return "Jswarm recovery: tool '" + toolName + "' failed.";
        }
        return "Jswarm recovery: tool '" + toolName
                + "' failed. Please answer directly or try another available tool.";
    }

    private static Plan rejected(List<ToolCall> calls, String message) {
        List<CanonicalMessage> results = calls == null ? List.of() : calls.stream()
                .map(call -> CanonicalMessage.toolResult(call.id(), call.name(), message))
                .toList();
        return new Plan(List.of(), results, new RouteDecision.Continue(), message);
    }

    private static String validate(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return "Jswarm: tool call batch is empty.";
        }
        Set<String> ids = new HashSet<>();
        for (ToolCall call : calls) {
            if (!ids.add(call.id())) {
                return "Jswarm: duplicate tool call id in the same batch.";
            }
        }
        return null;
    }

    public record Plan(
            List<ToolCall> externalCalls,
            List<CanonicalMessage> resultMessages,
            RouteDecision decision,
            String recoveryReason) {

        public Plan {
            externalCalls = List.copyOf(externalCalls);
            resultMessages = List.copyOf(resultMessages);
        }
    }
}
