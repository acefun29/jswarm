package com.jswarm.adapter.lc4j.invoke;

import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
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
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new SwarmException("LLM call timed out after " + timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new SwarmException("LLM call interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new SwarmException("LLM call failed", cause);
        }
    }
}
