package com.heartvault.cooldown;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final Map<UUID, Map<String, Long>> nextAllowedMillis = new ConcurrentHashMap<>();

    public long remainingSeconds(UUID uuid, String key) {
        long now = System.currentTimeMillis();
        Long until = nextAllowedMillis.getOrDefault(uuid, Map.of()).get(key);
        if (until == null) {
            return 0;
        }
        long remaining = until - now;
        return Math.max(0, (remaining + 999) / 1000);
    }

    public boolean tryUse(UUID uuid, String key, long cooldownSeconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> map = nextAllowedMillis.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        long until = map.getOrDefault(key, 0L);
        if (until > now) {
            return false;
        }
        map.put(key, now + cooldownSeconds * 1000L);
        return true;
    }

    public void clear(UUID uuid) {
        nextAllowedMillis.remove(uuid);
    }
}

