// 运行事件接收器
package com.jswarm.runtime.event;

@FunctionalInterface
public interface RunEventSink {

    RunEventSink NOOP = event -> {
    };

    void accept(RunEvent event);
}
