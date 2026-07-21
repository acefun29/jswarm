package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ChatInvoker {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private ChatInvoker() {
    }

    public static ChatResponse invoke(JAgent agent, Prompt prompt, Duration timeout) {
        SwarmContext captured = SwarmContext.current();
        try {
            if (timeout != null) {
                return withTimeout(agent, prompt, timeout, captured);
            }
            return agent.model().call(prompt);
        } catch (RuntimeException e) {
            throw new SwarmException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private static ChatResponse withTimeout(JAgent agent, Prompt prompt,
                                             Duration timeout, SwarmContext captured) {
        Future<ChatResponse> future = EXECUTOR.submit(() -> {
            SwarmContext previous = SwarmContext.current();
            SwarmContext.set(captured);
            try {
                return agent.model().call(prompt);
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
            throw new SwarmException("LLM call timed out after " + timeout.toMillis() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new SwarmException("LLM call was interrupted", e);
        } catch (CancellationException | ExecutionException e) {
            throw new SwarmException("LLM call failed: " + e.getMessage(), e);
        }
    }
}
