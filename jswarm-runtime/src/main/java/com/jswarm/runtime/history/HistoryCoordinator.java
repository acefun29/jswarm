// HistoryStore 与共享 Runtime 的恢复协调
package com.jswarm.runtime.history;

import com.jswarm.runtime.run.RunResult;
import com.jswarm.spi.history.HistoryChecksum;
import com.jswarm.spi.history.HistoryRecord;
import com.jswarm.spi.history.HistoryStore;
import com.jswarm.spi.id.RunId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HistoryCoordinator {

    private HistoryCoordinator() {
    }

    public static List<CanonicalMessage> load(
            HistoryStore store, String sessionId, SwarmVersion swarmVersion, String tenantId) {
        Objects.requireNonNull(store, "store");
        HistoryRecord record = store.load(sessionId).orElse(null);
        if (record == null) {
            return List.of();
        }
        if (!record.swarmVersion().equals(swarmVersion)
                || !Objects.equals(record.tenantId(), tenantId)) {
            throw new IllegalArgumentException("History scope does not match current run");
        }
        if (!record.checksum().equals(HistoryChecksum.sha256(record.messages()))) {
            throw new IllegalArgumentException("History checksum mismatch");
        }
        return record.messages();
    }

    public static HistoryRecord save(
            HistoryStore store,
            String sessionId,
            HistoryRecord previous,
            SwarmVersion swarmVersion,
            String tenantId,
            RunResult result) {
        long expected = previous != null ? previous.version() : 0;
        HistoryRecord next = new HistoryRecord(
                1,
                result.runId() != null ? result.runId() : RunId.generate(),
                swarmVersion,
                tenantId,
                expected,
                HistoryChecksum.sha256(result.history()),
                result.history(),
                result.finalState().name(),
                Map.of("currentAgentId", result.currentAgentId(), "reply", result.reply()));
        return store.save(sessionId, next, expected);
    }
}
