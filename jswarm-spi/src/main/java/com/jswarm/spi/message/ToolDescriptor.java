// Canonical 工具描述
package com.jswarm.spi.message;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record ToolDescriptor(
        String toolId,
        String version,
        String name,
        String description,
        String inputSchema,
        String outputSchema,
        Set<String> allowedAgentIds,
        Set<String> allowedTenantIds,
        Set<String> allowedPrincipals,
        ToolSensitivity sensitivity,
        Duration timeout,
        long maxResultBytes,
        String idempotencyKeyField,
        ToolSideEffect sideEffect,
        boolean confirmationRequired) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");

    public ToolDescriptor {
        requireId("toolId", toolId);
        requireId("version", version);
        requireId("name", name);
        description = description != null ? description : "";
        inputSchema = requireSchema(inputSchema, "inputSchema");
        outputSchema = requireSchema(outputSchema, "outputSchema");
        allowedAgentIds = immutableSet(allowedAgentIds);
        allowedTenantIds = immutableSet(allowedTenantIds);
        allowedPrincipals = immutableSet(allowedPrincipals);
        sensitivity = sensitivity != null ? sensitivity : ToolSensitivity.PUBLIC;
        timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("tool timeout must be positive");
        }
        if (maxResultBytes <= 0) {
            throw new IllegalArgumentException("maxResultBytes must be positive");
        }
        idempotencyKeyField = idempotencyKeyField != null ? idempotencyKeyField.trim() : "";
        sideEffect = sideEffect != null ? sideEffect : ToolSideEffect.READ_ONLY;
        if (sideEffect == ToolSideEffect.SIDE_EFFECTING && idempotencyKeyField.isBlank()) {
            throw new IllegalArgumentException("side-effecting tools require idempotencyKeyField");
        }
    }

    public ToolDescriptor(String name, String description, String inputSchema) {
        this(name, "1", name, description, inputSchema, "{\"type\":\"string\"}",
                Set.of(), Set.of(), Set.of(), ToolSensitivity.PUBLIC, Duration.ofSeconds(30),
                64 * 1024, "", ToolSideEffect.READ_ONLY, false);
    }

    public ToolDescriptor(
            String name,
            String description,
            String inputSchema,
            String outputSchema,
            Set<String> allowedAgentIds,
            Set<String> allowedTenantIds,
            Set<String> allowedPrincipals,
            ToolSensitivity sensitivity,
            Duration timeout,
            long maxResultBytes,
            String idempotencyKeyField,
            ToolSideEffect sideEffect,
            boolean confirmationRequired) {
        this(name, "1", name, description, inputSchema, outputSchema, allowedAgentIds,
                allowedTenantIds, allowedPrincipals, sensitivity, timeout, maxResultBytes,
                idempotencyKeyField, sideEffect, confirmationRequired);
    }

    private static String requireSchema(String schema, String field) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = schema.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return trimmed;
    }

    private static void requireId(String field, String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has invalid grammar");
        }
    }

    private static Set<String> immutableSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(values);
    }
}
