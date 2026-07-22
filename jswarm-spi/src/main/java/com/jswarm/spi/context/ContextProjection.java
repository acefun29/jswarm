// delegate 子 scope 上下文投影
package com.jswarm.spi.context;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContextProjection {

    private ContextProjection() {
    }

    public static ContextSnapshot forDelegate(ContextSnapshot parent) {
        if (parent == null) {
            return ContextSnapshot.empty();
        }
        Map<String, Object> projected = new LinkedHashMap<>();
        Map<String, Sensitivity> projectedSens = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parent.asMap().entrySet()) {
            Sensitivity s = parent.sensitivity(entry.getKey());
            if (s == Sensitivity.PUBLIC || s == Sensitivity.INTERNAL) {
                projected.put(entry.getKey(), entry.getValue());
                projectedSens.put(entry.getKey(), s);
            }
        }
        return ContextSnapshot.fromEntries(projected, projectedSens);
    }
}
