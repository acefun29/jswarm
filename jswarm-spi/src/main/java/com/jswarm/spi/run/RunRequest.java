// 运行入口请求 DTO
package com.jswarm.spi.run;

import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;

import java.time.Duration;
import java.util.Objects;

public final class RunRequest {

    private final String userMessage;
    private final AgentId startAgentId;
    private final SwarmVersion swarmVersion;
    private final ContextSnapshot contextSnapshot;
    private final RunPolicy policy;
    private final RunBudget budget;
    private final Duration runTimeout;

    private RunRequest(Builder builder) {
        this.userMessage = builder.userMessage;
        this.startAgentId = builder.startAgentId;
        this.swarmVersion = Objects.requireNonNull(builder.swarmVersion, "swarmVersion");
        this.contextSnapshot = builder.contextSnapshot != null ? builder.contextSnapshot : ContextSnapshot.empty();
        this.policy = builder.policy != null ? builder.policy : RunPolicy.defaults();
        this.budget = builder.budget != null ? builder.budget : RunBudget.defaults();
        this.runTimeout = builder.runTimeout != null
                ? builder.runTimeout
                : RunDefaults.MODEL_TIMEOUT.multipliedBy(RunDefaults.MAX_TURNS);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String userMessage() {
        return userMessage;
    }

    public AgentId startAgentId() {
        return startAgentId;
    }

    public SwarmVersion swarmVersion() {
        return swarmVersion;
    }

    public ContextSnapshot contextSnapshot() {
        return contextSnapshot;
    }

    public RunPolicy policy() {
        return policy;
    }

    public RunBudget budget() {
        return budget;
    }

    public Duration runTimeout() {
        return runTimeout;
    }

    public static final class Builder {
        private String userMessage;
        private AgentId startAgentId;
        private SwarmVersion swarmVersion;
        private ContextSnapshot contextSnapshot;
        private RunPolicy policy;
        private RunBudget budget;
        private Duration runTimeout;

        public Builder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public Builder startAgentId(AgentId startAgentId) {
            this.startAgentId = startAgentId;
            return this;
        }

        public Builder swarmVersion(SwarmVersion swarmVersion) {
            this.swarmVersion = swarmVersion;
            return this;
        }

        public Builder contextSnapshot(ContextSnapshot contextSnapshot) {
            this.contextSnapshot = contextSnapshot;
            return this;
        }

        public Builder policy(RunPolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder budget(RunBudget budget) {
            this.budget = budget;
            return this;
        }

        public Builder runTimeout(Duration runTimeout) {
            this.runTimeout = runTimeout;
            return this;
        }

        public RunRequest build() {
            return new RunRequest(this);
        }
    }
}
