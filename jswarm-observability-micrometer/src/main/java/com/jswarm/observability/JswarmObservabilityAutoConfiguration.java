// Jswarm 可观测性可选自动配置
package com.jswarm.observability;

import com.jswarm.adapter.springai.advisor.SwarmLoggingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Advisor.class)
public class JswarmObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "jswarm", name = "logging.enabled", havingValue = "true")
    public Advisor swarmLoggingAdvisor() {
        return new SwarmLoggingAdvisor();
    }
}
