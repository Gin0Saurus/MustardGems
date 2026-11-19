package io.github.gin0saurus.facet.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which gem items in a set of inventories must be removed.
 *
 * <p>Pure logic, no Minecraft types: the Paper layer turns inventories into {@link Found} rows,
 * asks this class what to purge, and deletes those slots. Order matters - the first VALID copy
 * of an id that is seen wins, so callers should list the owner's own inventory first.
 */
public final class DupeScan {

    private DupeScan() {}

    /**
     * @param holder who is holding the item (player UUID), or null for a container/ground item
     * @param slot   opaque slot reference handed back to the caller
     */
    public record Found(UUID holder, Object slot, Token token, int amount) {}

    public record Purge(Found found, String reason) {}

    public static List<Purge> scan(Ledger ledger, List<Found> items) {
        List<Purge> purge = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Found f : items) {
            if (f.token() == null) {
                purge.add(new Purge(f, "unreadable gem tag"));
                continue;
            }
            Ledger.Verdict v = ledger.check(f.token(), f.holder());
            if (v != Ledger.Verdict.VALID) {
                purge.add(new Purge(f, v.name().toLowerCase()));
                continue;
            }
            if (!seen.add(f.token().id())) {
                purge.add(new Purge(f, "duplicate copy"));
                continue;
            }
            if (f.amount() > 1) {
                // A stacked gem is always a dupe: keep the stack slot but the caller trims it to 1.
                purge.add(new Purge(f, "stacked copies"));
            }
        }
        return purge;
    }
}
