// 可关闭执行器生命周期接口
package com.jswarm.spi.lifecycle;

/**
 * 线程安全：实现类须支持并发提交与关闭；close 后拒绝新任务。
 */
public interface CloseableExecutor extends AutoCloseable {

    @Override
    void close();
}
