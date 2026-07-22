// 工具执行上下文
package com.jswarm.spi.lifecycle;

import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.time.CancellationToken;
import com.jswarm.spi.time.Deadline;

public record ToolContext(RunScope scope, Deadline deadline, CancellationToken cancellation) {
}
