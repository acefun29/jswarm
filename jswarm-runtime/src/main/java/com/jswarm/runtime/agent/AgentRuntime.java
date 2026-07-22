// Agent 的 provider-neutral 运行能力
package com.jswarm.runtime.agent;

import com.jswarm.spi.lifecycle.ModelGateway;
import com.jswarm.spi.lifecycle.ToolInvoker;
import com.jswarm.spi.message.ToolDescriptor;

import java.util.List;
import java.util.Objects;

public final class AgentRuntime {

    private final String agentId;
    private final ModelGateway modelGateway;
    private final ToolInvoker toolInvoker;
    private final List<ToolDescriptor> tools;
    private final boolean streamingSupported;
    private final boolean instructionsConfigured;

    public AgentRuntime(
            String agentId,
            ModelGateway modelGateway,
            ToolInvoker toolInvoker,
            List<ToolDescriptor> tools,
            boolean streamingSupported,
            boolean instructionsConfigured) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        this.agentId = agentId;
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
        this.toolInvoker = toolInvoker;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
        this.streamingSupported = streamingSupported;
        this.instructionsConfigured = instructionsConfigured;
    }

    public String agentId() {
        return agentId;
    }

    public ModelGateway modelGateway() {
        return modelGateway;
    }

    public ToolInvoker toolInvoker() {
        return toolInvoker;
    }

    public List<ToolDescriptor> tools() {
        return tools;
    }

    public boolean streamingSupported() {
        return streamingSupported;
    }

    public boolean instructionsConfigured() {
        return instructionsConfigured;
    }
}
