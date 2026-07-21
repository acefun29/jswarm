package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.core.SwarmException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class StreamingChatInvoker {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private StreamingChatInvoker() {
    }

    public static AssistantMessage stream(JAgent agent, Prompt prompt, Duration timeout,
                                          Consumer<SwarmEvent> sink) {
        StreamingChatModel streamingModel = agent.streamingModel();
        if (streamingModel == null) {
            return fallbackSync(agent, prompt, timeout, sink);
        }
        return streamInternal(agent, prompt, timeout, sink, streamingModel);
    }

    private static AssistantMessage fallbackSync(JAgent agent, Prompt prompt,
                                                  Duration timeout, Consumer<SwarmEvent> sink) {
        ChatResponse response = ChatInvoker.invoke(agent, prompt, timeout);
        AssistantMessage msg = response.getResult().getOutput();
        String text = msg.getText();
        if (text != null && !text.isEmpty()) {
            sink.accept(new SwarmEvent.Token(agent.id(), text));
        }
        return msg;
    }

    private static AssistantMessage streamInternal(JAgent agent, Prompt prompt,
                                                    Duration timeout, Consumer<SwarmEvent> sink,
                                                    StreamingChatModel streamingModel) {
        SwarmContext captured = SwarmContext.current();
        String agentId = agent.id();

        Future<AssistantMessage> future = EXECUTOR.submit(() -> {
            SwarmContext previous = SwarmContext.current();
            SwarmContext.set(captured);
            Disposable subscription = null;
            try {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                subscription = new MessageAggregator()
                        .aggregate(streamingModel.stream(prompt), cr -> {
                            aggregatedRef.set(cr);
                            latch.countDown();
                        })
                        .doOnNext(chunk -> {
                            SwarmContext earlier = SwarmContext.current();
                            SwarmContext.set(captured);
                            try {
                                if (chunk.getResult() != null) {
                                    AssistantMessage msg = chunk.getResult().getOutput();
                                    if (msg != null) {
                                        String text = msg.getText();
                                        if (text != null && !text.isEmpty()) {
                                            sink.accept(new SwarmEvent.Token(agentId, text));
                                        }
                                    }
                                }
                            } finally {
                                if (earlier != null) {
                                    SwarmContext.set(earlier);
                                } else {
                                    SwarmContext.clear();
                                }
                            }
                        })
                        .doOnError(error -> {
                            errorRef.set(error);
                            latch.countDown();
                        })
                        .subscribe();

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SwarmException("Streaming LLM call was interrupted", e);
                }

                if (errorRef.get() != null) {
                    Throwable error = errorRef.get();
                    if (error instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new SwarmException("Streaming LLM call failed: " + error.getMessage(), error);
                }

                ChatResponse aggregated = aggregatedRef.get();
                if (aggregated == null || aggregated.getResult() == null) {
                    throw new SwarmException("LLM returned empty streaming response");
                }
                return aggregated.getResult().getOutput();
            } finally {
                if (subscription != null && !subscription.isDisposed()) {
                    subscription.dispose();
                }
                if (previous != null) {
                    SwarmContext.set(previous);
                } else {
                    SwarmContext.clear();
                }
            }
        });

        try {
            if (timeout != null) {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new SwarmException("Streaming LLM call timed out after " + timeout.toMillis() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new SwarmException("Streaming LLM call was interrupted", e);
        } catch (CancellationException | ExecutionException e) {
            if (e.getCause() instanceof SwarmException se) {
                throw se;
            }
            throw new SwarmException("Streaming LLM call failed: " + e.getMessage(), e);
        }
    }
}
