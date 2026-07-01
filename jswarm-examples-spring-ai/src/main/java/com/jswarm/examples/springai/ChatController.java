// REST API：同步对话 + SSE 流式对话
package com.jswarm.examples.springai;

import com.jswarm.adapter.springai.run.SwarmRunner;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class ChatController {

    private static final long SSE_TIMEOUT = 120_000L;

    private final SwarmRunner runner;
    private final Map<String, SwarmContext> sessions = new ConcurrentHashMap<>();
    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(SwarmRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/api/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String sessionId = body.getOrDefault("sessionId", "default");
        if (message == null || message.isBlank()) {
            return Map.of("reply", "请输入消息");
        }

        SwarmContext ctx = sessions.computeIfAbsent(sessionId, k -> {
            SwarmContext c = new SwarmContext();
            c.put("user_name", "访客");
            return c;
        });
        String reply = runner.run(message, ctx);
        return Map.of("reply", reply);
    }

    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam(defaultValue = "你好") String message,
                                  @RequestParam(defaultValue = "default") String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        SwarmContext ctx = sessions.computeIfAbsent(sessionId, k -> {
            SwarmContext c = new SwarmContext();
            c.put("user_name", "访客");
            return c;
        });

        sseExecutor.submit(() -> {
            try {
                runner.runStreaming(message, ctx, event -> {
                    try {
                        String type;
                        if (event instanceof SwarmEvent.RunStarted) {
                            type = "run_started";
                        } else if (event instanceof SwarmEvent.AgentEnter) {
                            type = "agent_enter";
                        } else if (event instanceof SwarmEvent.AgentExit) {
                            type = "agent_exit";
                        } else if (event instanceof SwarmEvent.Token) {
                            type = "token";
                        } else if (event instanceof SwarmEvent.ToolCall) {
                            type = "tool_call";
                        } else if (event instanceof SwarmEvent.ToolResult) {
                            type = "tool_result";
                        } else if (event instanceof SwarmEvent.Handoff) {
                            type = "handoff";
                        } else if (event instanceof SwarmEvent.DelegateStarted) {
                            type = "delegate_start";
                        } else if (event instanceof SwarmEvent.DelegateFinished) {
                            type = "delegate_end";
                        } else if (event instanceof SwarmEvent.RecoveryTriggered) {
                            type = "recovery";
                        } else if (event instanceof SwarmEvent.RunCompleted) {
                            type = "run_complete";
                        } else if (event instanceof SwarmEvent.RunFailed) {
                            type = "run_failed";
                        } else {
                            type = "unknown";
                        }
                        emitter.send(SseEmitter.event()
                                .name(type)
                                .data(event));
                        if (event instanceof SwarmEvent.RunCompleted || event instanceof SwarmEvent.RunFailed) {
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
            } catch (RuntimeException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/api/reset")
    public Map<String, String> reset(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "default");
        sessions.remove(sessionId);
        return Map.of("status", "ok");
    }
}
