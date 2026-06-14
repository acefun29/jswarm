package com.jswarm.adapter.lc4j.tool;

public final class ToolNotHandledException extends RuntimeException {

    public ToolNotHandledException(String toolName) {
        super("Tool not handled: " + toolName);
    }
}
