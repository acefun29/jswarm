package com.jswarm.core;

public interface Agent {
    String id();
    String name();
    String description();

    default String instructions() {
        return "";
    }

    default void onEnter(SwarmContext context) {
    }

    default void onExit(SwarmContext context) {
    }

    default void onDelegateEnter(SwarmContext context, String task) {
    }

    default void onDelegateExit(SwarmContext context, String task, String result) {
    }
}
