// delegate 退出钩子三参函数式接口
package com.jswarm.adapter.lc4j;

import com.jswarm.core.SwarmContext;

@FunctionalInterface
public interface DelegateExitHook {

    void accept(SwarmContext context, String task, String result);
}
