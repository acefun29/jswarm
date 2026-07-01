// Spring Boot AutoConfiguration 自动装配
package com.jswarm.adapter.springai.auto;

import com.jswarm.adapter.springai.advisor.SwarmLoggingAdvisor;
import com.jswarm.adapter.springai.run.SwarmRunner;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.core.Swarm;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Swarm.class)
@EnableConfigurationProperties(JswarmProperties.class)
public class JswarmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SwarmRunOptions swarmRunOptions(JswarmProperties props) {
        SwarmRunOptions.Builder builder = SwarmRunOptions.builder()
                .maxTurns(props.getMaxTurns())
                .maxRecoveryAttempts(props.getMaxRecoveryAttempts())
                .maxDelegateDepth(props.getMaxDelegateDepth())
                .delegateStreaming(props.isDelegateStreaming());
        if (props.getModelTimeout() != null) {
            builder.modelTimeout(props.getModelTimeout());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Swarm.class)
    public SwarmRunner swarmRunner(Swarm swarm, SwarmRunOptions options) {
        return SwarmRunner.create(swarm, options);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jswarm", name = "logging.enabled", havingValue = "true")
    public SwarmLoggingAdvisor swarmLoggingAdvisor() {
        return new SwarmLoggingAdvisor();
    }
}
