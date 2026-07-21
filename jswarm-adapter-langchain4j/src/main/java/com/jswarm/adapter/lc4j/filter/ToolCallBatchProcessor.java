package com.jswarm.adapter.lc4j.filter;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.core.RouteDeniedException;
import com.jswarm.core.SwarmException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ToolCallBatchProcessor {

    public sealed interface Outcome {
        record Continue() implements Outcome {}

        record Handoff(String targetAgentId, ToolExecutionRequest routingCall) implements Outcome {}

        record Delegate(String targetAgentId, String task, ToolExecutionRequest routingCall) implements Outcome {}
    }

    private ToolCallBatchProcessor() {
    }

    public static Outcome process(
            SwarmFilter filter,
            String sourceAgentId,
            List<ChatMessage> messages,
            AiMessage aiMessage,
            ExternalToolExecutor exec,
            BiConsumer<String, ToolExecutionRequest> onToolCall,
            BiConsumer<String, String> onToolResult) {

        List<ToolExecutionRequest> calls = aiMessage.toolExecutionRequests();
        String protocolError = validateBatch(calls);
        if (protocolError != null) {
            appendAssistantAndResults(messages, aiMessage, calls, protocolError);
            return new Outcome.Continue();
        }

        List<ToolExecutionRequest> routingCalls = new ArrayList<>();
        List<ToolExecutionRequest> externalCalls = new ArrayList<>();
        for (ToolExecutionRequest call : calls) {
            if (isRoutingTool(call.name())) {
                routingCalls.add(call);
            } else {
                externalCalls.add(call);
            }
        }

        if (routingCalls.size() > 1 || (!routingCalls.isEmpty() && !externalCalls.isEmpty())) {
            appendAssistantAndResults(messages, aiMessage, calls,
                    "Jswarm: only one routing tool call is allowed per turn.");
            return new Outcome.Continue();
        }

        messages.add(aiMessage);

        if (routingCalls.size() == 1) {
            ToolExecutionRequest routingCall = routingCalls.get(0);
            FilterDecision decision;
            try {
                decision = filter.decide(sourceAgentId, routingCall);
            } catch (SwarmException e) {
                messages.add(ToolExecutionResultMessage.from(routingCall,
                        "Jswarm recovery: invalid routing arguments. " + e.getMessage()));
                return new Outcome.Continue();
            }

            if (decision instanceof FilterDecision.Reject reject) {
                messages.add(ToolExecutionResultMessage.from(routingCall, reject.modelSafeMessage()));
                return new Outcome.Continue();
            }
            if (decision instanceof FilterDecision.Handoff handoff) {
                messages.add(ToolExecutionResultMessage.from(routingCall,
                        "Jswarm: transferred to agent '" + handoff.targetAgentId() + "'."));
                return new Outcome.Handoff(handoff.targetAgentId(), routingCall);
            }
            if (decision instanceof FilterDecision.Delegate delegate) {
                return new Outcome.Delegate(delegate.targetAgentId(), delegate.task(), routingCall);
            }
            messages.add(ToolExecutionResultMessage.from(routingCall,
                    "Jswarm: routing tool was not authorized."));
            return new Outcome.Continue();
        }

        for (ToolExecutionRequest call : calls) {
            if (onToolCall != null) {
                onToolCall.accept(sourceAgentId, call);
            }
            String result;
            try {
                result = exec.execute(call);
            } catch (RuntimeException e) {
                result = "Jswarm recovery: tool '" + call.name()
                        + "' failed. Please answer directly or try another available tool.";
            }
            result = ProtocolLimits.truncateResult(result);
            if (onToolResult != null) {
                onToolResult.accept(call.name(), result);
            }
            messages.add(ToolExecutionResultMessage.from(call, result));
        }
        return new Outcome.Continue();
    }

    public static Outcome processDelegateTurn(
            SwarmFilter filter,
            String sourceAgentId,
            List<ChatMessage> subMessages,
            AiMessage aiMessage,
            ExternalToolExecutor exec) {

        List<ToolExecutionRequest> calls = aiMessage.toolExecutionRequests();
        String protocolError = validateBatch(calls);
        if (protocolError != null) {
            appendAssistantAndResults(subMessages, aiMessage, calls, protocolError);
            return new Outcome.Continue();
        }

        List<ToolExecutionRequest> routingCalls = calls.stream()
                .filter(call -> isRoutingTool(call.name()))
                .toList();
        if (!routingCalls.isEmpty()) {
            appendAssistantAndResults(subMessages, aiMessage, calls,
                    "Jswarm: routing tools are not allowed inside delegate sub-loops.");
            return new Outcome.Continue();
        }

        subMessages.add(aiMessage);
        for (ToolExecutionRequest call : calls) {
            String result;
            try {
                result = exec.execute(call);
            } catch (RuntimeException e) {
                result = "Jswarm recovery: tool '" + call.name() + "' failed.";
            }
            subMessages.add(ToolExecutionResultMessage.from(call, ProtocolLimits.truncateResult(result)));
        }
        return new Outcome.Continue();
    }

    private static void appendAssistantAndResults(
            List<ChatMessage> messages,
            AiMessage aiMessage,
            List<ToolExecutionRequest> calls,
            String resultText) {
        messages.add(aiMessage);
        for (ToolExecutionRequest call : calls) {
            messages.add(ToolExecutionResultMessage.from(call, resultText));
        }
    }

    private static String validateBatch(List<ToolExecutionRequest> calls) {
        if (calls == null || calls.isEmpty()) {
            return "Jswarm: tool call batch is empty.";
        }
        Set<String> ids = new HashSet<>();
        for (ToolExecutionRequest call : calls) {
            if (call.name() == null || call.name().isBlank()) {
                return "Jswarm: tool name must not be blank.";
            }
            String id = call.id();
            if (id == null || id.isBlank()) {
                return "Jswarm: tool call id must not be blank.";
            }
            if (!ids.add(id)) {
                return "Jswarm: duplicate tool call id in the same batch.";
            }
        }
        return null;
    }

    private static boolean isRoutingTool(String toolName) {
        return "handoff".equals(toolName) || "delegate".equals(toolName);
    }
}
