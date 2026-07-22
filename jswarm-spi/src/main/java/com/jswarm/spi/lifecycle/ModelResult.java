// 模型调用结果
package com.jswarm.spi.lifecycle;

import com.jswarm.spi.message.CanonicalMessage;

import java.util.Map;

public record ModelResult(CanonicalMessage message, Map<String, Object> metadata) {

    public ModelResult {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public ModelResult(CanonicalMessage message) {
        this(message, Map.of());
    }
}
