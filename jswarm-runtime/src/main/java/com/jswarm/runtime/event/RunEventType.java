// 运行事件类型
package com.jswarm.runtime.event;

public enum RunEventType {
    STATE_CHANGED,
    AGENT_ENTERED,
    AGENT_EXITED,
    MODEL_CALLED,
    TOOL_CALLED,
    TOOL_RESULT,
    HANDOFF,
    DELEGATE_STARTED,
    DELEGATE_COMPLETED,
    RECOVERY,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
