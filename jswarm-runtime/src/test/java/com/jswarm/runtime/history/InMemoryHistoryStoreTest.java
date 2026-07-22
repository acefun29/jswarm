// 历史版本与 checksum 测试
package com.jswarm.runtime.history;

import com.jswarm.spi.history.HistoryChecksum;
import com.jswarm.spi.history.HistoryConflictException;
import com.jswarm.spi.history.HistoryRecord;
import com.jswarm.spi.history.HistoryCompactor;
import com.jswarm.spi.id.RunId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.message.CanonicalMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryHistoryStoreTest {

    @Test
    void shouldVersionSavesAndRejectStaleWriter() {
        InMemoryHistoryStore store = new InMemoryHistoryStore();
        List<CanonicalMessage> messages = List.of(CanonicalMessage.user("hello"));
        HistoryRecord initial = record(messages);

        HistoryRecord saved = store.create("session", initial);

        assertEquals(1, saved.version());
        assertThrows(HistoryConflictException.class,
                () -> store.save("session", record(List.of(CanonicalMessage.user("stale"))), 0));
        assertEquals(saved.checksum(), HistoryChecksum.sha256(saved.messages()));
    }

    @Test
    void shouldLoadImmutableHistory() {
        InMemoryHistoryStore store = new InMemoryHistoryStore();
        HistoryRecord saved = store.create("session", record(List.of(CanonicalMessage.user("hello"))));

        assertEquals("hello", store.load("session").orElseThrow().messages().get(0).text());
        assertThrows(UnsupportedOperationException.class,
                () -> saved.messages().add(CanonicalMessage.user("mutate")));
    }

    @Test
    void shouldReportExplicitCompactionLoss() {
        var result = HistoryCompactor.window(List.of(
                CanonicalMessage.system("system"),
                CanonicalMessage.user("one"),
                CanonicalMessage.user("two")), 8);

        assertEquals(true, result.compacted());
        assertEquals(2, result.omittedMessages());
    }

    private static HistoryRecord record(List<CanonicalMessage> messages) {
        return new HistoryRecord(1, RunId.generate(), SwarmVersion.of("s"), "tenant-a", 0,
                HistoryChecksum.sha256(messages), messages, "COMPLETED", java.util.Map.of());
    }
}
