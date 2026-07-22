// SPI 层不可变运行结果
package com.jswarm.spi.run;

import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.RunId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SpiRunResult(
        RunId runId,
        AgentId finalAgentId,
        String reply,
        Map<String, String> metadata) {

    public SpiRunResult {
        Objects.requireNonNull(runId, "runId");
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static SpiRunResult of(RunId runId, AgentId finalAgentId, String reply) {
        return new SpiRunResult(runId, finalAgentId, reply, Map.of());
    }
}
