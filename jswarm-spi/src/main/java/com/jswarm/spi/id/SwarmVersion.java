// Swarm 拓扑版本戳
package com.jswarm.spi.id;

import java.util.Objects;

public record SwarmVersion(String swarmId, String version) {

    public SwarmVersion {
        Objects.requireNonNull(swarmId, "swarmId");
        if (swarmId.isBlank()) {
            throw new IllegalArgumentException("swarmId must not be blank");
        }
        if (version == null || version.isBlank()) {
            version = "1";
        }
    }

    public static SwarmVersion of(String swarmId) {
        return new SwarmVersion(swarmId, "1");
    }

    public static SwarmVersion of(String swarmId, String version) {
        return new SwarmVersion(swarmId, version);
    }

    @Override
    public String toString() {
        return swarmId + "@" + version;
    }
}
