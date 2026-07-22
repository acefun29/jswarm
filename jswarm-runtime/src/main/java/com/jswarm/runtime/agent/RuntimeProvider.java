// Adapter 提供的 Agent 运行能力解析器
package com.jswarm.runtime.agent;

import com.jswarm.core.Agent;

@FunctionalInterface
public interface RuntimeProvider {

    AgentRuntime resolve(Agent agent);
}
