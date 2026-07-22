// Canonical 历史存储契约
package com.jswarm.spi.history;

import java.util.Optional;

public interface HistoryStore {

    Optional<HistoryRecord> load(String sessionId);

    HistoryRecord save(String sessionId, HistoryRecord record, long expectedVersion);

    default HistoryRecord create(String sessionId, HistoryRecord record) {
        return save(sessionId, record, 0);
    }
}
