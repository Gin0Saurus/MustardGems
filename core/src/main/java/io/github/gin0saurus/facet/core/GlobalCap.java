package io.github.gin0saurus.facet.core;

/**
 * A server-wide limited pool (e.g. how many top-tier upgrades can ever happen). The pool only
 * goes down when something is actually granted; raising the configured cap later adds the
 * difference without refunding anyone.
 */
public final class GlobalCap {

    private int cap;
    private int used;

    public GlobalCap(int cap, int used) {
        setCap(cap);
        this.used = Math.max(0, used);
    }

    /** cap < 0 in config means "unlimited". */
    public boolean unlimited() { return cap == Integer.MAX_VALUE; }

    public int remaining() { return unlimited() ? Integer.MAX_VALUE : Math.max(0, cap - used); }

    public int used() { return used; }

    public boolean tryTake() {
        if (remaining() <= 0) return false;
        used++;
        return true;
    }

    /** Undo a take if the action it paid for failed afterwards. */
    public void giveBack() { if (used > 0) used--; }

    public void setCap(int newCap) { this.cap = newCap < 0 ? Integer.MAX_VALUE : newCap; }
}
