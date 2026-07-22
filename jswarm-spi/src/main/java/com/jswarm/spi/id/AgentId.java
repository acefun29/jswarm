// 强类型 Agent 标识符
package com.jswarm.spi.id;

import com.jswarm.core.ProtocolLimits;

import java.util.Objects;

public record AgentId(String value) {

    public AgentId {
        Objects.requireNonNull(value, "agentId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (value.length() > ProtocolLimits.MAX_TARGET_LENGTH) {
            throw new IllegalArgumentException("agentId exceeds maximum length");
        }
    }

    public static AgentId of(String value) {
        return new AgentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
