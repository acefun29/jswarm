package com.jswarm.adapter.springai;

public class ToolNotHandledException extends RuntimeException {

    private final String toolName;

    public ToolNotHandledException(String toolName) {
        super("No executor handles tool: " + toolName);
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }
}
