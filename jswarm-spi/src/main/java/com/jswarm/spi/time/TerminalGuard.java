// 运行终止守卫，防止 terminal 后继续操作
package com.jswarm.spi.time;

import com.jswarm.spi.error.SwarmError;
import com.jswarm.spi.error.SwarmErrorCode;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TerminalGuard {

    private final AtomicBoolean terminal = new AtomicBoolean(false);

    public boolean markTerminal() {
        return terminal.compareAndSet(false, true);
    }

    public boolean isTerminal() {
        return terminal.get();
    }

    public void checkActive() {
        if (terminal.get()) {
            throw SwarmError.of(SwarmErrorCode.CANCELLED, "Run already completed")
                    .toException();
        }
    }
}
