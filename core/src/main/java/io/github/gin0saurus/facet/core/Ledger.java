package io.github.gin0saurus.facet.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The single source of truth for every power item (gem) on the server.
 *
 * <p>Items in the world are only <em>tokens</em>: they carry an id and a version. A token is
 * valid only if the ledger has a live record with the same id and the same version. Every
 * state change (bind, unbind, upgrade, reissue) bumps the version, so any copy of an item made
 * before that change - by a dupe glitch, creative middle-click, a rollback or another plugin -
 * becomes worthless the moment the real one changes hands.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>a player has at most one active (bound) gem;</li>
 *   <li>a bound gem belongs to exactly one player;</li>
 *   <li>destroyed gems never come back.</li>
 * </ul>
 *
 * Not thread-safe: call it from the server thread only.
 */
public final class Ledger {

    public enum Verdict {
        /** Token matches a live record and may be held by this holder. */
        VALID,
        /** Record exists but the item is an old copy (version mismatch). */
        STALE,
        /** No record with this id: forged, or from another server/world reset. */
        UNKNOWN,
        /** Record was destroyed (consumed, admin-removed). */
        DESTROYED,
        /** Bound gem held by someone who is not its owner. */
        FOREIGN
    }

    public static final class GemRecord {
        private final UUID id;
        private String type;
        private int tier;
        private UUID owner;
        private int version;
        private boolean destroyed;

        GemRecord(UUID id, String type, int tier, UUID owner, int version, boolean destroyed) {
            this.id = id;
            this.type = type;
            this.tier = tier;
            this.owner = owner;
            this.version = version;
            this.destroyed = destroyed;
        }

        public UUID id() { return id; }
        public String type() { return type; }
        public int tier() { return tier; }
        public UUID owner() { return owner; }
        public boolean bound() { return owner != null; }
        public int version() { return version; }
        public boolean destroyed() { return destroyed; }
        public Token token() { return new Token(id, version); }

        @Override public String toString() {
            return "Gem[" + id + " " + type + " t" + tier + " v" + version + (owner == null ? " unbound" : " owner=" + owner) + (destroyed ? " DESTROYED" : "") + "]";
        }
    }

    private final Map<UUID, GemRecord> records = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeByPlayer = new HashMap<>();
    private final Supplier<UUID> ids;
    private boolean dirty;

    public Ledger() { this(UUID::randomUUID); }

    /** For tests: deterministic ids. */
    public Ledger(Supplier<UUID> ids) { this.ids = Objects.requireNonNull(ids); }

    public GemRecord create(String type, int tier, UUID owner) {
        Objects.requireNonNull(type, "type");
        if (owner != null && activeByPlayer.containsKey(owner)) {
            throw new IllegalStateException("Player already has an active gem; unbind it first");
        }
        UUID id;
        do { id = ids.get(); } while (records.containsKey(id));
        GemRecord r = new GemRecord(id, type, Math.max(0, tier), owner, 1, false);
        records.put(id, r);
        if (owner != null) activeByPlayer.put(owner, id);
        dirty = true;
        return r;
    }

    public Optional<GemRecord> get(UUID id) { return Optional.ofNullable(records.get(id)); }

    public Optional<GemRecord> activeOf(UUID player) {
        UUID id = activeByPlayer.get(player);
        return id == null ? Optional.empty() : get(id);
    }

    public Verdict check(Token token, UUID holder) {
        if (token == null) return Verdict.UNKNOWN;
        GemRecord r = records.get(token.id());
        if (r == null) return Verdict.UNKNOWN;
        if (r.destroyed) return Verdict.DESTROYED;
        if (r.version != token.version()) return Verdict.STALE;
        if (r.owner != null && holder != null && !r.owner.equals(holder)) return Verdict.FOREIGN;
        return Verdict.VALID;
    }

    /**
     * Binds an unbound gem to a player. Returns the record that was previously active for that
     * player (now unbound, with a bumped version) so the caller can hand it back as an unbound
     * item, or empty if the player had none.
     */
    public Optional<GemRecord> bind(UUID id, UUID player) {
        GemRecord r = live(id);
        if (r.owner != null) throw new IllegalStateException("Gem is already bound");
        Optional<GemRecord> previous = Optional.empty();
        UUID prevId = activeByPlayer.get(player);
        if (prevId != null) {
            GemRecord prev = records.get(prevId);
            if (prev != null && !prev.destroyed) {
                prev.owner = null;
                prev.version++;
                previous = Optional.of(prev);
            }
        }
        r.owner = player;
        r.version++;
        activeByPlayer.put(player, id);
        dirty = true;
        return previous;
    }

