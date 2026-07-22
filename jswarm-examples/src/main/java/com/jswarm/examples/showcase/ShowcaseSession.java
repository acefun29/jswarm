// 会话状态：history、Context、当前 Agent
package com.jswarm.examples.showcase;

import com.jswarm.core.SwarmContext;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public final class ShowcaseSession {

    private final String sessionId;
    private SwarmContext context;
    private String currentAgentId;
    private List<ChatMessage> history = new ArrayList<>();
    private boolean entryHookFired;
    private long version;

    public ShowcaseSession(String sessionId, String entryAgentId, SwarmContext context) {
        this.sessionId = sessionId;
        this.currentAgentId = entryAgentId;
        this.context = context;
    }

    public String sessionId() {
        return sessionId;
    }

    public SwarmContext context() {
        return context;
    }

    public void resetContext(SwarmContext context) {
        this.context = context;
        this.history = new ArrayList<>();
        this.entryHookFired = false;
    }

    public String currentAgentId() {
        return currentAgentId;
    }

    public void setCurrentAgentId(String currentAgentId) {
        this.currentAgentId = currentAgentId;
    }

    public List<ChatMessage> history() {
        return history;
    }

    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }

    public boolean entryHookFired() {
        return entryHookFired;
    }

    public void setEntryHookFired(boolean entryHookFired) {
        this.entryHookFired = entryHookFired;
    }

    public long version() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
