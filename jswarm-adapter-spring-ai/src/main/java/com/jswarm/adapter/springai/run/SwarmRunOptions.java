package com.jswarm.adapter.springai.run;

import java.time.Duration;

public record SwarmRunOptions(
        int maxTurns,
        int maxRecoveryAttempts,
        int maxDelegateDepth,
        Duration modelTimeout,
        boolean delegateStreaming,
        SwarmRunListener listener) {

    public static SwarmRunOptions defaults() {
        return new SwarmRunOptions(10, 2, 3, null, true, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxTurns = 10;
        private int maxRecoveryAttempts = 2;
        private int maxDelegateDepth = 3;
        private Duration modelTimeout;
        private boolean delegateStreaming = true;
        private SwarmRunListener listener;

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public Builder maxRecoveryAttempts(int maxRecoveryAttempts) {
            this.maxRecoveryAttempts = maxRecoveryAttempts;
            return this;
        }

        public Builder maxDelegateDepth(int maxDelegateDepth) {
            this.maxDelegateDepth = maxDelegateDepth;
            return this;
        }

        public Builder modelTimeout(Duration modelTimeout) {
            this.modelTimeout = modelTimeout;
            return this;
        }

        public Builder delegateStreaming(boolean delegateStreaming) {
            this.delegateStreaming = delegateStreaming;
            return this;
        }

        public Builder listener(SwarmRunListener listener) {
            this.listener = listener;
            return this;
        }

        public SwarmRunOptions build() {
            if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be positive, got: " + maxTurns);
            if (maxRecoveryAttempts <= 0) throw new IllegalArgumentException("maxRecoveryAttempts must be positive, got: " + maxRecoveryAttempts);
            if (maxDelegateDepth <= 0) throw new IllegalArgumentException("maxDelegateDepth must be positive, got: " + maxDelegateDepth);
            return new SwarmRunOptions(maxTurns, maxRecoveryAttempts, maxDelegateDepth, modelTimeout, delegateStreaming, listener);
        }
    }
}