    /** Makes a bound gem unbound (death drop, voluntary unbind). */
    public GemRecord unbind(UUID id) {
        GemRecord r = live(id);
        if (r.owner != null) {
            activeByPlayer.remove(r.owner, id);
            r.owner = null;
        }
        r.version++;
        dirty = true;
        return r;
    }

    /** Raises the tier by one. Caller checks costs and the max tier. */
    public GemRecord upgrade(UUID id) {
        GemRecord r = live(id);
        r.tier++;
        r.version++;
        dirty = true;
        return r;
    }

    public GemRecord setTier(UUID id, int tier) {
        GemRecord r = live(id);
        r.tier = Math.max(0, tier);
        r.version++;
        dirty = true;
        return r;
    }

    /** Invalidates every existing copy and returns the record for a fresh item (lost-item recovery). */
    public GemRecord reissue(UUID id) {
        GemRecord r = live(id);
        r.version++;
        dirty = true;
        return r;
    }

    public void destroy(UUID id) {
        GemRecord r = records.get(id);
        if (r == null || r.destroyed) return;
        if (r.owner != null) activeByPlayer.remove(r.owner, id);
        r.owner = null;
        r.destroyed = true;
        r.version++;
        dirty = true;
    }

    /** Clears a player's active gem entirely (admin /gem clear). */
    public Optional<GemRecord> clearPlayer(UUID player) {
        UUID id = activeByPlayer.get(player);
        if (id == null) return Optional.empty();
        destroy(id);
        return Optional.ofNullable(records.get(id));
    }

    public Collection<GemRecord> all() { return Collections.unmodifiableCollection(records.values()); }

    public int liveCount() {
        int n = 0;
        for (GemRecord r : records.values()) if (!r.destroyed) n++;
        return n;
    }

    public boolean dirty() { return dirty; }
    public void markClean() { dirty = false; }

    private GemRecord live(UUID id) {
        GemRecord r = records.get(id);
        if (r == null) throw new IllegalArgumentException("Unknown gem " + id);
        if (r.destroyed) throw new IllegalStateException("Gem was destroyed " + id);
        return r;
    }

    // ---------------------------------------------------------------- persistence

    /** Plain maps/lists only, so any YAML or JSON writer can store it. */
    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GemRecord r : records.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id.toString());
            m.put("type", r.type);
            m.put("tier", r.tier);
            m.put("version", r.version);
            if (r.owner != null) m.put("owner", r.owner.toString());
            if (r.destroyed) m.put("destroyed", true);
            out.add(m);
        }
        return out;
    }

    /**
     * Restores from {@link #snapshot()}. Bad rows are skipped and reported, never fatal: a broken
     * data file must not stop the server from starting.
     */
    public List<String> restore(List<? extends Map<?, ?>> rows) {
        records.clear();
        activeByPlayer.clear();
        List<String> problems = new ArrayList<>();
        if (rows == null) return problems;
        int i = 0;
        for (Map<?, ?> m : rows) {
            i++;
            try {
                UUID id = UUID.fromString(String.valueOf(m.get("id")));
                String type = String.valueOf(m.get("type"));
                int tier = toInt(m.get("tier"), 0);
                int version = Math.max(1, toInt(m.get("version"), 1));
                Object o = m.get("owner");
                UUID owner = o == null ? null : UUID.fromString(String.valueOf(o));
                boolean destroyed = Boolean.TRUE.equals(m.get("destroyed"));
                if (destroyed) owner = null;
                if (owner != null && activeByPlayer.containsKey(owner)) {
                    problems.add("row " + i + ": player " + owner + " had two active gems; kept the first, unbound " + id);
                    owner = null;
                    version++;
                }
                GemRecord r = new GemRecord(id, type, tier, owner, version, destroyed);
                records.put(id, r);
                if (owner != null) activeByPlayer.put(owner, id);
            } catch (RuntimeException e) {
                problems.add("row " + i + ": skipped (" + e.getMessage() + ")");
            }
        }
        dirty = !problems.isEmpty();
        return problems;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return o == null ? def : Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
