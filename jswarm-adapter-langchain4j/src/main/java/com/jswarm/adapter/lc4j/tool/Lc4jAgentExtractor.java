package com.jswarm.adapter.lc4j.tool;

import com.jswarm.core.SwarmException;
import dev.langchain4j.service.SystemMessage;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Lc4jAgentExtractor {

    private Lc4jAgentExtractor() {
    }

    public static String extractInstructions(Class<?> aiServiceInterface) {
        if (aiServiceInterface == null || !aiServiceInterface.isInterface()) {
            throw new SwarmException("AiService interface must be a non-null interface type");
        }

        List<Method> annotated = new ArrayList<>();
        for (Method method : aiServiceInterface.getMethods()) {
            if (method.isAnnotationPresent(SystemMessage.class)) {
                annotated.add(method);
            }
        }

        if (annotated.size() != 1) {
            throw new SwarmException("AiService interface must have exactly one @SystemMessage method, found: "
                    + annotated.size());
        }

        SystemMessage systemMessage = annotated.get(0).getAnnotation(SystemMessage.class);
        if (!systemMessage.fromResource().isBlank()) {
            return readResource(aiServiceInterface, systemMessage.fromResource());
        }

        String[] value = systemMessage.value();
        if (value.length == 0 || (value.length == 1 && value[0].isEmpty())) {
            throw new SwarmException("@SystemMessage value is empty");
        }
        return String.join(systemMessage.delimiter(), value);
    }

    private static String readResource(Class<?> clazz, String resource) {
        try (InputStream input = clazz.getResourceAsStream(resource)) {
            if (input == null) {
                throw new SwarmException("Resource not found: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SwarmException("Failed to read resource: " + resource, e);
        }
    }
}
