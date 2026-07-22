// 模型网关 SPI，计划 03 迁移实现
package com.jswarm.spi.lifecycle;

import com.jswarm.spi.run.RunScope;

/**
 * 线程安全：实现类须为无状态或内部同步；同一 RunScope 不可跨 run 复用。
 */
public interface ModelGateway {

    ModelResult invoke(ModelRequest request, RunScope scope);
}
