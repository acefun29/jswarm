package com.jswarm.adapter.springai;

import org.springframework.ai.chat.messages.AssistantMessage;

@FunctionalInterface
public interface ExternalToolExecutor {
    String execute(AssistantMessage.ToolCall toolCall);
}
