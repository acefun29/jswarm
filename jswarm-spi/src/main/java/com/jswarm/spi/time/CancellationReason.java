// 取消原因枚举
package com.jswarm.spi.time;

public enum CancellationReason {
    USER_REQUEST,
    DEADLINE,
    BUDGET,
    POLICY,
    INTERNAL
}
