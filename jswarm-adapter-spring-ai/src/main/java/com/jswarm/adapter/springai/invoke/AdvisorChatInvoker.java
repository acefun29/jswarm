// Advisor 感知的 LLM 调用封装
package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.core.SwarmException;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.client.advisor.ChatModelStreamAdvisor;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.List;
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

public final class AdvisorChatInvoker {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private AdvisorChatInvoker() {}

    public static ChatResponse invoke(JAgent agent, Prompt prompt,
                                       Duration timeout, List<Advisor> advisors) {
        if (advisors == null || advisors.isEmpty()) {
            return ChatInvoker.invoke(agent, prompt, timeout);
        }
        DefaultAroundAdvisorChain chain = DefaultAroundAdvisorChain
                .builder(ObservationRegistry.NOOP)
                .pushAll(advisors)
                .push(ChatModelCallAdvisor.builder().chatModel(agent.model()).build())
                .build();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();
        ChatClientResponse response = chain.nextCall(request);
        return response.chatResponse();
    }

    public static AssistantMessage stream(JAgent agent, Prompt prompt,
                                           Duration timeout, List<Advisor> advisors,
                                           Consumer<SwarmEvent> sink) {
        if (advisors == null || advisors.isEmpty()) {
            return StreamingChatInvoker.stream(agent, prompt, timeout, sink);
        }
        DefaultAroundAdvisorChain chain = DefaultAroundAdvisorChain
                .builder(ObservationRegistry.NOOP)
                .pushAll(advisors)
                .push(ChatModelStreamAdvisor.builder().chatModel(agent.model()).build())
                .build();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();
        var flux = chain.nextStream(request)
                .map(ChatClientResponse::chatResponse);
        return aggregateStreaming(agent.id(), flux, timeout, sink);
    }

    private static AssistantMessage aggregateStreaming(String agentId,
                                                        reactor.core.publisher.Flux<ChatResponse> chatFlux,
                                                        Duration timeout,
                                                        Consumer<SwarmEvent> sink) {
        SwarmContext captured = SwarmContext.current();

        Future<AssistantMessage> future = EXECUTOR.submit(() -> {
            SwarmContext previous = SwarmContext.current();
            SwarmContext.set(captured);
            Disposable subscription = null;
            try {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                subscription = new MessageAggregator()
                        .aggregate(chatFlux, cr -> {
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
                    if (error instanceof RuntimeException re) throw re;
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
            if (e.getCause() instanceof SwarmException se) throw se;
            throw new SwarmException("Streaming LLM call failed: " + e.getMessage(), e);
        }
    }
}
