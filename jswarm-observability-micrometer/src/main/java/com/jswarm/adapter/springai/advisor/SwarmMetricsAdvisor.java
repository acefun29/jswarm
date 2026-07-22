// Spring AI Micrometer 指标 Advisor
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
        return request.mutate().context("jswarm_start_time", System.nanoTime()).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Object started = response.context().get("jswarm_start_time");
        if (started instanceof Long value) {
            long elapsed = (System.nanoTime() - value) / 1_000_000;
            meterRegistry.timer("jswarm.llm.call.duration", "status", "success")
                    .record(elapsed, TimeUnit.MILLISECONDS);
            meterRegistry.counter("jswarm.llm.call.count", "status", "success").increment();
        }
        return response;
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
