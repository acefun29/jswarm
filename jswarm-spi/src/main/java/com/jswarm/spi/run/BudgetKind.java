// 预算扣减维度
package com.jswarm.spi.run;

public enum BudgetKind {
    TURN,
    MODEL_CALL,
    TOOL_CALL,
    DEPTH,
    TOKEN,
    COST,
    BYTES
}
