// 不可变上下文快照
package com.jswarm.spi.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ContextSnapshot {

    private final Map<String, Object> values;
    private final Map<String, Sensitivity> sensitivities;

    private ContextSnapshot(Map<String, Object> values, Map<String, Sensitivity> sensitivities) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.sensitivities = Collections.unmodifiableMap(new LinkedHashMap<>(sensitivities));
    }

    public static ContextSnapshot empty() {
        return new ContextSnapshot(Map.of(), Map.of());
    }

    public static ContextSnapshot fromMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return empty();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Sensitivity> sensitivities = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                values.put(entry.getKey(), entry.getValue());
                sensitivities.put(entry.getKey(), Sensitivity.PUBLIC);
            }
        }
        return new ContextSnapshot(values, sensitivities);
    }

    static ContextSnapshot fromEntries(Map<String, Object> values, Map<String, Sensitivity> sensitivities) {
        return new ContextSnapshot(values, sensitivities);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Object get(String key) {
        return values.get(key);
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public Sensitivity sensitivity(String key) {
        return sensitivities.getOrDefault(key, Sensitivity.PUBLIC);
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public Map<String, Sensitivity> sensitivities() {
        return sensitivities;
    }

    public ContextSnapshot withOverlay(Map<String, ?> overlay) {
        if (overlay == null || overlay.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(values);
        Map<String, Sensitivity> mergedSens = new LinkedHashMap<>(sensitivities);
        for (Map.Entry<String, ?> entry : overlay.entrySet()) {
            if (entry.getValue() == null) {
                merged.remove(entry.getKey());
                mergedSens.remove(entry.getKey());
            } else {
                merged.put(entry.getKey(), entry.getValue());
                mergedSens.putIfAbsent(entry.getKey(), Sensitivity.PUBLIC);
            }
        }
        return new ContextSnapshot(merged, mergedSens);
    }

    public String resolve(String template) {
        if (template == null || !template.contains("{")) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Sensitivity> sensitivities = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            return put(key, value, Sensitivity.PUBLIC);
        }

        public Builder put(String key, Object value, Sensitivity sensitivity) {
            Objects.requireNonNull(key, "key");
            if (value == null) {
                values.remove(key);
                sensitivities.remove(key);
            } else {
                values.put(key, value);
                sensitivities.put(key, sensitivity != null ? sensitivity : Sensitivity.PUBLIC);
            }
            return this;
        }

        public ContextSnapshot build() {
            return new ContextSnapshot(values, sensitivities);
        }
    }
}
