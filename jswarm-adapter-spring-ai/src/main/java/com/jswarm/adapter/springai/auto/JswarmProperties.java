// Spring Boot AutoConfiguration 配置属性绑定
package com.jswarm.adapter.springai.auto;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jswarm")
public class JswarmProperties {

    private int maxTurns = 10;
    private int maxRecoveryAttempts = 2;
    private int maxDelegateDepth = 3;
    private boolean delegateStreaming = true;
    private Duration modelTimeout;
    private Logging logging = new Logging();

    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }

    public int getMaxRecoveryAttempts() { return maxRecoveryAttempts; }
    public void setMaxRecoveryAttempts(int maxRecoveryAttempts) { this.maxRecoveryAttempts = maxRecoveryAttempts; }

    public int getMaxDelegateDepth() { return maxDelegateDepth; }
    public void setMaxDelegateDepth(int maxDelegateDepth) { this.maxDelegateDepth = maxDelegateDepth; }

    public boolean isDelegateStreaming() { return delegateStreaming; }
    public void setDelegateStreaming(boolean delegateStreaming) { this.delegateStreaming = delegateStreaming; }

    public Duration getModelTimeout() { return modelTimeout; }
    public void setModelTimeout(Duration modelTimeout) { this.modelTimeout = modelTimeout; }

    public Logging getLogging() { return logging; }
    public void setLogging(Logging logging) { this.logging = logging; }

    public static class Logging {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
