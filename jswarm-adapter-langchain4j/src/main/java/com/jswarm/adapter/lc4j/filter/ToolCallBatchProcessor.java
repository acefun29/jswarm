// LangChain4j 批处理兼容入口
package com.jswarm.adapter.lc4j.filter;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.runtime.Lc4jMessageCodec;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.runtime.route.RouteDecision;
import com.jswarm.runtime.tool.ToolBatchPlanner;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.ToolCall;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.List;
import java.util.function.BiConsumer;

@Deprecated(forRemoval = true)
public final class ToolCallBatchProcessor {

    public sealed interface Outcome {
        record Continue() implements Outcome {
        }

        record Handoff(String targetAgentId, ToolExecutionRequest routingCall) implements Outcome {
        }

        record Delegate(String targetAgentId, String task, ToolExecutionRequest routingCall) implements Outcome {
        }
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
        return processInternal(
                filter, sourceAgentId, messages, aiMessage, exec, onToolCall, onToolResult, false);
    }

    public static Outcome processDelegateTurn(
            SwarmFilter filter,
            String sourceAgentId,
            List<ChatMessage> subMessages,
            AiMessage aiMessage,
            ExternalToolExecutor exec) {
        return processInternal(
                filter, sourceAgentId, subMessages, aiMessage, exec, null, null, true);
    }

    private static Outcome processInternal(
            SwarmFilter filter,
            String sourceAgentId,
            List<ChatMessage> messages,
            AiMessage aiMessage,
            ExternalToolExecutor exec,
            BiConsumer<String, ToolExecutionRequest> onToolCall,
            BiConsumer<String, String> onToolResult,
            boolean delegate) {
        List<ToolCall> calls = new Lc4jMessageCodec().decode(aiMessage).toolCalls();
        ToolBatchPlanner.Plan plan = ToolBatchPlanner.plan(
                calls,
                delegate,
                call -> route(filter, sourceAgentId, providerCall(aiMessage, call.id())));
        messages.add(aiMessage);
        for (CanonicalMessage result : plan.resultMessages()) {
            messages.add(ToolExecutionResultMessage.from(
                    result.toolCallId(), result.toolName(), result.text()));
        }
        for (ToolCall call : plan.externalCalls()) {
            ToolExecutionRequest providerCall = providerCall(aiMessage, call.id());
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
            messages.add(ToolExecutionResultMessage.from(providerCall, result));
        }
        return outcome(plan.decision(), aiMessage);
    }

    private static RouteDecision route(
            SwarmFilter filter, String sourceAgentId, ToolExecutionRequest call) {
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

    private static Outcome outcome(RouteDecision decision, AiMessage message) {
        ToolExecutionRequest call = message.toolExecutionRequests().isEmpty()
                ? null : message.toolExecutionRequests().get(0);
        if (decision instanceof RouteDecision.Handoff handoff) {
            return new Outcome.Handoff(handoff.targetAgentId(), call);
        }
        if (decision instanceof RouteDecision.Delegate delegate) {
            return new Outcome.Delegate(delegate.targetAgentId(), delegate.task(), call);
        }
        return new Outcome.Continue();
    }

    private static ToolExecutionRequest providerCall(AiMessage message, String id) {
        return message.toolExecutionRequests().stream()
                .filter(call -> id.equals(call.id()))
                .findFirst()
                .orElseThrow();
    }
}
