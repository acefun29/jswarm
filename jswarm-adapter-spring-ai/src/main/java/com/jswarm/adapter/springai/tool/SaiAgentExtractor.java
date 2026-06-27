package com.jswarm.adapter.springai.tool;

public final class SaiAgentExtractor {

    private SaiAgentExtractor() {
    }

    public static String extractInstructions(Class<?> aiServiceClass) {
        throw new UnsupportedOperationException(
                "Phase 1 does not support fromAiService. Use JAgent.builder() instead.");
    }
}
