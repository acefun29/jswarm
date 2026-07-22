// 父运行标识符，根 run 使用 empty
package com.jswarm.spi.id;

import java.util.Objects;
import java.util.Optional;

public record ParentRunId(String value) {

    public static final ParentRunId ROOT = new ParentRunId(null);

    public ParentRunId {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("parentRunId must not be blank when present");
        }
    }

    public static ParentRunId of(RunId runId) {
        Objects.requireNonNull(runId, "runId");
        return new ParentRunId(runId.value());
    }

    public static ParentRunId of(String value) {
        return value == null ? ROOT : new ParentRunId(value);
    }

    public boolean isRoot() {
        return value == null;
    }

    public Optional<String> asOptional() {
        return Optional.ofNullable(value);
    }

    @Override
    public String toString() {
        return value == null ? "ROOT" : value;
    }
}
