// Showcase 编排轨迹事件
package com.jswarm.examples.showcase;

import java.util.LinkedHashMap;
import java.util.Map;

public record ShowcaseEvent(
        String type,
        String agent,
        String from,
        String to,
        String task,
        String detail) {

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", type);
        if (agent != null) m.put("agent", agent);
        if (from != null) m.put("from", from);
        if (to != null) m.put("to", to);
        if (task != null) m.put("task", task);
        if (detail != null) m.put("detail", detail);
        return m;
    }

    public static ShowcaseEvent onEnter(String agent, String detail) {
        return new ShowcaseEvent("onEnter", agent, null, null, null, detail);
    }

    public static ShowcaseEvent onExit(String agent) {
        return new ShowcaseEvent("onExit", agent, null, null, null, null);
    }

    public static ShowcaseEvent handoff(String from, String to) {
        return new ShowcaseEvent("handoff", null, from, to, null, null);
    }

    public static ShowcaseEvent delegate(String from, String to, String task) {
        return new ShowcaseEvent("delegate", null, from, to, task, null);
    }

    public static ShowcaseEvent tool(String agent, String toolName, String detail) {
        return new ShowcaseEvent("tool", agent, null, null, null, toolName + (detail != null ? ": " + detail : ""));
    }

    public static ShowcaseEvent context(String key, String value) {
        return new ShowcaseEvent("context", null, null, null, null, key + "=" + value);
    }
}
