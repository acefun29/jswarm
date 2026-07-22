// LangChain4j 与 Canonical 工具描述互转
package com.jswarm.adapter.lc4j.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.message.ToolDescriptor;
import dev.langchain4j.agent.tool.ToolSpecification;

public final class Lc4jToolCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ToolDescriptor decode(ToolSpecification specification) {
        try {
            JsonNode root = MAPPER.readTree(specification.toJson());
            JsonNode parameters = root.get("parameters");
            return new ToolDescriptor(
                    specification.name(),
                    specification.description(),
                    parameters != null ? parameters.toString() : "{\"type\":\"object\"}");
        } catch (Exception e) {
            throw new SwarmException("Failed to decode LangChain4j tool specification", e);
        }
    }

    public ToolSpecification encode(ToolDescriptor descriptor) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("name", descriptor.name());
            root.put("description", descriptor.description());
            root.set("parameters", MAPPER.readTree(descriptor.inputSchema()));
            return ToolSpecification.fromJson(MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            throw new SwarmException("Failed to encode LangChain4j tool specification", e);
        }
    }
}
