// 运行默认配置
package com.jswarm.spi.run;

import java.time.Duration;

public final class RunDefaults {

    public static final Duration MODEL_TIMEOUT = Duration.ofSeconds(60);
    public static final int MAX_TURNS = 10;
    public static final int MAX_RECOVERY_ATTEMPTS = 2;
    public static final int MAX_DELEGATE_DEPTH = 3;
    public static final long UNLIMITED = Long.MAX_VALUE;

    private RunDefaults() {
    }
}
