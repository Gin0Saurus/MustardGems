package io.github.gin0saurus.facet.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Stops reward farming: a killer only gets paid for the same victim once per window.
 * Tracked per (killer, victim) pair so farming one alt never blocks rewards from anyone else.
 */
public final class PairLimiter {

    private final Map<String, Long> lastClaim = new HashMap<>();
    private final LongSupplier clock;

    public PairLimiter(LongSupplier clockMillis) { this.clock = clockMillis; }

    /** Returns true and records the claim if allowed. A window of 0 or less always allows. */
    public boolean tryClaim(UUID killer, UUID victim, long windowMillis) {
        if (killer.equals(victim)) return false;
        long now = clock.getAsLong();
        if (windowMillis <= 0) return true;
        String key = killer + ">" + victim;
        Long last = lastClaim.get(key);
        if (last != null && now - last < windowMillis) return false;
        lastClaim.put(key, now);
        if (lastClaim.size() > 10_000) lastClaim.values().removeIf(t -> now - t >= windowMillis);
        return true;
    }
}
