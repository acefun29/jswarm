// decorate 链式构建器，产出 ForwardingJAgent
package com.jswarm.adapter.lc4j;

import com.jswarm.core.SwarmContext;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class JAgentDecorator {

    private final JAgent delegate;
    private Consumer<SwarmContext> onEnter;
    private Consumer<SwarmContext> onExit;
    private BiConsumer<SwarmContext, String> onDelegateEnter;
    private DelegateExitHook onDelegateExit;

    JAgentDecorator(JAgent delegate) {
        this.delegate = delegate;
    }

    public JAgentDecorator onEnter(Consumer<SwarmContext> onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    public JAgentDecorator onExit(Consumer<SwarmContext> onExit) {
        this.onExit = onExit;
        return this;
    }

    public JAgentDecorator onDelegateEnter(BiConsumer<SwarmContext, String> onDelegateEnter) {
        this.onDelegateEnter = onDelegateEnter;
        return this;
    }

    public JAgentDecorator onDelegateExit(DelegateExitHook onDelegateExit) {
        this.onDelegateExit = onDelegateExit;
        return this;
    }

    public JAgent build() {
        return new ForwardingJAgent(delegate, onEnter, onExit, onDelegateEnter, onDelegateExit);
    }
}
