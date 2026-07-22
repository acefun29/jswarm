// Starter Spring Context smoke 测试
package com.jswarm.starter.auto;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JswarmContextSmokeTest {

    @Test
    void shouldCreateRunnerForSingleSwarm() {
        JAgent agent = JAgent.builder("a", "A")
                .description("d").instructions("i").model(prompt -> null).build();
        Swarm swarm = Swarm.create("s").agent(agent).entry("a").build();

        new ApplicationContextRunner()
                .withUserConfiguration(JswarmAutoConfiguration.class)
                .withBean(Swarm.class, () -> swarm)
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertNotNull(context.getBean(com.jswarm.adapter.springai.run.SwarmRunOptions.class));
                    assertNotNull(context.getBean(com.jswarm.adapter.springai.run.SwarmRunner.class));
                });
    }
}
