package com.jswarm.core;

import java.util.*;

public final class Swarm {

    private final String id;
    private final String entryAgentId;
    private final Map<String, Agent> agents;
    private final Map<String, Set<String>> handoffTargets;
    private final Map<String, Set<String>> delegateTargets;

    Swarm(String id,
          String entryAgentId,
          Map<String, Agent> agents,
          Map<String, Set<String>> handoffTargets,
          Map<String, Set<String>> delegateTargets) {
        this.id = id;
        this.entryAgentId = entryAgentId;
        this.agents = Collections.unmodifiableMap(new LinkedHashMap<>(agents));
        this.handoffTargets = deepUnmodifiable(handoffTargets);
        this.delegateTargets = deepUnmodifiable(delegateTargets);
    }

    public String id() { return id; }

    public String entryAgentId() { return entryAgentId; }

    public Agent entryAgent() { return agents.get(entryAgentId); }

    public Agent getAgent(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            throw new SwarmException("Agent not found: " + agentId);
        }
        return agent;
    }

    public boolean contains(String agentId) {
        return agents.containsKey(agentId);
    }

    public Collection<Agent> listAgents() {
        return agents.values();
    }

    public Set<String> getHandoffTargets(String agentId) {
        return handoffTargets.getOrDefault(agentId, Set.of());
    }

    public Set<String> getDelegateTargets(String agentId) {
        return delegateTargets.getOrDefault(agentId, Set.of());
    }

    public static SwarmBuilder create(String id) {
        return new SwarmBuilder(id);
    }

    private static Map<String, Set<String>> deepUnmodifiable(Map<String, Set<String>> map) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }
}
