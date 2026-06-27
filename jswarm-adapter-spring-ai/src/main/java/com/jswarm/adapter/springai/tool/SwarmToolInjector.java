package com.jswarm.adapter.springai.tool;

import com.jswarm.core.Swarm;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SwarmToolInjector {

    private SwarmToolInjector() {
    }

    public static List<ToolCallback> generateTools(Swarm swarm, String agentId,
                                                    List<ToolCallback> agentTools) {
        List<ToolCallback> tools = new ArrayList<>(agentTools);

        Set<String> handoffTargets = swarm.getHandoffTargets(agentId);
        if (!handoffTargets.isEmpty()) {
            tools.add(buildHandoffCallback(swarm, handoffTargets));
        }

        Set<String> delegateTargets = swarm.getDelegateTargets(agentId);
        if (!delegateTargets.isEmpty()) {
            tools.add(buildDelegateCallback(swarm, delegateTargets));
        }

        return tools;
    }

    public static List<ToolCallback> generateExternalToolsOnly(List<ToolCallback> agentTools) {
        return new ArrayList<>(agentTools);
    }

    static ToolCallback buildHandoffCallback(Swarm swarm, Set<String> targetIds) {
        String agentsDesc = targetIds.stream()
                .map(id -> {
                    var agent = swarm.getAgent(id);
                    return id + " (" + agent.description() + ")";
                })
                .collect(Collectors.joining("; "));

        String description = "Hand off the conversation to another agent. Available agents: " + agentsDesc;

        String inputSchema = """
                {
                    "type": "object",
                    "properties": {
                        "target": {
                            "type": "string",
                            "description": "The id of the agent that should take over the conversation."
                        }
                    },
                    "required": ["target"]
                }
                """;

        ToolDefinition def = ToolDefinition.builder()
                .name("handoff")
                .description(description)
                .inputSchema(inputSchema)
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return def;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException(
                        "handoff must be intercepted by SwarmFilter before tool execution");
            }
        };
    }

    static ToolCallback buildDelegateCallback(Swarm swarm, Set<String> targetIds) {
        String agentsDesc = targetIds.stream()
                .map(id -> {
                    var agent = swarm.getAgent(id);
                    return id + " (" + agent.description() + ")";
                })
                .collect(Collectors.joining("; "));

        String description = "Delegate a sub-task to another agent. The agent will work independently and return the result. Available agents: " + agentsDesc;

        String inputSchema = """
                {
                    "type": "object",
                    "properties": {
                        "target": {
                            "type": "string",
                            "description": "The id of the agent that should handle the sub-task."
                        },
                        "task": {
                            "type": "string",
                            "description": "The task description for the delegated agent."
                        }
                    },
                    "required": ["target", "task"]
                }
                """;

        ToolDefinition def = ToolDefinition.builder()
                .name("delegate")
                .description(description)
                .inputSchema(inputSchema)
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return def;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException(
                        "delegate must be intercepted by SwarmFilter before tool execution");
            }
        };
    }
}
