package com.jswarm.core;

import java.util.*;

public final class SwarmBuilder {

    private final String swarmId;
    private String entryAgentId;
    private final Map<String, Agent> agents = new LinkedHashMap<>();
    private final Map<String, Set<String>> handoffTargets = new HashMap<>();
    private final Map<String, Set<String>> delegateTargets = new HashMap<>();

    SwarmBuilder(String id) {
        if (id == null || id.isBlank()) {
            throw new SwarmException("Swarm id must not be blank");
        }
        this.swarmId = id;
    }

    public SwarmBuilder agent(Agent agent) {
        if (agent == null) throw new SwarmException("Agent must not be null");
        if (agents.containsKey(agent.id())) {
            throw new SwarmException("Duplicate agent id: " + agent.id());
        }
        agents.put(agent.id(), agent);
        return this;
    }

    public SwarmBuilder entry(String agentId) {
        this.entryAgentId = agentId;
        return this;
    }

    public SwarmBuilder handoff(String fromAgentId, String... toAgentIds) {
        if (toAgentIds.length == 0) {
            throw new SwarmException("handoff must have at least one target");
        }
        handoffTargets.computeIfAbsent(fromAgentId, k -> new LinkedHashSet<>())
                      .addAll(Arrays.asList(toAgentIds));
        return this;
    }

    public SwarmBuilder delegate(String fromAgentId, String... toAgentIds) {
        if (toAgentIds.length == 0) {
            throw new SwarmException("delegate must have at least one target");
        }
        delegateTargets.computeIfAbsent(fromAgentId, k -> new LinkedHashSet<>())
                       .addAll(Arrays.asList(toAgentIds));
        return this;
    }

    public Swarm build() {
        if (agents.isEmpty()) {
            throw new SwarmException("Swarm must have at least one agent");
        }

        if (entryAgentId == null || entryAgentId.isBlank()) {
            throw new SwarmException("Entry agent id must be set");
        }

        if (!agents.containsKey(entryAgentId)) {
            throw new SwarmException("Entry agent '" + entryAgentId + "' not found in registered agents");
        }

        validateTargets(handoffTargets, "handoff");
        validateTargets(delegateTargets, "delegate");

        return new Swarm(swarmId, entryAgentId, agents, handoffTargets, delegateTargets);
    }

    private void validateTargets(Map<String, Set<String>> targets, String type) {
        for (var entry : targets.entrySet()) {
            String from = entry.getKey();
            if (!agents.containsKey(from)) {
                throw new SwarmException(type + " source agent '" + from + "' not found");
            }
            for (String to : entry.getValue()) {
                if (!agents.containsKey(to)) {
                    throw new SwarmException(type + " target agent '" + to + "' not found (from '" + from + "')");
                }
            }
        }
    }
}
