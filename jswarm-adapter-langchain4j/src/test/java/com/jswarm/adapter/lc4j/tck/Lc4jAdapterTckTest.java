// LangChain4j Adapter 共享 TCK
package com.jswarm.adapter.lc4j.tck;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.runtime.Lc4jMessageCodec;
import com.jswarm.adapter.lc4j.run.SwarmRunListener;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.adapter.lc4j.run.SwarmRunner;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmBuilder;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.tck.AdapterCompatibilityTck;
import com.jswarm.tck.AdapterHarness;
import com.jswarm.tck.TckAgent;
import com.jswarm.tck.TckFixture;
import com.jswarm.tck.TckOutcome;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.time.Duration;

final class Lc4jAdapterTckTest extends AdapterCompatibilityTck {

    @Override
    protected AdapterHarness harness() {
        return this::execute;
    }

    private TckOutcome execute(TckFixture fixture) {
        Lc4jMessageCodec codec = new Lc4jMessageCodec();
        SwarmBuilder builder = Swarm.create("tck");
        for (TckAgent specification : fixture.agents()) {
            builder.agent(new ScriptedAgent(specification, codec));
        }
        builder.entry(fixture.entryAgentId());
        fixture.handoffs().forEach((source, targets) ->
                builder.handoff(source, targets.toArray(String[]::new)));
        fixture.delegates().forEach((source, targets) ->
                builder.delegate(source, targets.toArray(String[]::new)));
        Swarm swarm = builder.build();
        List<String> events = new ArrayList<>();
        SwarmRunListener listener = listener(events);
        SwarmRunner runner = SwarmRunner.create(
                swarm,
                SwarmRunOptions.builder()
                        .maxTurns(fixture.maxTurns())
                        .modelTimeout(Duration.ofMillis(fixture.modelTimeoutMillis()))
                        .build(),
                (ExternalToolExecutor) null,
                listener);
        SwarmContext context = fixture.nullContext() ? null : new SwarmContext(fixture.context());
        try {
            SwarmRunner.RunResult result = runner.runWithHistory(
                    fixture.userMessage(), null, fixture.entryAgentId(), context, false);
            return new TckOutcome(
                    result.reply(), result.currentAgentId(), codec.decode(result.updatedHistory()), events, null);
        } catch (SwarmErrorException error) {
            return new TckOutcome(null, null, List.of(), events, error.code());
        } catch (RuntimeException error) {
            return new TckOutcome(null, null, List.of(), events, SwarmErrorCode.INTERNAL);
        }
    }

    private static SwarmRunListener listener(List<String> events) {
        return new SwarmRunListener() {
            @Override
            public void onAgentEnter(String agentId, String source) {
                events.add("AGENT_ENTERED");
            }

            @Override
            public void onAgentExit(String agentId) {
                events.add("AGENT_EXITED");
            }

            @Override
            public void onToolCall(String agentId, String toolName, String args) {
                events.add("TOOL_CALLED");
            }

            @Override
            public void onToolResult(String agentId, String toolName, String result) {
                events.add("TOOL_RESULT");
            }

            @Override
            public void onHandoff(String from, String to) {
                events.add("HANDOFF");
            }

            @Override
            public void onDelegateStart(String parent, String target, String task) {
                events.add("DELEGATE_STARTED");
            }

            @Override
            public void onDelegateEnd(String parent, String target) {
                events.add("DELEGATE_COMPLETED");
            }

            @Override
            public void onRecovery(String agentId, String reason) {
                events.add("RECOVERY");
            }

            @Override
            public void onRunComplete(String finalText) {
                events.add("COMPLETED");
            }

            @Override
            public void onRunFail(String agentId, String error) {
                events.add("FAILED");
            }
        };
    }

    private static final class ScriptedAgent implements JAgent {
        private final TckAgent specification;
        private final ChatModel model;
        private final List<ToolSpecification> tools;
        private final ExternalToolExecutor executor;

        private ScriptedAgent(TckAgent specification, Lc4jMessageCodec codec) {
            this.specification = specification;
            Deque<CanonicalMessage> responses = new ArrayDeque<>(specification.responses());
            this.model = new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest request) {
                    delay(specification.modelDelayMillis());
                    AiMessage response = (AiMessage) codec.encode(
                            List.of(responses.removeFirst())).get(0);
                    return ChatResponse.builder().aiMessage(response).build();
                }
            };
            this.tools = specification.toolResults().keySet().stream()
                    .map(name -> ToolSpecification.builder()
                            .name(name)
                            .description(name)
                            .parameters(JsonObjectSchema.builder().build())
                            .build())
                    .toList();
            this.executor = request -> requiredToolResult(specification.toolResults(), request.name());
        }

        @Override
        public String id() {
            return specification.id();
        }

        @Override
        public String name() {
            return specification.id();
        }

        @Override
        public String description() {
            return specification.id() + " description";
        }

        @Override
        public String instructions() {
            return specification.instructions();
        }

        @Override
        public ChatModel model() {
            return model;
        }

        @Override
        public List<ToolSpecification> externalTools() {
            return tools;
        }

        @Override
        public ExternalToolExecutor toolExecutor() {
            return executor;
        }

        @Override
        public void onExit(SwarmContext context) {
            if (specification.failExit()) {
                throw new IllegalStateException("exit failed");
            }
        }
    }

    private static void delay(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static String requiredToolResult(Map<String, String> results, String name) {
        String result = results.get(name);
        if (result == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return result;
    }
}
