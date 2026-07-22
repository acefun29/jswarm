// Jswarm Spring Boot 配置属性
package com.jswarm.starter.auto;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jswarm")
public class JswarmProperties {

    private int maxTurns = 10;
    private int maxRecoveryAttempts = 2;
    private int maxDelegateDepth = 3;
    private boolean delegateStreaming = true;
    private Duration modelTimeout;
    private final Logging logging = new Logging();

    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int value) { maxTurns = value; }
    public int getMaxRecoveryAttempts() { return maxRecoveryAttempts; }
    public void setMaxRecoveryAttempts(int value) { maxRecoveryAttempts = value; }
    public int getMaxDelegateDepth() { return maxDelegateDepth; }
    public void setMaxDelegateDepth(int value) { maxDelegateDepth = value; }
    public boolean isDelegateStreaming() { return delegateStreaming; }
    public void setDelegateStreaming(boolean value) { delegateStreaming = value; }
    public Duration getModelTimeout() { return modelTimeout; }
    public void setModelTimeout(Duration value) { modelTimeout = value; }
    public Logging getLogging() { return logging; }

    public void validate() {
        if (maxTurns <= 0 || maxRecoveryAttempts < 0 || maxDelegateDepth <= 0) {
            throw new IllegalArgumentException("jswarm run limits are invalid");
        }
        if (modelTimeout != null && (modelTimeout.isNegative() || modelTimeout.isZero())) {
            throw new IllegalArgumentException("jswarm.model-timeout must be positive");
        }
    }

    public static class Logging {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
    }
}
