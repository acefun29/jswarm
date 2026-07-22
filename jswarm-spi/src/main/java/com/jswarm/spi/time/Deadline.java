// 绝对截止时间
package com.jswarm.spi.time;

import com.jswarm.spi.error.SwarmError;
import com.jswarm.spi.error.SwarmErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class Deadline {

    private final Instant expiresAt;
    private final Clock clock;

    private Deadline(Instant expiresAt, Clock clock) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public static Deadline fromNow(Duration timeout) {
        return fromNow(timeout, Clock.systemUTC());
    }

    public static Deadline fromNow(Duration timeout, Clock clock) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Clock c = clock != null ? clock : Clock.systemUTC();
        return new Deadline(c.instant().plus(timeout), c);
    }

    public static Deadline at(Instant expiresAt) {
        return new Deadline(expiresAt, Clock.systemUTC());
    }

    public static Deadline at(Instant expiresAt, Clock clock) {
        return new Deadline(expiresAt, clock != null ? clock : Clock.systemUTC());
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return !clock.instant().isBefore(expiresAt);
    }

    public Duration remaining() {
        Duration d = Duration.between(clock.instant(), expiresAt);
        return d.isNegative() ? Duration.ZERO : d;
    }

    public void check() {
        if (isExpired()) {
            throw SwarmError.of(SwarmErrorCode.MODEL_TIMEOUT, "Run deadline exceeded")
                    .toException();
        }
    }

    public Duration effectiveTimeout(Duration perCallTimeout) {
        Duration remaining = remaining();
        if (perCallTimeout == null) {
            return remaining;
        }
        if (remaining.isZero()) {
            return Duration.ZERO;
        }
        return remaining.compareTo(perCallTimeout) < 0 ? remaining : perCallTimeout;
    }
}
