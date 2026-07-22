package com.jswarm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SwarmContext {

    private static final ThreadLocal<SwarmContext> CURRENT = new ThreadLocal<>();

    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    public SwarmContext() {}

    public SwarmContext(Map<String, ?> initialVariables) {
        if (initialVariables != null) {
            initialVariables.forEach((k, v) -> {
                if (v != null) {
                    this.variables.put(k, v);
                }
            });
        }
    }

    /**
     * @deprecated Prefer {@link com.jswarm.spi.run.RunScope#current()} via {@link com.jswarm.spi.bridge.SwarmContextBridge}.
     */
    @Deprecated
    public static SwarmContext current() {
        return CURRENT.get();
    }

    public static void set(SwarmContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public SwarmContext put(String key, Object value) {
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
        return this;
    }

    public Object get(String key) {
        return variables.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new IllegalArgumentException("Value associated with key '" + key + "' is not of type: " + type.getName());
    }

    public boolean contains(String key) {
        return variables.containsKey(key);
    }

    public SwarmContext remove(String key) {
        variables.remove(key);
        return this;
    }

    public Map<String, Object> asMap() {
        return new ConcurrentHashMap<>(variables);
    }

    public String resolve(String template) {
        if (template == null || !template.contains("{")) {
            return template;
        }
        List<Map.Entry<String, Object>> sorted = new ArrayList<>(variables.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        String result = template;
        for (Map.Entry<String, Object> entry : sorted) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
