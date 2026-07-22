// 事件序号与终止契约测试
package com.jswarm.runtime.event;

import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.run.RunRequest;
import com.jswarm.spi.run.RunScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDispatcherTest {

    @Test
    void shouldEmitSingleTerminalEvent() {
        List<RunEvent> events = new ArrayList<>();
        EventDispatcher dispatcher = new EventDispatcher(events::add);
        RunScope scope = scope();
        dispatcher.emit(scope, 1, "a", null, RunEventType.MODEL_CALLED, Map.of());
        dispatcher.emit(scope, 1, "a", null, RunEventType.COMPLETED, Map.of());
        assertNull(dispatcher.emit(scope, 1, "a", null, RunEventType.FAILED, Map.of()));
        assertEquals(List.of(1L, 2L), events.stream().map(RunEvent::seq).toList());
    }

    @Test
    void shouldAssignUniqueMonotonicSequenceConcurrently() throws Exception {
        ConcurrentLinkedQueue<RunEvent> events = new ConcurrentLinkedQueue<>();
        EventDispatcher dispatcher = new EventDispatcher(events::add);
        RunScope scope = scope();
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 200; i++) {
                executor.submit(() -> dispatcher.emit(
                        scope, 1, "a", null, RunEventType.MODEL_CALLED, Map.of()));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(200, events.size());
        assertEquals(200, events.stream().map(RunEvent::seq).distinct().count());
        assertEquals(200L, events.stream().mapToLong(RunEvent::seq).max().orElseThrow());
        assertEquals(java.util.stream.LongStream.rangeClosed(1, 200).boxed().toList(),
                events.stream().map(RunEvent::seq).toList());
    }

    private static RunScope scope() {
        return RunScope.root(RunRequest.builder()
                .swarmVersion(SwarmVersion.of("test"))
                .contextSnapshot(ContextSnapshot.empty())
                .runTimeout(Duration.ofMinutes(1))
                .build());
    }
}
