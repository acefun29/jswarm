// 已废弃的 LangChain4j 批处理兼容入口
package com.jswarm.adapter.lc4j.filter;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.function.BiConsumer;

@Deprecated
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
        throw new UnsupportedOperationException("Tool batches are processed by shared RunEngine");
    }

    public static Outcome processDelegateTurn(
            SwarmFilter filter,
            String sourceAgentId,
            List<ChatMessage> subMessages,
            AiMessage aiMessage,
            ExternalToolExecutor exec) {
        throw new UnsupportedOperationException("Delegate turns are processed by shared RunEngine");
    }
}
