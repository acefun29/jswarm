// 合法状态转移控制
package com.jswarm.runtime.state;

import com.jswarm.spi.error.SwarmError;
import com.jswarm.spi.error.SwarmErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class RunStateMachine {

    private static final Map<RunState, EnumSet<RunState>> TRANSITIONS = transitions();

    private final AtomicReference<RunState> state = new AtomicReference<>(RunState.CREATED);

    public RunState state() {
        return state.get();
    }

    public void transitionTo(RunState next) {
        while (true) {
            RunState current = state.get();
            if (!TRANSITIONS.getOrDefault(current, EnumSet.noneOf(RunState.class)).contains(next)) {
                throw SwarmError.of(SwarmErrorCode.ILLEGAL_STATE,
                                "Illegal run state transition")
                        .withMetadata("from", current.name())
                        .withMetadata("to", next.name())
                        .toException();
            }
            if (state.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private static Map<RunState, EnumSet<RunState>> transitions() {
        Map<RunState, EnumSet<RunState>> values = new EnumMap<>(RunState.class);
        values.put(RunState.CREATED, EnumSet.of(RunState.ENTERING, RunState.FAILED, RunState.CANCELLED));
        values.put(RunState.ENTERING, EnumSet.of(RunState.MODEL_CALL, RunState.FAILED, RunState.CANCELLED));
        values.put(RunState.MODEL_CALL, EnumSet.of(RunState.TOOL_BATCH, RunState.COMPLETED, RunState.FAILED, RunState.CANCELLED));
        values.put(RunState.TOOL_BATCH, EnumSet.of(RunState.MODEL_CALL, RunState.ROUTING, RunState.DELEGATE, RunState.FAILED, RunState.CANCELLED));
        values.put(RunState.ROUTING, EnumSet.of(RunState.ENTERING, RunState.FAILED, RunState.CANCELLED));
        values.put(RunState.DELEGATE, EnumSet.of(RunState.MODEL_CALL, RunState.FAILED, RunState.CANCELLED));
        values.put(RunState.COMPLETED, EnumSet.noneOf(RunState.class));
        values.put(RunState.FAILED, EnumSet.noneOf(RunState.class));
        values.put(RunState.CANCELLED, EnumSet.noneOf(RunState.class));
        return Map.copyOf(values);
    }
}
