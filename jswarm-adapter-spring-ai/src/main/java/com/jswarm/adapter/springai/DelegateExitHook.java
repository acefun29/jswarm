package com.jswarm.adapter.springai;

import com.jswarm.core.SwarmContext;

@FunctionalInterface
public interface DelegateExitHook {
    void accept(SwarmContext context, String task, String result);
}
