package com.jswarm.adapter.lc4j.tool;

import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SwarmToolInjector {

    private SwarmToolInjector() {
    }

    public static List<ToolSpecification> generateTools(Swarm swarm, String agentId) {
        return generateTools(swarm, agentId, List.of());
    }

    public static List<ToolSpecification> generateTools(Swarm swarm, String agentId, List<ToolSpecification> userTools) {
        List<ToolSpecification> tools = new ArrayList<>(userTools);

        Set<String> handoffTargets = swarm.getHandoffTargets(agentId);
        if (!handoffTargets.isEmpty()) {
            tools.add(buildHandoffTool(swarm, handoffTargets));
        }

        Set<String> delegateTargets = swarm.getDelegateTargets(agentId);
        if (!delegateTargets.isEmpty()) {
            tools.add(buildDelegateTool(swarm, delegateTargets));
        }

        return tools;
    }

    public static List<ToolSpecification> generateExternalToolsOnly(List<ToolSpecification> userTools) {
        return new ArrayList<>(userTools);
    }

    private static ToolSpecification buildHandoffTool(Swarm swarm, Set<String> targetIds) {
        StringBuilder sb = new StringBuilder("Handoff the conversation to another agent.\n\n");
        sb.append("Use this tool only when the target agent should fully take over the user conversation.\n");
        sb.append("After handoff, you will not receive control back.\n");
        sb.append("The conversation history will be preserved, but the system prompt will be replaced with the target agent's instructions.\n");
        sb.append("Do not use this tool if you need the target agent's result and then want to continue responding yourself. Use delegate instead.\n\n");
        sb.append("Available targets:\n");
        for (String id : targetIds) {
            Agent a = swarm.getAgent(id);
            sb.append("- ").append(id).append(": ").append(a.description()).append("\n");
        }
        String exampleTarget = targetIds.iterator().next();
        sb.append("\nArguments:\n");
        sb.append("- target: The id of the agent that should take over the conversation.\n\n");
        sb.append("Example:\n");
        sb.append("{\"target\":\"").append(exampleTarget).append("\"}");
        return ToolSpecification.builder()
                .name("handoff")
                .description(sb.toString())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("target", "The id of the agent that should take over the conversation.")
                        .required(List.of("target"))
                        .build())
                .build();
    }

    private static ToolSpecification buildDelegateTool(Swarm swarm, Set<String> targetIds) {
        StringBuilder sb = new StringBuilder("Delegate a sub-task to another agent and receive the result back.\n\n");
        sb.append("Use this tool when you need another agent to complete a specific sub-task, and you want to continue the conversation after receiving the result.\n");
        sb.append("The delegated agent does not take over the user conversation.\n");
        sb.append("Nested delegation is not allowed.\n");
        sb.append("Handoff is not allowed inside a delegated task.\n");
        sb.append("Write a clear and self-contained task for the target agent.\n\n");
        sb.append("Available targets:\n");
        for (String id : targetIds) {
            Agent a = swarm.getAgent(id);
            sb.append("- ").append(id).append(": ").append(a.description()).append("\n");
        }
        String exampleTarget = targetIds.iterator().next();
        sb.append("\nArguments:\n");
        sb.append("- target: The id of the agent that should perform the delegated sub-task.\n");
        sb.append("- task: A clear and self-contained task for the target agent.\n\n");
        sb.append("Example:\n");
        sb.append("{\"target\":\"").append(exampleTarget)
                .append("\",\"task\":\"Analyze last month's sales trend and return a concise summary with key numbers.\"}");
        return ToolSpecification.builder()
                .name("delegate")
                .description(sb.toString())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("target", "The id of the agent that should perform the delegated sub-task.")
                        .addStringProperty("task", "A clear and self-contained task for the target agent.")
                        .required(List.of("target", "task"))
                        .build())
                .build();
    }
}
