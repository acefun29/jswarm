// Canonical 历史记录
package com.jswarm.spi.history;

import com.jswarm.spi.id.RunId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record HistoryRecord(
        int schemaVersion,
        RunId runId,
        SwarmVersion swarmVersion,
        String tenantId,
        long version,
        String checksum,
        List<CanonicalMessage> messages,
        String state,
        Map<String, String> metadata) {

    public HistoryRecord {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(swarmVersion, "swarmVersion");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("checksum must not be blank");
        }
        messages = messages != null ? List.copyOf(messages) : List.of();
        state = state != null ? state : "UNKNOWN";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
