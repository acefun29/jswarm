package com.jswarm.adapter.lc4j.filter;

public sealed interface FilterDecision {

    static FilterDecision handoff(String targetAgentId) {
        return new Handoff(targetAgentId);
    }

    static FilterDecision delegate(String targetAgentId, String task) {
        return new Delegate(targetAgentId, task);
    }

    record Handoff(String targetAgentId) implements FilterDecision {}

    record Delegate(String targetAgentId, String task) implements FilterDecision {}
}
