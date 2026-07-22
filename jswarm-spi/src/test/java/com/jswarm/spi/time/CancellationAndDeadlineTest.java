package com.jswarm.spi.time;

import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CancellationAndDeadlineTest {

    @Test
    void deadlineShouldExpire() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        Deadline deadline = Deadline.fromNow(Duration.ofSeconds(10), clock);
        assertFalse(deadline.isExpired());
        Clock later = Clock.fixed(Instant.parse("2026-01-01T00:01:00Z"), ZoneOffset.UTC);
        Deadline expired = Deadline.at(deadline.expiresAt(), later);
        assertTrue(expired.isExpired());
        SwarmErrorException ex = assertThrows(SwarmErrorException.class, expired::check);
        assertEquals(SwarmErrorCode.MODEL_TIMEOUT, ex.code());
    }

    @Test
    void cancellationShouldBeIdempotent() {
        CancellationToken token = new CancellationToken();
        assertTrue(token.cancel(CancellationReason.USER_REQUEST));
        assertFalse(token.cancel(CancellationReason.INTERNAL));
        assertTrue(token.isCancelled());
        SwarmErrorException ex = assertThrows(SwarmErrorException.class, token::throwIfCancelled);
        assertEquals(SwarmErrorCode.CANCELLED, ex.code());
    }

    @Test
    void terminalGuardShouldRejectAfterTerminal() {
        TerminalGuard guard = new TerminalGuard();
        assertTrue(guard.markTerminal());
        assertFalse(guard.markTerminal());
        assertThrows(SwarmErrorException.class, guard::checkActive);
    }
}
