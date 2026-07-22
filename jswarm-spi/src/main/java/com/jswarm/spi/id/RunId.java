// 强类型运行标识符
package com.jswarm.spi.id;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public record RunId(String value) {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public RunId {
        Objects.requireNonNull(value, "runId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
    }

    public static RunId generate() {
        return new RunId(Long.toHexString(SEQUENCE.incrementAndGet()) + "-" + java.util.UUID.randomUUID());
    }

    public static RunId of(String value) {
        return new RunId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
