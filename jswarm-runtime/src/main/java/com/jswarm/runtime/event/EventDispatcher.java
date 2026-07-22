// 单调且唯一终止的事件分发器
package com.jswarm.runtime.event;

import com.jswarm.spi.run.RunScope;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class EventDispatcher {

    private final RunEventSink sink;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean terminal = new AtomicBoolean();

    public EventDispatcher(RunEventSink sink) {
        this(sink, Clock.systemUTC());
    }

    EventDispatcher(RunEventSink sink, Clock clock) {
        this.sink = sink != null ? sink : RunEventSink.NOOP;
        this.clock = clock;
    }

    public RunEvent emit(
            RunScope scope,
            int turn,
            String agentId,
            String callId,
            RunEventType type,
            Map<String, Object> payload) {
        if (terminal.get()) {
            return null;
        }
        if (type.terminal() && !terminal.compareAndSet(false, true)) {
            return null;
        }
        RunEvent event = new RunEvent(
                scope.runId(),
                scope.parentRunId(),
                sequence.incrementAndGet(),
                turn,
                scope.depth(),
                agentId,
                callId,
                Instant.now(clock),
                type,
                payload);
        sink.accept(event);
        return event;
    }

    public boolean terminal() {
        return terminal.get();
    }
}
