// 并发安全的历史存储测试实现
package com.jswarm.runtime.history;

import com.jswarm.spi.history.HistoryConflictException;
import com.jswarm.spi.history.HistoryRecord;
import com.jswarm.spi.history.HistoryStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryHistoryStore implements HistoryStore {

    private final Map<String, HistoryRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<HistoryRecord> load(String sessionId) {
        return Optional.ofNullable(records.get(sessionId));
    }

    @Override
    public synchronized HistoryRecord save(String sessionId, HistoryRecord record, long expectedVersion) {
        HistoryRecord current = records.get(sessionId);
        long actualVersion = current != null ? current.version() : 0;
        if (actualVersion != expectedVersion) {
            throw new HistoryConflictException(sessionId, expectedVersion, actualVersion);
        }
        HistoryRecord saved = new HistoryRecord(
                record.schemaVersion(), record.runId(), record.swarmVersion(), record.tenantId(),
                actualVersion + 1, record.checksum(), record.messages(), record.state(), record.metadata());
        records.put(sessionId, saved);
        return saved;
    }
}
