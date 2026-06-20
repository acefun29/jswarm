// SwarmRunListener SPI：同步 run 的事件回调
package com.jswarm.adapter.lc4j.run;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public interface SwarmRunListener {

    default void onAgentEnter(String agentId, String source) {
    }

    default void onAgentExit(String agentId) {
    }

    default void onToolCall(String agentId, String toolName, String args) {
    }

    default void onToolResult(String agentId, String toolName, String result) {
    }

    default void onHandoff(String from, String to) {
    }

    default void onDelegateStart(String parent, String target, String task) {
    }

    default void onDelegateEnd(String parent, String target) {
    }

    default void onRecovery(String agentId, String reason) {
    }

    default void onRunComplete(String finalText) {
    }

    default void onRunFail(String agentId, String error) {
    }

    default void onMessageHistoryUpdated(List<ChatMessage> messages) {
    }
}
