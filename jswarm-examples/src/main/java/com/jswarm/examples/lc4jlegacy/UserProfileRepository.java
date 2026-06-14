package com.jswarm.examples.lc4jlegacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class UserProfileRepository {

    public record UserProfile(String userId, String name, String vipLevel, int totalSpent) {
    }

    private final Map<String, UserProfile> byUserId = new LinkedHashMap<>();

    public UserProfileRepository() {
        byUserId.put("u001", new UserProfile("u001", "张三", "黄金VIP", 15800));
        byUserId.put("u002", new UserProfile("u002", "李四", "普通用户", 300));
    }

    public Optional<UserProfile> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byUserId.get(userId.trim().toLowerCase()));
    }
}
