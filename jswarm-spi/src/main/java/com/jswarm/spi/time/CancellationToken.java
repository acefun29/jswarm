// 一次性取消信号
package com.jswarm.spi.time;

import com.jswarm.spi.error.SwarmError;
import com.jswarm.spi.error.SwarmErrorCode;

import java.util.concurrent.atomic.AtomicReference;

public final class CancellationToken {

    private final AtomicReference<CancellationReason> reason = new AtomicReference<>();

    public boolean isCancelled() {
        return reason.get() != null;
    }

    public CancellationReason cancellationReason() {
        return reason.get();
    }

    public boolean cancel(CancellationReason cancelReason) {
        if (cancelReason == null) {
            cancelReason = CancellationReason.USER_REQUEST;
        }
        return reason.compareAndSet(null, cancelReason);
    }

    public void throwIfCancelled() {
        CancellationReason r = reason.get();
        if (r != null) {
            throw SwarmError.of(SwarmErrorCode.CANCELLED, "Run cancelled: " + r.name().toLowerCase())
                    .toException();
        }
    }
}
