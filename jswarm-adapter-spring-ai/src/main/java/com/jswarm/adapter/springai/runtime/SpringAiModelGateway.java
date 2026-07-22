// Spring AI 模型网关
package com.jswarm.adapter.springai.runtime;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.invoke.AdvisorChatInvoker;
import com.jswarm.adapter.springai.invoke.ChatInvoker;
import com.jswarm.adapter.springai.invoke.StreamingChatInvoker;
import com.jswarm.core.SwarmEvent;
import com.jswarm.spi.lifecycle.ModelGateway;
import com.jswarm.spi.lifecycle.ModelRequest;
import com.jswarm.spi.lifecycle.ModelResult;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public final class SpringAiModelGateway implements ModelGateway {

    private final JAgent agent;
    private final Duration timeout;
    private final List<Advisor> advisors;
    private final Consumer<SwarmEvent> streamingSink;
    private final SpringAiMessageCodec messageCodec = new SpringAiMessageCodec();
    private final SpringAiToolCodec toolCodec = new SpringAiToolCodec();

    public SpringAiModelGateway(
            JAgent agent,
            Duration timeout,
            List<Advisor> advisors,
            Consumer<SwarmEvent> streamingSink) {
        this.agent = agent;
        this.timeout = timeout;
        this.advisors = advisors != null ? List.copyOf(advisors) : List.of();
        this.streamingSink = streamingSink != null ? streamingSink : event -> {
        };
    }

    @Override
    public ModelResult invoke(ModelRequest request, RunScope scope) {
        ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(request.tools().stream().map(toolCodec::encode).toList())
                .build();
        Prompt prompt = new Prompt(messageCodec.encode(request.messages()), chatOptions);
        Duration effectiveTimeout = RunScopeChecks.effectiveModelTimeout(scope, timeout);
        AssistantMessage response;
        if (request.streaming()) {
            response = advisors.isEmpty()
                    ? StreamingChatInvoker.stream(agent, prompt, effectiveTimeout, streamingSink,
                            scope.cancellation())
                    : AdvisorChatInvoker.stream(agent, prompt, effectiveTimeout, advisors, streamingSink,
                            scope.cancellation());
        } else {
            response = advisors.isEmpty()
                    ? ChatInvoker.invoke(agent, prompt, effectiveTimeout).getResult().getOutput()
                    : AdvisorChatInvoker.invoke(agent, prompt, effectiveTimeout, advisors).getResult().getOutput();
        }
        return new ModelResult(messageCodec.decode(response));
    }
}
