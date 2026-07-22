// 脱敏的 Spring AI 调用日志 Advisor
package com.jswarm.adapter.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class SwarmLoggingAdvisor implements BaseAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmLoggingAdvisor.class);

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        var prompt = request.prompt();
        return request.mutate()
                .context("jswarm_start_time", System.nanoTime())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Object started = response.context().get("jswarm_start_time");
        long elapsed = started instanceof Long value
                ? (System.nanoTime() - value) / 1_000_000 : -1;
        int messages = response.chatResponse() != null
                ? response.chatResponse().getMetadata().getUsage() != null ? 1 : 0 : 0;
        LOG.info("Jswarm model call completed: elapsedMs={}, usagePresent={}", elapsed, messages == 1);
        return response;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
