package com.jswarm.adapter.springai.run;

import java.time.Duration;

public record SwarmRunOptions(
        int maxTurns,
        int maxRecoveryAttempts,
        int maxDelegateDepth,
        Duration modelTimeout,
        boolean delegateStreaming) {

    public static SwarmRunOptions defaults() {
        return new SwarmRunOptions(10, 2, 3, null, true);
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

        public SwarmRunOptions build() {
            return new SwarmRunOptions(maxTurns, maxRecoveryAttempts, maxDelegateDepth, modelTimeout, delegateStreaming);
        }
    }
}
