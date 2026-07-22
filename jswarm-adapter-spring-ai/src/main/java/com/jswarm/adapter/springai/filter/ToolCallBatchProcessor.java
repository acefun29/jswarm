// Spring AI 批处理兼容入口
package com.jswarm.adapter.springai.filter;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.runtime.SpringAiMessageCodec;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.runtime.route.RouteDecision;
import com.jswarm.runtime.tool.ToolBatchPlanner;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.ToolCall;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Deprecated(forRemoval = true)
public final class ToolCallBatchProcessor {

    public sealed interface Outcome {
        record Continue() implements Outcome {
        }

        record Handoff(String targetAgentId, AssistantMessage.ToolCall routingCall) implements Outcome {
        }

        record Delegate(String targetAgentId, String task, AssistantMessage.ToolCall routingCall) implements Outcome {
        }
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
        return processInternal(
                filter, sourceAgentId, messages, assistantMsg, exec, onToolCall, onToolResult, false);
    }

    public static Outcome processDelegateTurn(
            SwarmFilter filter,
            String sourceAgentId,
            List<Message> subMessages,
            AssistantMessage assistantMsg,
            ExternalToolExecutor exec) {
        return processInternal(
                filter, sourceAgentId, subMessages, assistantMsg, exec, null, null, true);
    }

    private static Outcome processInternal(
            SwarmFilter filter,
            String sourceAgentId,
            List<Message> messages,
            AssistantMessage assistantMsg,
            ExternalToolExecutor exec,
            BiConsumer<String, AssistantMessage.ToolCall> onToolCall,
            BiConsumer<String, String> onToolResult,
            boolean delegate) {
        List<ToolCall> calls = new SpringAiMessageCodec().decode(assistantMsg).toolCalls();
        ToolBatchPlanner.Plan plan = ToolBatchPlanner.plan(
                calls,
                delegate,
                call -> route(filter, sourceAgentId, providerCall(assistantMsg, call.id())));
        messages.add(assistantMsg);
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (CanonicalMessage result : plan.resultMessages()) {
            responses.add(new ToolResponseMessage.ToolResponse(
                    result.toolCallId(), result.toolName(), result.text()));
        }
        for (ToolCall call : plan.externalCalls()) {
            AssistantMessage.ToolCall providerCall = providerCall(assistantMsg, call.id());
            if (onToolCall != null) {
                onToolCall.accept(sourceAgentId, providerCall);
            }
            String result;
            try {
                RunScopeChecks.beforeToolCall(RunScope.current());
                result = exec.execute(providerCall);
            } catch (RuntimeException failure) {
                result = ToolBatchPlanner.externalFailure(call.name(), delegate);
            }
            result = ProtocolLimits.truncateResult(result);
            RunScopeChecks.recordToolResultBytes(RunScope.current(), result);
            if (onToolResult != null) {
                onToolResult.accept(call.name(), result);
            }
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
        }
        if (!responses.isEmpty()) {
            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }
        return outcome(plan.decision(), assistantMsg);
    }

    private static RouteDecision route(
            SwarmFilter filter, String sourceAgentId, AssistantMessage.ToolCall call) {
        FilterDecision decision = filter.decide(sourceAgentId, call);
        if (decision instanceof FilterDecision.Handoff handoff) {
            return new RouteDecision.Handoff(handoff.targetAgentId());
        }
        if (decision instanceof FilterDecision.Delegate delegate) {
            return new RouteDecision.Delegate(delegate.targetAgentId(), delegate.task());
        }
        if (decision instanceof FilterDecision.Reject reject) {
            return new RouteDecision.Reject(reject.modelSafeMessage());
        }
        return new RouteDecision.Continue();
    }

    private static Outcome outcome(RouteDecision decision, AssistantMessage message) {
        AssistantMessage.ToolCall call = message.getToolCalls().isEmpty()
                ? null : message.getToolCalls().get(0);
        if (decision instanceof RouteDecision.Handoff handoff) {
            return new Outcome.Handoff(handoff.targetAgentId(), call);
        }
        if (decision instanceof RouteDecision.Delegate delegate) {
            return new Outcome.Delegate(delegate.targetAgentId(), delegate.task(), call);
        }
        return new Outcome.Continue();
    }

    private static AssistantMessage.ToolCall providerCall(AssistantMessage message, String id) {
        return message.getToolCalls().stream()
                .filter(call -> id.equals(call.id()))
                .findFirst()
                .orElseThrow();
    }
}
