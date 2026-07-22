package com.jswarm.adapter.springai.filter;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ToolCallBatchProcessor {

    public sealed interface Outcome {
        record Continue() implements Outcome {}

        record Handoff(String targetAgentId, AssistantMessage.ToolCall routingCall) implements Outcome {}

        record Delegate(String targetAgentId, String task, AssistantMessage.ToolCall routingCall) implements Outcome {}
    }

    private ToolCallBatchProcessor() {
    }

    public static Outcome process(
            SwarmFilter filter,
            String sourceAgentId,
            List<Message> messages,
            AssistantMessage assistantMsg,
            ExternalToolExecutor exec,
            BiConsumer<String, AssistantMessage.ToolCall> onToolCall,
            BiConsumer<String, String> onToolResult) {

        List<AssistantMessage.ToolCall> calls = assistantMsg.getToolCalls();
        String protocolError = validateBatch(calls);
        if (protocolError != null) {
            appendAssistantAndResults(messages, assistantMsg, calls, protocolError);
            return new Outcome.Continue();
        }

        List<AssistantMessage.ToolCall> routingCalls = new ArrayList<>();
        List<AssistantMessage.ToolCall> externalCalls = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            if (isRoutingTool(call.name())) {
                routingCalls.add(call);
            } else {
                externalCalls.add(call);
            }
        }

        if (routingCalls.size() > 1 || (!routingCalls.isEmpty() && !externalCalls.isEmpty())) {
            appendAssistantAndResults(messages, assistantMsg, calls,
                    "Jswarm: only one routing tool call is allowed per turn.");
            return new Outcome.Continue();
        }

        messages.add(assistantMsg);

        if (routingCalls.size() == 1) {
            AssistantMessage.ToolCall routingCall = routingCalls.get(0);
            FilterDecision decision;
            try {
                decision = filter.decide(sourceAgentId, routingCall);
            } catch (SwarmException e) {
                messages.add(toolResponse(routingCall,
                        "Jswarm recovery: invalid routing arguments. " + e.getMessage()));
                return new Outcome.Continue();
            }

            if (decision instanceof FilterDecision.Reject reject) {
                messages.add(toolResponse(routingCall, reject.modelSafeMessage()));
                return new Outcome.Continue();
            }
            if (decision instanceof FilterDecision.Handoff handoff) {
                messages.add(toolResponse(routingCall,
                        "Jswarm: transferred to agent '" + handoff.targetAgentId() + "'."));
                return new Outcome.Handoff(handoff.targetAgentId(), routingCall);
            }
            if (decision instanceof FilterDecision.Delegate delegate) {
                return new Outcome.Delegate(delegate.targetAgentId(), delegate.task(), routingCall);
            }
            messages.add(toolResponse(routingCall, "Jswarm: routing tool was not authorized."));
            return new Outcome.Continue();
        }

        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            if (onToolCall != null) {
                onToolCall.accept(sourceAgentId, call);
            }
            String result;
            try {
                RunScopeChecks.beforeToolCall(RunScope.current());
                result = exec.execute(call);
            } catch (RuntimeException e) {
                result = "Jswarm recovery: tool '" + call.name()
                        + "' failed. Please answer directly or try another available tool.";
            }
            result = ProtocolLimits.truncateResult(result);
            RunScopeChecks.recordToolResultBytes(RunScope.current(), result);
            if (onToolResult != null) {
                onToolResult.accept(call.name(), result);
            }
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
        }
        messages.add(ToolResponseMessage.builder().responses(responses).build());
        return new Outcome.Continue();
    }

    public static Outcome processDelegateTurn(
            SwarmFilter filter,
            String sourceAgentId,
            List<Message> subMessages,
            AssistantMessage assistantMsg,
            ExternalToolExecutor exec) {

        List<AssistantMessage.ToolCall> calls = assistantMsg.getToolCalls();
        String protocolError = validateBatch(calls);
        if (protocolError != null) {
            appendAssistantAndResults(subMessages, assistantMsg, calls, protocolError);
            return new Outcome.Continue();
        }

        boolean hasRouting = calls.stream().anyMatch(call -> isRoutingTool(call.name()));
        if (hasRouting) {
            appendAssistantAndResults(subMessages, assistantMsg, calls,
                    "Jswarm: routing tools are not allowed inside delegate sub-loops.");
            return new Outcome.Continue();
        }

        subMessages.add(assistantMsg);
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            String result;
            try {
                RunScopeChecks.beforeToolCall(RunScope.current());
                result = exec.execute(call);
            } catch (RuntimeException e) {
                result = "Jswarm recovery: tool '" + call.name() + "' failed.";
            }
            result = ProtocolLimits.truncateResult(result);
            RunScopeChecks.recordToolResultBytes(RunScope.current(), result);
            responses.add(new ToolResponseMessage.ToolResponse(
                    call.id(), call.name(), result));
        }
        subMessages.add(ToolResponseMessage.builder().responses(responses).build());
        return new Outcome.Continue();
    }

    private static void appendAssistantAndResults(
            List<Message> messages,
            AssistantMessage assistantMsg,
            List<AssistantMessage.ToolCall> calls,
            String resultText) {
        messages.add(assistantMsg);
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), resultText));
        }
        messages.add(ToolResponseMessage.builder().responses(responses).build());
    }

    private static ToolResponseMessage toolResponse(AssistantMessage.ToolCall call, String result) {
        return ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse(call.id(), call.name(), result))).build();
    }

    private static String validateBatch(List<AssistantMessage.ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return "Jswarm: tool call batch is empty.";
        }
        Set<String> ids = new HashSet<>();
        for (AssistantMessage.ToolCall call : calls) {
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
