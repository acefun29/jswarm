// 结构化运行错误
package com.jswarm.spi.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SwarmError(
        SwarmErrorCode code,
        String publicMessage,
        Map<String, String> metadata,
        Throwable cause) {

    public SwarmError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(publicMessage, "publicMessage");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ErrorCategory category() {
        return code.category();
    }

    public boolean retryable() {
        return code.retryable();
    }

    public SwarmErrorException toException() {
        return new SwarmErrorException(this);
    }

    public static SwarmError of(SwarmErrorCode code, String publicMessage) {
        return new SwarmError(code, publicMessage, Map.of(), null);
    }

    public static SwarmError of(SwarmErrorCode code, String publicMessage, Throwable cause) {
        return new SwarmError(code, publicMessage, Map.of(), cause);
    }

    public static SwarmError of(SwarmErrorCode code, String publicMessage, Map<String, String> metadata, Throwable cause) {
        return new SwarmError(code, publicMessage, metadata, cause);
    }

    public SwarmError withMetadata(String key, String value) {
        Map<String, String> merged = new LinkedHashMap<>(metadata);
        merged.put(key, value);
        return new SwarmError(code, publicMessage, Collections.unmodifiableMap(merged), cause);
    }

    public Map<String, String> eventPayload() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("code", code.name());
        payload.put("category", category().name());
        payload.put("retryable", Boolean.toString(retryable()));
        payload.put("message", publicMessage);
        payload.putAll(metadata);
        return Collections.unmodifiableMap(payload);
    }
}
