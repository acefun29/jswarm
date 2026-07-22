package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.error.SwarmErrorMapper;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
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
        RunScope scope = RunScope.current();
        Duration effectiveTimeout = RunScopeChecks.effectiveModelTimeout(scope, timeout);
        SwarmContext captured = SwarmContext.current();
        try {
            return withTimeout(agent, prompt, effectiveTimeout, captured);
        } catch (RuntimeException e) {
            throw SwarmErrorMapper.toRuntimeException(e);
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
            throw SwarmErrorMapper.toRuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw SwarmErrorMapper.toRuntimeException(e);
        } catch (CancellationException | ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw SwarmErrorMapper.toRuntimeException(cause);
        }
    }
}
