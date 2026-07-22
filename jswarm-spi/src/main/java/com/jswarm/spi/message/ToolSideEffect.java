// 工具副作用策略
package com.jswarm.spi.message;

public enum ToolSideEffect {
    READ_ONLY,
    IDEMPOTENT,
    SIDE_EFFECTING
}
