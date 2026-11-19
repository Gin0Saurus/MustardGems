package io.github.gin0saurus.facet.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Per-player cooldowns keyed by a string ("ability", "swap", ...). Cooldowns belong to the
 * player, not the item, so swapping or re-binding a gem can never reset them, and they survive
 * relogs and restarts through {@link #snapshot()}.
 */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();
    private final LongSupplier clock;

    public Cooldowns(LongSupplier clockMillis) { this.clock = clockMillis; }

    public boolean ready(UUID player, String key) { return remainingMillis(player, key) == 0; }

    public long remainingMillis(UUID player, String key) {
        Map<String, Long> m = readyAt.get(player);
        if (m == null) return 0;
        Long t = m.get(key);
        if (t == null) return 0;
        long left = t - clock.getAsLong();
        return Math.max(0, left);
    }

    /** Whole seconds left, rounded up, so the action bar never shows "0s" while still blocked. */
    public long remainingSeconds(UUID player, String key) {
        long ms = remainingMillis(player, key);
        return ms == 0 ? 0 : (ms + 999) / 1000;
    }

    public void start(UUID player, String key, long millis) {
        if (millis <= 0) { clear(player, key); return; }
        readyAt.computeIfAbsent(player, k -> new HashMap<>()).put(key, clock.getAsLong() + millis);
    }

    public void clear(UUID player, String key) {
        Map<String, Long> m = readyAt.get(player);
        if (m != null) m.remove(key);
    }

    public void clearAll(UUID player) { readyAt.remove(player); }

    /** Only cooldowns still running are saved; expired ones are dropped. */
    public Map<String, Map<String, Long>> snapshot() {
        long now = clock.getAsLong();
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Long>> e : readyAt.entrySet()) {
            Map<String, Long> live = new LinkedHashMap<>();
            for (Map.Entry<String, Long> c : e.getValue().entrySet()) if (c.getValue() > now) live.put(c.getKey(), c.getValue());
            if (!live.isEmpty()) out.put(e.getKey().toString(), live);
        }
        return out;
    }

    public void restore(Map<?, ?> data) {
        readyAt.clear();
        if (data == null) return;
        for (Map.Entry<?, ?> e : data.entrySet()) {
            try {
                UUID id = UUID.fromString(String.valueOf(e.getKey()));
                if (!(e.getValue() instanceof Map<?, ?> m)) continue;
                Map<String, Long> into = new HashMap<>();
                for (Map.Entry<?, ?> c : m.entrySet()) {
                    if (c.getValue() instanceof Number n) into.put(String.valueOf(c.getKey()), n.longValue());
                }
                if (!into.isEmpty()) readyAt.put(id, into);
            } catch (IllegalArgumentException ignored) {
                // bad uuid row: skip
            }
        }
    }

    /** Drops expired entries so memory does not grow with every player who ever joined. */
    public void prune() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<UUID, Map<String, Long>>> it = readyAt.entrySet().iterator();
        while (it.hasNext()) {
            Map<String, Long> m = it.next().getValue();
            m.values().removeIf(t -> t <= now);
            if (m.isEmpty()) it.remove();
        }
    }
}
