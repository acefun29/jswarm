// 只使用公开 SwarmRunner API 的多轮会话示例
package com.jswarm.examples.showcase;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.run.ChatMessageCodec;
import com.jswarm.adapter.lc4j.run.SwarmRunListener;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.adapter.lc4j.run.SwarmRunner;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import dev.langchain4j.data.message.ChatMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShowcaseSessionEngine {

    private final Swarm swarm;
    private final ExternalToolExecutor swarmToolExecutor;

    public ShowcaseSessionEngine(Swarm swarm, ExternalToolExecutor swarmToolExecutor) {
        this.swarm = swarm;
        this.swarmToolExecutor = swarmToolExecutor;
    }

    public ChatResult chat(ShowcaseSession session, String userMessage) {
        ShowcaseEventCollector collector = ShowcaseEventCollector.start();
        SwarmContext previous = SwarmContext.current();
        SwarmContext.set(session.context());
        try {
            SwarmRunner.RunResult result = SwarmRunner.create(
                            swarm,
                            SwarmRunOptions.builder().maxTurns(12).maxRecoveryAttempts(2).build(),
                            swarmToolExecutor,
                            showcaseListener(collector))
                    .runWithHistory(
                            userMessage,
                            session.history().isEmpty() ? null
                                    : ChatMessageCodec.forPersistence(session.history()),
                            session.currentAgentId(),
                            session.context(),
                            session.entryHookFired());
            session.setEntryHookFired(true);
            session.setHistory(ChatMessageCodec.forPersistence(result.updatedHistory()));
            session.setCurrentAgentId(result.currentAgentId());
            return new ChatResult(result.reply(), result.currentAgentId(),
                    collector.snapshotMaps(), contextSnapshot(session.context()));
        } finally {
            if (previous != null) {
                SwarmContext.set(previous);
            } else {
                SwarmContext.clear();
            }
            ShowcaseEventCollector.clear();
        }
    }

    private static SwarmRunListener showcaseListener(ShowcaseEventCollector collector) {
        return new SwarmRunListener() {
            @Override
            public void onAgentExit(String agentId) {
                collector.add(ShowcaseEvent.onExit(agentId));
            }

            @Override
            public void onHandoff(String from, String to) {
                collector.add(ShowcaseEvent.handoff(from, to));
            }

            @Override
            public void onDelegateStart(String parent, String target, String task) {
                collector.add(ShowcaseEvent.delegate(parent, target, task));
            }

            @Override
            public void onToolResult(String agentId, String toolName, String result) {
                collector.add(ShowcaseEvent.tool(agentId, toolName, truncate(result, 80)));
            }
        };
    }

    private static Map<String, Object> contextSnapshot(SwarmContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : List.of("user_id", "user_name", "vip_level", "total_spent",
                "session_id", "trace_id", "entered_at", "order_status")) {
            Object value = context.get(key);
            if (value != null) {
                snapshot.put(key, value);
            }
        }
        return snapshot;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    public record ChatResult(
            String reply,
            String currentAgent,
            List<Map<String, String>> events,
            Map<String, Object> context) {
    }
}
