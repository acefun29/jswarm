// 编排工具的 Canonical 描述
package com.jswarm.runtime.tool;

import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.spi.message.ToolDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class OrchestrationTools {

    public static final String HANDOFF = "handoff";
    public static final String DELEGATE = "delegate";

    private OrchestrationTools() {
    }

    public static List<ToolDescriptor> forAgent(Swarm swarm, String agentId) {
        List<ToolDescriptor> tools = new ArrayList<>();
        Set<String> handoffTargets = swarm.getHandoffTargets(agentId);
        if (!handoffTargets.isEmpty()) {
            tools.add(new ToolDescriptor(HANDOFF,
                    "Hand off the conversation. Available agents: " + describe(swarm, handoffTargets),
                    "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"}},\"required\":[\"target\"]}"));
        }
        Set<String> delegateTargets = swarm.getDelegateTargets(agentId);
        if (!delegateTargets.isEmpty()) {
            tools.add(new ToolDescriptor(DELEGATE,
                    "Delegate a sub-task and receive its result. Available agents: " + describe(swarm, delegateTargets),
                    "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"},\"task\":{\"type\":\"string\"}},\"required\":[\"target\",\"task\"]}"));
        }
        return List.copyOf(tools);
    }

    public static boolean routing(String name) {
        return HANDOFF.equals(name) || DELEGATE.equals(name);
    }

    private static String describe(Swarm swarm, Set<String> targetIds) {
        return targetIds.stream()
                .map(swarm::getAgent)
                .map(OrchestrationTools::describe)
                .collect(Collectors.joining("; "));
    }

    private static String describe(Agent agent) {
        return agent.id() + " (" + agent.description() + ")";
    }
}
