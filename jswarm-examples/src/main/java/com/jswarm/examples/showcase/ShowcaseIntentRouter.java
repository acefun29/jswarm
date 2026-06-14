// Showcase 意图识别：router 未调 handoff 时的兜底路由
package com.jswarm.examples.showcase;

import java.util.Locale;
import java.util.Optional;

public final class ShowcaseIntentRouter {

    private ShowcaseIntentRouter() {
    }

    public static Optional<String> handoffTarget(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String m = userMessage.toLowerCase(Locale.ROOT);
        if (containsAny(m, "激活", "激活码", "安装", "故障", "报错", "无效", "排查", "软件")) {
            return Optional.of("tech");
        }
        if (containsAny(m, "订单", "物流", "退换货", "ord-", "运单", "发货")) {
            return Optional.of("order");
        }
        if (containsAny(m, "价格", "优惠", "购买", "旗舰", "多少钱", "咨询", "套餐")) {
            return Optional.of("sales");
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
