// Adapter TCK 执行入口
package com.jswarm.tck;

@FunctionalInterface
public interface AdapterHarness {

    TckOutcome execute(TckFixture fixture);
}
