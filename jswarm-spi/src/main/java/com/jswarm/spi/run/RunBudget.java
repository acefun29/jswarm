// 原子运行预算
package com.jswarm.spi.run;

import com.jswarm.spi.error.SwarmError;
import com.jswarm.spi.error.SwarmErrorCode;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RunBudget {

    private final AtomicInteger remainingTurns;
    private final AtomicInteger remainingModelCalls;
    private final AtomicInteger remainingToolCalls;
    private final AtomicInteger remainingDepth;
    private final AtomicLong remainingTokens;
    private final AtomicLong remainingCost;
    private final AtomicLong remainingBytes;

    private final int maxTurns;
    private final int maxModelCalls;
    private final int maxToolCalls;
    private final int maxDepth;

    private RunBudget(Builder builder) {
        this.maxTurns = builder.maxTurns;
        this.maxModelCalls = builder.maxModelCalls;
        this.maxToolCalls = builder.maxToolCalls;
        this.maxDepth = builder.maxDepth;
        this.remainingTurns = new AtomicInteger(maxTurns);
        this.remainingModelCalls = new AtomicInteger(maxModelCalls);
        this.remainingToolCalls = new AtomicInteger(maxToolCalls);
        this.remainingDepth = new AtomicInteger(maxDepth);
        this.remainingTokens = new AtomicLong(builder.maxTokens);
        this.remainingCost = new AtomicLong(builder.maxCost);
        this.remainingBytes = new AtomicLong(builder.maxBytes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RunBudget defaults() {
        return builder().build();
    }

    public boolean tryConsume(BudgetKind kind) {
        return tryConsume(kind, 1);
    }

    public boolean tryConsume(BudgetKind kind, long amount) {
        if (amount <= 0) {
            return true;
        }
        return switch (kind) {
            case TURN -> {
                int after = remainingTurns.addAndGet(-(int) amount);
                if (after < 0) {
                    remainingTurns.addAndGet((int) amount);
                    yield false;
                }
                yield true;
            }
            case MODEL_CALL -> {
                int after = remainingModelCalls.addAndGet(-(int) amount);
                if (after < 0) {
                    remainingModelCalls.addAndGet((int) amount);
                    yield false;
                }
                yield true;
            }
            case TOOL_CALL -> {
                int after = remainingToolCalls.addAndGet(-(int) amount);
                if (after < 0) {
                    remainingToolCalls.addAndGet((int) amount);
                    yield false;
                }
                yield true;
            }
            case DEPTH -> {
                int after = remainingDepth.addAndGet(-(int) amount);
                if (after < 0) {
                    remainingDepth.addAndGet((int) amount);
                    yield false;
                }
                yield true;
            }
            case TOKEN -> {
                long after = remainingTokens.addAndGet(-amount);
                if (after < 0) {
                    remainingTokens.addAndGet(amount);
                    yield false;
                }
                yield true;
            }
            case COST -> {
                long after = remainingCost.addAndGet(-amount);
                if (after < 0) {
                    remainingCost.addAndGet(amount);
                    yield false;
                }
                yield true;
            }
            case BYTES -> {
                long after = remainingBytes.addAndGet(-amount);
                if (after < 0) {
                    remainingBytes.addAndGet(amount);
                    yield false;
                }
                yield true;
            }
        };
    }

    public void consumeOrThrow(BudgetKind kind) {
        consumeOrThrow(kind, 1);
    }

    public void consumeOrThrow(BudgetKind kind, long amount) {
        if (!tryConsume(kind, amount)) {
            throw SwarmError.of(SwarmErrorCode.BUDGET_EXCEEDED,
                    "Budget exceeded: " + kind.name().toLowerCase())
                    .withMetadata("budgetKind", kind.name())
                    .toException();
        }
    }

    public int remainingTurns() {
        return remainingTurns.get();
    }

    public int remainingModelCalls() {
        return remainingModelCalls.get();
    }

    public int remainingToolCalls() {
        return remainingToolCalls.get();
    }

    public int remainingDepth() {
        return remainingDepth.get();
    }

    public long remainingTokens() {
        return remainingTokens.get();
    }

    public long remainingBytes() {
        return remainingBytes.get();
    }

    public static final class Builder {
        private int maxTurns = RunDefaults.MAX_TURNS;
        private int maxModelCalls = RunDefaults.MAX_TURNS;
        private int maxToolCalls = Integer.MAX_VALUE;
        private int maxDepth = RunDefaults.MAX_DELEGATE_DEPTH;
        private long maxTokens = RunDefaults.UNLIMITED;
        private long maxCost = RunDefaults.UNLIMITED;
        private long maxBytes = RunDefaults.UNLIMITED;

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public Builder maxModelCalls(int maxModelCalls) {
            this.maxModelCalls = maxModelCalls;
            return this;
        }

        public Builder maxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder maxTokens(long maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder maxCost(long maxCost) {
            this.maxCost = maxCost;
            return this;
        }

        public Builder maxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        public RunBudget build() {
            if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be > 0");
            if (maxModelCalls <= 0) throw new IllegalArgumentException("maxModelCalls must be > 0");
            if (maxToolCalls <= 0) throw new IllegalArgumentException("maxToolCalls must be > 0");
            if (maxDepth <= 0) throw new IllegalArgumentException("maxDepth must be > 0");
            return new RunBudget(this);
        }
    }
}
