// 模型调用请求
package com.jswarm.spi.lifecycle;

import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.ToolDescriptor;

import java.util.List;

public record ModelRequest(
        String agentId,
        List<CanonicalMessage> messages,
        List<ToolDescriptor> tools,
        boolean streaming) {

    public ModelRequest {
        messages = messages != null ? List.copyOf(messages) : List.of();
        tools = tools != null ? List.copyOf(tools) : List.of();
    }
}
