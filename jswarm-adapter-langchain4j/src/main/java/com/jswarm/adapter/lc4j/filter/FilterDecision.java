package com.jswarm.adapter.lc4j.filter;

public sealed interface FilterDecision {

    static FilterDecision handoff(String targetAgentId) {
        return new Handoff(targetAgentId);
    }

    static FilterDecision delegate(String targetAgentId, String task) {
        return new Delegate(targetAgentId, task);
    }

    static FilterDecision external() {
        return External.INSTANCE;
    }

    static FilterDecision reject(String reason, String modelSafeMessage) {
        return new Reject(reason, modelSafeMessage);
    }

    record Handoff(String targetAgentId) implements FilterDecision {}

    record Delegate(String targetAgentId, String task) implements FilterDecision {}

    record External() implements FilterDecision {
        static final External INSTANCE = new External();
    }

    record Reject(String reason, String modelSafeMessage) implements FilterDecision {}
}
