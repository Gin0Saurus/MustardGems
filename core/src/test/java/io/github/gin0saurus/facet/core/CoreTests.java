package io.github.gin0saurus.facet.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dependency-free test runner for the core. Every public static void method whose name starts
 * with "test" is run. Exit code 1 on any failure, so CI fails the build.
 *
 * Run: java -cp core/build/classes io.github.gin0saurus.facet.core.CoreTests
 */
public final class CoreTests {

    static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    public static void main(String[] args) throws Exception {
        int pass = 0, fail = 0;
        List<String> failures = new ArrayList<>();
        for (Method m : CoreTests.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("test") || !Modifier.isStatic(m.getModifiers())) continue;
            try {
                m.invoke(null);
                pass++;
                System.out.println("PASS " + m.getName());
            } catch (java.lang.reflect.InvocationTargetException e) {
                fail++;
                failures.add(m.getName() + ": " + e.getCause());
                System.out.println("FAIL " + m.getName() + " -> " + e.getCause());
            }
        }
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) {
            failures.forEach(System.out::println);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ helpers

    static void eq(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void yes(boolean b, String what) { if (!b) throw new AssertionError(what); }

    static void throwsAny(Runnable r, String what) {
        try { r.run(); } catch (RuntimeException e) { return; }
        throw new AssertionError(what + ": expected an exception");
    }

    static Ledger ledger() {
        AtomicLong n = new AtomicLong();
        return new Ledger(() -> new UUID(0xFACE, n.incrementAndGet()));
    }

    // ------------------------------------------------------------ ledger: ownership + dupes

    public static void testNewBoundGemIsValidForOwnerOnly() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("strength", 0, ALICE);
        eq(Ledger.Verdict.VALID, l.check(g.token(), ALICE), "owner");
        eq(Ledger.Verdict.FOREIGN, l.check(g.token(), BOB), "someone else holding a bound gem");
        eq(g.id(), l.activeOf(ALICE).orElseThrow().id(), "active gem");
    }

    public static void testCopyMadeBeforeBindBecomesWorthless() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("fire", 0, null); // crafted / dropped, unbound
        Token copy = g.token();                         // dupe made while unbound
        l.bind(g.id(), BOB);
        eq(Ledger.Verdict.STALE, l.check(copy, BOB), "old unbound copy after bind");
        eq(Ledger.Verdict.VALID, l.check(g.token(), BOB), "real item");
    }

    public static void testDeathDropInvalidatesKeepInventoryCopy() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("speed", 2, ALICE);
        Token keptByVictim = g.token();
        l.unbind(g.id());                               // dropped on death as loot
        eq(Ledger.Verdict.STALE, l.check(keptByVictim, ALICE), "victim's leftover copy");
        yes(l.activeOf(ALICE).isEmpty(), "victim has no active gem");
        Token loot = l.get(g.id()).orElseThrow().token();
        eq(Ledger.Verdict.VALID, l.check(loot, BOB), "anyone may pick up unbound loot");
        l.bind(g.id(), BOB);
        eq(2, l.get(g.id()).orElseThrow().tier(), "tier survives the drop");
    }

    public static void testBindingSecondGemUnbindsFirst() {
        Ledger l = ledger();
        Ledger.GemRecord first = l.create("strength", 1, ALICE);
        Token firstOldItem = first.token();
        Ledger.GemRecord second = l.create("life", 0, null);
        var previous = l.bind(second.id(), ALICE);
        yes(previous.isPresent(), "previous gem returned");
        eq(first.id(), previous.get().id(), "previous id");
        yes(!previous.get().bound(), "previous is unbound now");
        eq(Ledger.Verdict.STALE, l.check(firstOldItem, ALICE), "old item of first gem");
        eq(second.id(), l.activeOf(ALICE).orElseThrow().id(), "new active gem");
    }

    public static void testCannotCreateSecondActiveGemDirectly() {
        Ledger l = ledger();
        l.create("strength", 0, ALICE);
        throwsAny(() -> l.create("fire", 0, ALICE), "second bound gem for same player");
    }

    public static void testCannotBindAlreadyBoundGem() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("strength", 0, ALICE);
        throwsAny(() -> l.bind(g.id(), BOB), "stealing a bound gem");
    }

    public static void testUpgradeInvalidatesOldCopies() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("ice", 0, ALICE);
        Token before = g.token();
        l.upgrade(g.id());
        eq(Ledger.Verdict.STALE, l.check(before, ALICE), "pre-upgrade copy");
        eq(1, l.get(g.id()).orElseThrow().tier(), "tier");
    }

    public static void testReissueKillsHiddenCopies() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("storm", 3, ALICE);
        Token hiddenInChest = g.token();
        l.reissue(g.id());
        eq(Ledger.Verdict.STALE, l.check(hiddenInChest, ALICE), "copy after /gem recover");
        eq(Ledger.Verdict.VALID, l.check(l.get(g.id()).orElseThrow().token(), ALICE), "new item");
    }

    public static void testDestroyedGemsNeverComeBack() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("wealth", 0, ALICE);
        Token t = g.token();
        l.destroy(g.id());
        eq(Ledger.Verdict.DESTROYED, l.check(new Token(t.id(), l.get(t.id()).orElseThrow().version()), ALICE), "destroyed");
        yes(l.activeOf(ALICE).isEmpty(), "no active gem");
        throwsAny(() -> l.bind(g.id(), BOB), "binding a destroyed gem");
        throwsAny(() -> l.upgrade(g.id()), "upgrading a destroyed gem");
    }

    public static void testUnknownTokens() {
        Ledger l = ledger();
        eq(Ledger.Verdict.UNKNOWN, l.check(new Token(UUID.randomUUID(), 1), ALICE), "forged id");
        eq(Ledger.Verdict.UNKNOWN, l.check(null, ALICE), "null token");
    }

    public static void testLedgerRoundTrip() {
        Ledger l = ledger();
        Ledger.GemRecord a = l.create("strength", 2, ALICE);
        Ledger.GemRecord b = l.create("fire", 1, null);
        Ledger.GemRecord c = l.create("ice", 0, BOB);
        l.destroy(c.id());
        Ledger copy = ledger();
        List<String> problems = copy.restore(l.snapshot());
        yes(problems.isEmpty(), "no problems: " + problems);
        eq(Ledger.Verdict.VALID, copy.check(a.token(), ALICE), "a after restore");
        eq(Ledger.Verdict.VALID, copy.check(b.token(), BOB), "b after restore");
        eq(Ledger.Verdict.DESTROYED, copy.check(copy.get(c.id()).orElseThrow().token(), BOB), "c after restore");
        eq(a.id(), copy.activeOf(ALICE).orElseThrow().id(), "active restored");
        yes(copy.activeOf(BOB).isEmpty(), "destroyed gem is not active");
    }

    public static void testRestoreRepairsTwoActiveGemsAndSkipsGarbage() {
        Ledger l = ledger();
        List<Map<String, Object>> rows = List.of(
            Map.of("id", new UUID(1, 1).toString(), "type", "strength", "tier", 0, "version", 3, "owner", ALICE.toString()),
            Map.of("id", new UUID(1, 2).toString(), "type", "fire", "tier", 0, "version", 1, "owner", ALICE.toString()),
            Map.of("id", "not-a-uuid", "type", "x"),
            Map.of("type", "missing id"));
        List<String> problems = l.restore(rows);
        eq(3, problems.size(), "problems reported");
        eq(new UUID(1, 1), l.activeOf(ALICE).orElseThrow().id(), "first active kept");
        yes(!l.get(new UUID(1, 2)).orElseThrow().bound(), "second unbound");
        eq(Ledger.Verdict.STALE, l.check(new Token(new UUID(1, 2), 1), ALICE), "second's old item is stale");
        yes(l.dirty(), "repaired ledger marked dirty so it gets saved");
    }

    // ------------------------------------------------------------ dupe scan

    public static void testScanRemovesDuplicatesStacksAndJunk() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("strength", 0, ALICE);
        Ledger.GemRecord loot = l.create("fire", 0, null);
        Token stale = g.token();
        l.reissue(g.id());
        Token real = l.get(g.id()).orElseThrow().token();
        List<DupeScan.Found> found = List.of(
            new DupeScan.Found(ALICE, "slot0", real, 1),
            new DupeScan.Found(ALICE, "slot1", real, 1),          // creative clone
            new DupeScan.Found(ALICE, "slot2", stale, 1),         // old copy
            new DupeScan.Found(ALICE, "slot3", null, 1),          // broken tag
            new DupeScan.Found(ALICE, "slot4", loot.token(), 3),  // stacked loot
            new DupeScan.Found(BOB, "slot5", real, 1));           // bob holding alice's gem
        List<DupeScan.Purge> purge = DupeScan.scan(l, found);
        List<Object> slots = purge.stream().map(p -> p.found().slot()).toList();
        eq(List.of("slot1", "slot2", "slot3", "slot4", "slot5"), slots, "purged slots");
        yes(!slots.contains("slot0"), "first real copy kept");
    }

    public static void testScanKeepsCleanInventory() {
        Ledger l = ledger();
        Ledger.GemRecord g = l.create("life", 0, ALICE);
        Ledger.GemRecord loot = l.create("ice", 1, null);
        List<DupeScan.Purge> purge = DupeScan.scan(l, List.of(
            new DupeScan.Found(ALICE, 0, g.token(), 1),
            new DupeScan.Found(ALICE, 1, loot.token(), 1)));
        yes(purge.isEmpty(), "nothing purged: " + purge);
    }

    // ------------------------------------------------------------ cooldowns

    public static void testCooldownsBelongToPlayerNotItem() {
        AtomicLong now = new AtomicLong(1_000_000);
        Cooldowns c = new Cooldowns(now::get);
        c.start(ALICE, "ability", 30_000);
        now.addAndGet(10_000);
        eq(20L, c.remainingSeconds(ALICE, "ability"), "seconds left");
        yes(!c.ready(ALICE, "ability"), "still cooling");
        yes(c.ready(BOB, "ability"), "other player unaffected");
        now.addAndGet(20_000);
        yes(c.ready(ALICE, "ability"), "ready after 30s");
    }

    public static void testCooldownSecondsRoundUp() {
        AtomicLong now = new AtomicLong(0);
        Cooldowns c = new Cooldowns(now::get);
        c.start(ALICE, "ability", 1_500);
        now.set(1_499);
        eq(1L, c.remainingSeconds(ALICE, "ability"), "1ms left shows 1s, never 0s");
    }

    public static void testCooldownsSurviveRestartButExpiredOnesDrop() {
        AtomicLong now = new AtomicLong(0);
        Cooldowns c = new Cooldowns(now::get);
        c.start(ALICE, "ability", 60_000);
        c.start(BOB, "ability", 1_000);
        now.set(5_000);
        Map<String, Map<String, Long>> snap = c.snapshot();
        yes(snap.containsKey(ALICE.toString()), "running cooldown saved");
        yes(!snap.containsKey(BOB.toString()), "expired cooldown dropped");
        Cooldowns again = new Cooldowns(now::get);
        again.restore(snap);
        eq(55L, again.remainingSeconds(ALICE, "ability"), "restored remaining");
    }

    public static void testZeroCooldownClears() {
        AtomicLong now = new AtomicLong(0);
        Cooldowns c = new Cooldowns(now::get);
        c.start(ALICE, "swap", 10_000);
        c.start(ALICE, "swap", 0);
        yes(c.ready(ALICE, "swap"), "0 clears");
    }

    // ------------------------------------------------------------ reward farming

    public static void testShardFarmLimiter() {
        AtomicLong now = new AtomicLong(0);
        PairLimiter p = new PairLimiter(now::get);
        long window = 10 * 60_000;
        yes(p.tryClaim(ALICE, BOB, window), "first kill pays");
        yes(!p.tryClaim(ALICE, BOB, window), "repeat kill blocked");
        yes(p.tryClaim(BOB, ALICE, window), "reverse pair is separate");
        now.addAndGet(window);
        yes(p.tryClaim(ALICE, BOB, window), "pays again after window");
        yes(!p.tryClaim(ALICE, ALICE, window), "self kill never pays");
        yes(p.tryClaim(ALICE, BOB, 0) && p.tryClaim(ALICE, BOB, 0), "window 0 disables the limit");
    }

    // ------------------------------------------------------------ server cap

    public static void testServerCap() {
        GlobalCap cap = new GlobalCap(2, 0);
        yes(cap.tryTake() && cap.tryTake(), "two allowed");
        yes(!cap.tryTake(), "third refused");
        eq(0, cap.remaining(), "remaining");
        cap.setCap(5);
        eq(3, cap.remaining(), "raising the cap adds the difference, no refunds");
        cap.giveBack();
        eq(4, cap.remaining(), "give back after a failed grant");
        GlobalCap unlimited = new GlobalCap(-1, 0);
        for (int i = 0; i < 1000; i++) yes(unlimited.tryTake(), "unlimited");
    }

    // ------------------------------------------------------------ config parsing

    public static void testTiersClampAndReport() {
        Issues issues = new Issues();
        Tiers t = Tiers.parse(List.of(
            Map.of("id", "Rough", "magnitude", 1, "upgrade-shards", 5),
            Map.of("id", "cut", "magnitude", 99, "cooldown-multiplier", 0),
            Map.of("id", "cut", "magnitude", "abc")), issues);
        eq(3, t.size(), "tiers");
        eq("rough", t.get(0).id(), "lower-cased id");
        eq(0, t.get(0).upgradeShards(), "first tier cost ignored");
        eq(10.0, t.get(1).magnitude(), "magnitude clamped");
        eq(0.05, t.get(1).cooldownMultiplier(), "cooldown multiplier clamped");
        yes(!t.get(2).id().equals("cut"), "duplicate id renamed");
        eq(1.0, t.get(2).magnitude(), "unparseable number falls back to default");
        yes(issues.warnings().size() >= 4, "problems reported: " + issues.warnings());
    }

    public static void testTiersDefaultWhenMissing() {
        Issues issues = new Issues();
        Tiers t = Tiers.parse(null, issues);
        eq(4, t.size(), "default ladder");
        yes(t.get(3).capped(), "top tier is server-capped by default");
        eq(3, t.indexOf("MYTHIC"), "case-insensitive lookup");
        eq(-1, t.indexOf("nope"), "missing tier");
        eq("mythic", t.get(99).id(), "out of range clamps to top");
    }

    public static void testTokenParsing() {
        UUID id = UUID.randomUUID();
        eq(new Token(id, 7), Token.parse(new Token(id, 7).encode()), "round trip");
        eq(null, Token.parse("garbage"), "garbage");
        eq(null, Token.parse(id + ":"), "missing version");
        eq(null, Token.parse(":5"), "missing id");
        eq(null, Token.parse(null), "null");
    }
}
