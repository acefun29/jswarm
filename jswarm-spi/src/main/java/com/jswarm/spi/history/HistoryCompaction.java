// 历史压缩结果
package com.jswarm.spi.history;

import com.jswarm.spi.message.CanonicalMessage;

import java.util.List;

public record HistoryCompaction(
        List<CanonicalMessage> messages,
        int omittedMessages,
        long omittedBytes,
        boolean compacted) {

    public HistoryCompaction {
        messages = messages != null ? List.copyOf(messages) : List.of();
    }
}
