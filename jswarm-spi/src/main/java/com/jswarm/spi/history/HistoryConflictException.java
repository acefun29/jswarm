// 历史乐观锁冲突
package com.jswarm.spi.history;

public final class HistoryConflictException extends RuntimeException {

    public HistoryConflictException(String sessionId, long expectedVersion, long actualVersion) {
        super("History version conflict for session '" + sessionId
                + "': expected " + expectedVersion + ", actual " + actualVersion);
    }
}
