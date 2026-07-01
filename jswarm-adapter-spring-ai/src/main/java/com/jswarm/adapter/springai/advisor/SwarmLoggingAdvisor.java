// Advisor 集成 — LLM 调用日志记录
package com.jswarm.adapter.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class SwarmLoggingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SwarmLoggingAdvisor.class);

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        var prompt = request.prompt();
        log.info("[LLM→] messages={}, tools={}",
                prompt.getInstructions().size(),
                prompt.getOptions() instanceof ToolCallingChatOptions tco
                        ? tco.getToolCallbacks().size() : 0);
        return request.mutate()
                .context("swarm_start_time", System.nanoTime())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Long startTime = (Long) response.context().get("swarm_start_time");
        if (startTime != null) {
            long elapsed = (System.nanoTime() - startTime) / 1_000_000;
            log.info("[LLM←] {}ms, text={}", elapsed,
                    response.chatResponse().getResult().getOutput().getText());
        }
        return response;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
