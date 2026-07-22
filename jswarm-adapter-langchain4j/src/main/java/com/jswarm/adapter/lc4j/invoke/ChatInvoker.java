package com.jswarm.adapter.lc4j.invoke;

import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.error.SwarmErrorMapper;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ChatInvoker {

    private static final ExecutorService CHAT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "jswarm-chat-invoker");
        t.setDaemon(true);
        return t;
    });

    private ChatInvoker() {
    }

    public static AiMessage invoke(JAgent agent, ChatRequest request, Duration timeout) {
        RunScope scope = RunScope.current();
        Duration effectiveTimeout = RunScopeChecks.effectiveModelTimeout(scope, timeout);
        SwarmContext context = SwarmContext.current();
        Future<AiMessage> future = CHAT_EXECUTOR.submit(() -> {
            SwarmContext previous = SwarmContext.current();
            SwarmContext.set(context);
            try {
                return agent.model().chat(request).aiMessage();
            } finally {
                if (previous != null) {
                    SwarmContext.set(previous);
                } else {
                    SwarmContext.clear();
                }
            }
        });
        try {
            return future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw SwarmErrorMapper.toRuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw SwarmErrorMapper.toRuntimeException(e);
        } catch (ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) {
                throw SwarmErrorMapper.toRuntimeException(re);
            }
            throw SwarmErrorMapper.toRuntimeException(cause);
        }
    }
}
