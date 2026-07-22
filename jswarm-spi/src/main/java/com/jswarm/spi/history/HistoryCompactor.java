// Canonical 历史窗口压缩策略
package com.jswarm.spi.history;

import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.MessageRole;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class HistoryCompactor {

    private HistoryCompactor() {
    }

    public static HistoryCompaction window(List<CanonicalMessage> history, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        List<CanonicalMessage> source = history != null ? history : List.of();
        long total = bytes(source);
        if (total <= maxBytes) {
            return new HistoryCompaction(source, 0, 0, false);
        }
        List<CanonicalMessage> kept = new ArrayList<>();
        if (!source.isEmpty() && source.get(0).role() == MessageRole.SYSTEM) {
            kept.add(source.get(0));
        }
        long used = bytes(kept);
        for (int i = source.size() - 1; i >= 0; i--) {
            CanonicalMessage message = source.get(i);
            if (kept.contains(message)) {
                continue;
            }
            long size = bytes(List.of(message));
            if (used + size > maxBytes) {
                break;
            }
            kept.add(1, message);
            used += size;
        }
        int omitted = source.size() - kept.size();
        return new HistoryCompaction(kept, omitted, Math.max(0, total - used), true);
    }

    private static long bytes(List<CanonicalMessage> messages) {
        long total = 0;
        for (CanonicalMessage message : messages) {
            total += message.text().getBytes(StandardCharsets.UTF_8).length;
            for (var call : message.toolCalls()) {
                total += call.arguments().getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return total;
    }
}
