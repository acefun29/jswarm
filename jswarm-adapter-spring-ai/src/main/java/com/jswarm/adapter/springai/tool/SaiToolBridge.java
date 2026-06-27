package com.jswarm.adapter.springai.tool;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.ToolNotHandledException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SaiToolBridge {

    private SaiToolBridge() {
    }

    public static BridgeResult bridge(Object... toolBeans) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Object bean : toolBeans) {
            if (bean instanceof ToolCallback tc) {
                callbacks.add(tc);
            } else if (bean instanceof ToolCallbackProvider tcp) {
                Collections.addAll(callbacks, tcp.getToolCallbacks());
            } else {
                ToolCallback[] fromBean = ToolCallbacks.from(bean);
                Collections.addAll(callbacks, fromBean);
            }
        }

        Set<String> names = new HashSet<>();
        for (ToolCallback tc : callbacks) {
            String name = tc.getToolDefinition().name();
            if ("handoff".equals(name) || "delegate".equals(name)) {
                throw new IllegalArgumentException(
                        "User tools must not use reserved name '" + name + "'");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("Duplicate tool name: " + name);
            }
        }

        ExternalToolExecutor executor = toolCall -> {
            for (ToolCallback tc : callbacks) {
                if (tc.getToolDefinition().name().equals(toolCall.name())) {
                    return tc.call(toolCall.arguments());
                }
            }
            throw new ToolNotHandledException(toolCall.name());
        };

        return new BridgeResult(callbacks, executor);
    }

    public record BridgeResult(List<ToolCallback> callbacks, ExternalToolExecutor executor) {
    }
}
