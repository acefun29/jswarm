// 请求级 Showcase 轨迹采集
package com.jswarm.examples.showcase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ShowcaseEventCollector {

    private static final ThreadLocal<ShowcaseEventCollector> CURRENT = new ThreadLocal<>();

    private final List<ShowcaseEvent> events = new ArrayList<>();

    public static ShowcaseEventCollector start() {
        ShowcaseEventCollector c = new ShowcaseEventCollector();
        CURRENT.set(c);
        return c;
    }

    public static ShowcaseEventCollector current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public void add(ShowcaseEvent event) {
        events.add(event);
    }

    public List<ShowcaseEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public List<Map<String, String>> snapshotMaps() {
        return events.stream().map(ShowcaseEvent::toMap).toList();
    }
}
