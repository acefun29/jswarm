// Spring AI Agent 运行能力适配
package com.jswarm.adapter.springai.runtime;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.adapter.springai.tool.ToolExecutionMerger;
import com.jswarm.core.Agent;
import com.jswarm.core.SwarmEvent;
import com.jswarm.runtime.agent.AgentRuntime;
import com.jswarm.runtime.agent.RuntimeProvider;
import com.jswarm.spi.lifecycle.ToolResult;
import com.jswarm.spi.message.ToolDescriptor;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.function.Consumer;

public final class SpringAiRuntimeProvider implements RuntimeProvider {

    private final SwarmRunOptions options;
    private final ExternalToolExecutor swarmToolExecutor;
    private final Consumer<SwarmEvent> streamingSink;
    private final SpringAiToolCodec toolCodec = new SpringAiToolCodec();

    public SpringAiRuntimeProvider(
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
                new SpringAiModelGateway(runtimeAgent, options.modelTimeout(), options.advisors(), streamingSink),
                executor != null ? (invocation, context) -> new ToolResult(executor.execute(
                        new AssistantMessage.ToolCall(
                                invocation.callId(), "function", invocation.toolName(), invocation.arguments()))) : null,
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
