package com.jswarm.adapter.lc4j.tool;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.core.SwarmException;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Lc4jToolBridge {

    private static final Set<String> RESERVED_NAMES = Set.of("handoff", "delegate");

    private Lc4jToolBridge() {
    }

    public record BridgeResult(List<ToolSpecification> specs, ExternalToolExecutor executor) {
    }

    public static BridgeResult bridge(Object... toolBeans) {
        if (toolBeans == null || toolBeans.length == 0) {
            return new BridgeResult(List.of(), null);
        }

        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, DefaultToolExecutor> executorsByName = new HashMap<>();

        for (Object bean : toolBeans) {
            if (bean == null) {
                throw new SwarmException("Tool bean must not be null");
            }
            for (Method method : toolMethods(bean)) {
                ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                String name = spec.name();
                if (RESERVED_NAMES.contains(name)) {
                    throw new SwarmException("Tool name '" + name + "' is reserved by Jswarm orchestration");
                }
                if (executorsByName.containsKey(name)) {
                    throw new SwarmException("Duplicate tool name: " + name);
                }
                executorsByName.put(name, new DefaultToolExecutor(bean, method));
                specs.add(spec);
            }
        }

        try {
            ToolSpecifications.validateSpecifications(specs);
        } catch (IllegalArgumentException e) {
            throw new SwarmException(e.getMessage(), e);
        }

        ExternalToolExecutor executor = req -> {
            DefaultToolExecutor ex = executorsByName.get(req.name());
            if (ex == null) {
                throw new ToolNotHandledException(req.name());
            }
            return ex.execute(req, null);
        };

        return new BridgeResult(List.copyOf(specs), executor);
    }

    private static List<Method> toolMethods(Object bean) {
        List<Method> methods = new ArrayList<>();
        Class<?> clazz = bean.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return methods;
    }
}
