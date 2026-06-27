package com.jswarm.adapter.springai;

import com.jswarm.core.SwarmContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class ForwardingJAgent implements JAgent {

    private final JAgent delegate;
    private final Consumer<SwarmContext> onEnter;
    private final Consumer<SwarmContext> onExit;
    private final BiConsumer<SwarmContext, String> onDelegateEnter;
    private final DelegateExitHook onDelegateExit;

    ForwardingJAgent(JAgent delegate, Consumer<SwarmContext> onEnter, Consumer<SwarmContext> onExit,
                     BiConsumer<SwarmContext, String> onDelegateEnter, DelegateExitHook onDelegateExit) {
        this.delegate = delegate;
        this.onEnter = onEnter;
        this.onExit = onExit;
        this.onDelegateEnter = onDelegateEnter;
        this.onDelegateExit = onDelegateExit;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public String instructions() {
        return delegate.instructions();
    }

    @Override
    public ChatModel model() {
        return delegate.model();
    }

    @Override
    public StreamingChatModel streamingModel() {
        return delegate.streamingModel();
    }

    @Override
    public List<ToolCallback> externalTools() {
        return delegate.externalTools();
    }

    @Override
    public ExternalToolExecutor toolExecutor() {
        return delegate.toolExecutor();
    }

    @Override
    public void onEnter(SwarmContext context) {
        if (onEnter != null) {
            onEnter.accept(context);
        } else {
            delegate.onEnter(context);
        }
    }

    @Override
    public void onExit(SwarmContext context) {
        if (onExit != null) {
            onExit.accept(context);
        } else {
            delegate.onExit(context);
        }
    }

    @Override
    public void onDelegateEnter(SwarmContext context, String task) {
        if (onDelegateEnter != null) {
            onDelegateEnter.accept(context, task);
        } else {
            delegate.onDelegateEnter(context, task);
        }
    }

    @Override
    public void onDelegateExit(SwarmContext context, String task, String result) {
        if (onDelegateExit != null) {
            onDelegateExit.accept(context, task, result);
        } else {
            delegate.onDelegateExit(context, task, result);
        }
    }
}
