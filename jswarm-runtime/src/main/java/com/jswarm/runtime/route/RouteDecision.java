// Canonical 路由决策
package com.jswarm.runtime.route;

public sealed interface RouteDecision {

    record Continue() implements RouteDecision {
    }

    record Handoff(String targetAgentId) implements RouteDecision {
    }

    record Delegate(String targetAgentId, String task) implements RouteDecision {
    }

    record Reject(String modelSafeMessage) implements RouteDecision {
    }
}
