// Jswarm Spring Boot 自动配置
package com.jswarm.starter.auto;

import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.adapter.springai.run.SwarmRunner;
import com.jswarm.core.Swarm;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass({Swarm.class, SwarmRunner.class})
@EnableConfigurationProperties(JswarmProperties.class)
public class JswarmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SwarmRunOptions swarmRunOptions(
            JswarmProperties props, ObjectProvider<Advisor> advisors) {
        props.validate();
        SwarmRunOptions.Builder builder = SwarmRunOptions.builder()
                .maxTurns(props.getMaxTurns())
                .maxRecoveryAttempts(props.getMaxRecoveryAttempts())
                .maxDelegateDepth(props.getMaxDelegateDepth())
                .delegateStreaming(props.isDelegateStreaming())
                .advisors(advisors.orderedStream().toList());
        if (props.getModelTimeout() != null) {
            builder.modelTimeout(props.getModelTimeout());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(Swarm.class)
    public SwarmRunner swarmRunner(Swarm swarm, SwarmRunOptions options) {
        return SwarmRunner.create(swarm, options);
    }

}
