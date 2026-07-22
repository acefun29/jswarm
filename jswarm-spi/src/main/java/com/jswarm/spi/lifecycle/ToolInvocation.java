// 工具调用请求
package com.jswarm.spi.lifecycle;

public record ToolInvocation(String callId, String toolName, String arguments) {
}
