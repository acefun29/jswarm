// 已废弃的 Spring AI 批处理兼容入口
package com.jswarm.adapter.springai.filter;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.function.BiConsumer;

@Deprecated
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
        throw new UnsupportedOperationException("Tool batches are processed by shared RunEngine");
    }

    public static Outcome processDelegateTurn(
            SwarmFilter filter,
            String sourceAgentId,
            List<Message> subMessages,
            AssistantMessage assistantMsg,
            ExternalToolExecutor exec) {
        throw new UnsupportedOperationException("Delegate turns are processed by shared RunEngine");
    }
}
