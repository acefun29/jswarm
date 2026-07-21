// 多轮会话引擎，委托 SwarmRunner 处理编排与 tool batch 协议
package com.jswarm.examples.showcase;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.run.ChatMessageCodec;
import com.jswarm.adapter.lc4j.run.SwarmRunListener;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.adapter.lc4j.run.SwarmRunner;
import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            SwarmRunner runner = createRunner(collector);
            List<ChatMessage> priorHistory = session.history().isEmpty()
                    ? null
                    : new ArrayList<>(ChatMessageCodec.forPersistence(session.history()));

            SwarmRunner.RunResult result = runner.runWithHistory(
                    userMessage,
                    priorHistory,
                    session.currentAgentId(),
                    session.context(),
                    session.entryHookFired());
            session.setEntryHookFired(true);

            Optional<String> forcedTarget = resolveForcedHandoff(result);
            if (forcedTarget.isPresent()) {
                result = applyForcedHandoff(result, forcedTarget.get(), runner, collector);
            }

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

    private SwarmRunner createRunner(ShowcaseEventCollector collector) {
        SwarmRunOptions options = SwarmRunOptions.builder()
                .maxTurns(12)
                .maxRecoveryAttempts(2)
                .build();
        return SwarmRunner.create(swarm, options, swarmToolExecutor, showcaseListener(collector));
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

    private Optional<String> resolveForcedHandoff(SwarmRunner.RunResult result) {
        if (!swarm.entryAgentId().equals(result.currentAgentId())) {
            return Optional.empty();
        }
        List<ChatMessage> history = result.updatedHistory();
        if (history.isEmpty()) {
            return Optional.empty();
        }
        ChatMessage last = history.get(history.size() - 1);
        if (!(last instanceof AiMessage ai) || ai.hasToolExecutionRequests()) {
            return Optional.empty();
        }
        String userText = history.stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second)
                .orElse("");
        Optional<String> target = ShowcaseIntentRouter.handoffTarget(userText);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        return swarm.getHandoffTargets(result.currentAgentId()).contains(target.get())
                ? target
                : Optional.empty();
    }

    private SwarmRunner.RunResult applyForcedHandoff(
            SwarmRunner.RunResult firstResult,
            String targetAgentId,
            SwarmRunner runner,
            ShowcaseEventCollector collector) {
        List<ChatMessage> messages = new ArrayList<>(firstResult.updatedHistory());
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof AiMessage) {
            messages.remove(messages.size() - 1);
        }
        String from = firstResult.currentAgentId();
        Agent to = swarm.getAgent(targetAgentId);
        to.onEnter(SwarmContext.current());
        collector.add(ShowcaseEvent.handoff(from, targetAgentId));
        String targetInstructions = SwarmContext.current().resolve(to.instructions());
        messages.set(0, SystemMessage.from(targetInstructions));
        return runner.continueWithMessages(messages, targetAgentId, SwarmContext.current(), true);
    }

    private static Map<String, Object> contextSnapshot(SwarmContext ctx) {
        Map<String, Object> snap = new LinkedHashMap<>();
        for (String key : List.of("user_id", "user_name", "vip_level", "total_spent",
                "session_id", "trace_id", "entered_at", "order_status")) {
            Object v = ctx.get(key);
            if (v != null) {
                snap.put(key, v);
            }
        }
        return snap;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public record ChatResult(
            String reply,
            String currentAgent,
            List<Map<String, String>> events,
            Map<String, Object> context) {
    }
}
