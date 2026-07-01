// Advisor 集成 — Micrometer 指标上报
package com.jswarm.adapter.springai.advisor;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

import java.util.concurrent.TimeUnit;

public class SwarmMetricsAdvisor implements BaseAdvisor {

    private final MeterRegistry meterRegistry;

    public SwarmMetricsAdvisor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request.mutate()
                .context("swarm_start_time", System.nanoTime())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Long startTime = (Long) response.context().get("swarm_start_time");
        if (startTime != null) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            meterRegistry.timer("jswarm.llm.call.duration").record(elapsedMs, TimeUnit.MILLISECONDS);
            meterRegistry.counter("jswarm.llm.call.count").increment();
        }
        return response;
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
