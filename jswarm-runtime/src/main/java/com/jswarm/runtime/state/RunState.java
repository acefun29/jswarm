// 运行状态
package com.jswarm.runtime.state;

public enum RunState {
    CREATED,
    ENTERING,
    MODEL_CALL,
    TOOL_BATCH,
    ROUTING,
    DELEGATE,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
