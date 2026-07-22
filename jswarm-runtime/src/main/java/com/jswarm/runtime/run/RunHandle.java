// 可取消的共享 Runtime 运行句柄
package com.jswarm.runtime.run;

import com.jswarm.runtime.event.RunEvent;
import com.jswarm.spi.time.CancellationReason;
import com.jswarm.spi.run.RunScope;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RunHandle {

    private final RunScope scope;
    private final CompletableFuture<RunResult> completion;
    private final CopyOnWriteArrayList<RunEvent> events = new CopyOnWriteArrayList<>();

    public RunHandle(RunScope scope, CompletableFuture<RunResult> completion) {
        this.scope = scope;
        this.completion = completion;
    }

    public CompletableFuture<RunResult> completion() {
        return completion;
    }

    public boolean cancel() {
        boolean cancelled = scope.cancellation().cancel(CancellationReason.USER_REQUEST);
        return cancelled;
    }

    public boolean cancelled() {
        return scope.cancellation().isCancelled();
    }

    public List<RunEvent> events() {
        return List.copyOf(events);
    }

    public RunResult await() {
        return completion.join();
    }

    public void append(RunEvent event) {
        if (event != null) {
            events.add(event);
        }
    }
}
