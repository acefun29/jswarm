// LangChain4j Agent 运行能力适配
package com.jswarm.adapter.lc4j.runtime;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.adapter.lc4j.tool.ToolExecutionMerger;
import com.jswarm.core.Agent;
import com.jswarm.core.SwarmEvent;
import com.jswarm.runtime.agent.AgentRuntime;
import com.jswarm.runtime.agent.RuntimeProvider;
import com.jswarm.spi.lifecycle.ToolResult;
import com.jswarm.spi.message.ToolDescriptor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.function.Consumer;

public final class Lc4jRuntimeProvider implements RuntimeProvider {

    private final SwarmRunOptions options;
    private final ExternalToolExecutor swarmToolExecutor;
    private final Consumer<SwarmEvent> streamingSink;
    private final Lc4jToolCodec toolCodec = new Lc4jToolCodec();

    public Lc4jRuntimeProvider(
            SwarmRunOptions options,
            ExternalToolExecutor swarmToolExecutor,
            Consumer<SwarmEvent> streamingSink) {
        this.options = options;
        this.swarmToolExecutor = swarmToolExecutor;
        this.streamingSink = streamingSink;
    }

    @Override
    public AgentRuntime resolve(Agent agent) {
        if (!(agent instanceof JAgent runtimeAgent)) {
            return null;
        }
        List<ToolDescriptor> tools = runtimeAgent.externalTools().stream().map(toolCodec::decode).toList();
        ExternalToolExecutor executor = ToolExecutionMerger.merge(
                runtimeAgent.toolExecutor(), swarmToolExecutor);
        return new AgentRuntime(
                agent.id(),
                new Lc4jModelGateway(runtimeAgent, options.modelTimeout(), streamingSink),
                executor != null ? (invocation, context) -> new ToolResult(executor.execute(
                        ToolExecutionRequest.builder()
                                .id(invocation.callId())
                                .name(invocation.toolName())
                                .arguments(invocation.arguments())
                                .build())) : null,
                tools,
                true,
                instructionsConfigured(agent));
    }

    private static boolean instructionsConfigured(Agent agent) {
        try {
            String instructions = agent.instructions();
            return instructions != null && !instructions.isBlank();
        } catch (IllegalStateException dynamicContextRequired) {
            return true;
        }
    }
}
