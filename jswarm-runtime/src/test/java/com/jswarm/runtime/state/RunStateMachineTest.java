// 状态转移契约测试
package com.jswarm.runtime.state;

import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunStateMachineTest {

    @Test
    void shouldFollowMainLoopTransitions() {
        RunStateMachine machine = new RunStateMachine();
        machine.transitionTo(RunState.ENTERING);
        machine.transitionTo(RunState.MODEL_CALL);
        machine.transitionTo(RunState.TOOL_BATCH);
        machine.transitionTo(RunState.ROUTING);
        machine.transitionTo(RunState.ENTERING);
        machine.transitionTo(RunState.MODEL_CALL);
        machine.transitionTo(RunState.COMPLETED);
        assertEquals(RunState.COMPLETED, machine.state());
    }

    @Test
    void shouldRejectTransitionAfterTerminal() {
        RunStateMachine machine = new RunStateMachine();
        machine.transitionTo(RunState.ENTERING);
        machine.transitionTo(RunState.MODEL_CALL);
        machine.transitionTo(RunState.COMPLETED);
        SwarmErrorException error = assertThrows(SwarmErrorException.class,
                () -> machine.transitionTo(RunState.FAILED));
        assertEquals(SwarmErrorCode.ILLEGAL_STATE, error.code());
    }
}
