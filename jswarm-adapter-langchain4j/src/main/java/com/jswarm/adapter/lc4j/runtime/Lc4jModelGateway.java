// LangChain4j 模型网关
package com.jswarm.adapter.lc4j.runtime;

import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.invoke.ChatInvoker;
import com.jswarm.adapter.lc4j.invoke.StreamingChatInvoker;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.spi.lifecycle.ModelGateway;
import com.jswarm.spi.lifecycle.ModelRequest;
import com.jswarm.spi.lifecycle.ModelResult;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public final class Lc4jModelGateway implements ModelGateway {

    private final JAgent agent;
    private final Duration timeout;
    private final Consumer<SwarmEvent> streamingSink;
    private final Lc4jMessageCodec messageCodec = new Lc4jMessageCodec();
    private final Lc4jToolCodec toolCodec = new Lc4jToolCodec();

    public Lc4jModelGateway(JAgent agent, Duration timeout, Consumer<SwarmEvent> streamingSink) {
        this.agent = agent;
        this.timeout = timeout;
        this.streamingSink = streamingSink != null ? streamingSink : event -> {
        };
    }

    @Override
    public ModelResult invoke(ModelRequest request, RunScope scope) {
        List<ToolSpecification> tools = request.tools().stream().map(toolCodec::encode).toList();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messageCodec.encode(request.messages()))
                .toolSpecifications(tools)
                .build();
        Duration effectiveTimeout = RunScopeChecks.effectiveModelTimeout(scope, timeout);
        AiMessage response = request.streaming()
                ? StreamingChatInvoker.stream(agent, chatRequest, SwarmContext.current(),
                        effectiveTimeout, streamingSink)
                : ChatInvoker.invoke(agent, chatRequest, effectiveTimeout);
        return new ModelResult(messageCodec.decode(response));
    }
}
