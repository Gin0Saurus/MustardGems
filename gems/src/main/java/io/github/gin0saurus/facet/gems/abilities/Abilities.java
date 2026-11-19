package io.github.gin0saurus.facet.gems.abilities;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The eight built-in abilities. Each is generic ("dash", "heal allies") so any concept can reuse
 * it with different numbers and a different theme. Nothing here breaks or places blocks.
 */
public final class Abilities {

    /** Players who must not take fall damage until the given time (after a dash or launch). */
    public static final Map<UUID, Long> FALL_GRACE = new ConcurrentHashMap<>();

    private Abilities() {}

    public static Map<String, Ability> all() {
        Map<String, Ability> m = new LinkedHashMap<>();
        for (Ability a : List.of(new Rampage(), new Dash(), new Mend(), new Inferno(), new GravityWell(),
                new DeepFreeze(), new Blessing(), new Discharge())) {
            m.put(a.id(), a);
        }
        return m;
    }

    static void grace(Player p, int ticks) { FALL_GRACE.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L); }

    static Vector away(Location from, LivingEntity e, double strength, double up) {
        Vector v = e.getLocation().toVector().subtract(from.toVector());
        if (v.lengthSquared() < 1e-4) v = new Vector(0, 0, 0);
        else v.normalize();
        return v.multiply(strength).setY(up);
    }

    // ------------------------------------------------------------------ strength

    /** Self buff burst: Strength, Haste and Resistance for part of the cooldown. */
    static final class Rampage implements Ability {
        public String id() { return "rampage"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            int ticks = c.effectTicks("duration-fraction", 0.35);
            int bonus = c.tier().amplifierBonus();
            p.addPotionEffect(c.effect(PotionEffectType.STRENGTH, ticks, c.pi("strength-amplifier", 0) + bonus));
            p.addPotionEffect(c.effect(PotionEffectType.HASTE, ticks, c.pi("haste-amplifier", 1) + bonus));
            p.addPotionEffect(c.effect(PotionEffectType.RESISTANCE, ticks, c.pi("resistance-amplifier", 0)));
            Location l = p.getLocation().add(0, 1, 0);
            c.fx().burst(l, Particle.CRIT, 40, 0.6, 0.3);
            c.fx().ring(p.getLocation(), 1.5, c.skin().particleColor());
            c.castSound("entity.ravager.roar", 1.2f);
            return true;
        }
    }

    // ------------------------------------------------------------------ speed

    /** Charge forward; enemies in the path take damage and are knocked aside. */
    static final class Dash implements Ability {
        public String id() { return "dash"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            double power = Math.min(c.p("power", 1.6) * Math.sqrt(c.magnitude()), 4.0);
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) return false;
            dir.normalize().multiply(power).setY(Math.max(0.25, c.p("lift", 0.3)));
            p.setVelocity(dir);
            grace(p, 60);
            double damage = c.p("damage", 5.0) * c.magnitude();
            Set<UUID> hit = new HashSet<>();
            c.fx().burst(p.getLocation(), Particle.CLOUD, 20, 0.3, 0.05);
            c.castSound("entity.breeze.wind_burst", 1.3f);
            int[] ticks = {0};
            Bukkit.getScheduler().runTaskTimer(c.plugin(), task -> {
                if (!p.isOnline() || p.isDead() || ++ticks[0] > 12) { task.cancel(); return; }
                c.fx().dust(p.getLocation().add(0, 1, 0), c.skin().particleColor(), 1.4f, 4, 0.3);
                for (LivingEntity e : c.targets().enemiesNear(p, p.getLocation().add(0, 1, 0), 1.9, 8)) {
                    if (!hit.add(e.getUniqueId())) continue;
                    if (c.targets().hit(p, e, damage)) {
                        e.setVelocity(Abilities.away(p.getLocation(), e, 0.9, 0.45));
                        c.fx().burst(e.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 2, 0.2, 0);
                    }
                }
            }, 1L, 1L);
            return true;
        }
    }

    // ------------------------------------------------------------------ life

    /** Heals the caster and allies (same scoreboard team) nearby, with an absorption shield. Never enemies. */
    static final class Mend implements Ability {
        public String id() { return "mend"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            double heal = c.p("heal", 6.0) * c.magnitude();
            double radius = c.p("radius", 6.0);
            int absorb = c.pi("absorption-amplifier", 0) + c.tier().amplifierBonus();
            int ticks = c.effectTicks("absorption-fraction", 0.5);
            for (Player ally : c.targets().alliesNear(p, radius)) {
                AttributeInstance max = ally.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20.0 : max.getValue();
                ally.setHealth(Math.min(cap, ally.getHealth() + heal));
                ally.addPotionEffect(c.effect(PotionEffectType.ABSORPTION, ticks, absorb));
                c.fx().burst(ally.getLocation().add(0, 1.2, 0), Particle.HEART, 6, 0.5, 0);
            }
            c.fx().ring(p.getLocation(), radius, c.skin().particleColor());
            c.castSound("block.amethyst_block.resonate", 1.4f);
            return true;
        }
    }

    // ------------------------------------------------------------------ fire

    /** Fire burst around the caster: damage, burning and knockback. Sets no blocks alight. */
    static final class Inferno implements Ability {
        public String id() { return "inferno"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            double radius = c.p("radius", 5.0) + c.tierIndex() * c.p("radius-per-tier", 0.5);
            double damage = c.p("damage", 6.0) * c.magnitude();
            int fire = (int) Math.round(c.p("burn-seconds", 4) * 20 * Math.sqrt(c.magnitude()));
            Location center = p.getLocation();
            for (LivingEntity e : c.targets().enemiesNear(p, center, radius, 16)) {
                if (c.targets().hit(p, e, damage)) {
                    e.setFireTicks(Math.max(e.getFireTicks(), fire));
                    e.setVelocity(Abilities.away(center, e, c.p("knockback", 1.1), 0.4));
                }
            }
            c.fx().burst(center.clone().add(0, 1, 0), Particle.FLAME, 120, radius / 2.5, 0.08);
            c.fx().burst(center.clone().add(0, 1, 0), Particle.LAVA, 20, radius / 3, 0);
            c.castSound("entity.blaze.shoot", 0.8f);
            c.sound("entity.generic.explode", 0.7f, 1.2f);
            return true;
        }
    }

    // ------------------------------------------------------------------ gravity

    /** Opens a well where you look: pulls enemies in, then slams them for radius damage. */
    static final class GravityWell implements Ability {
        public String id() { return "gravity_well"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            double range = c.p("range", 12);
            Block b = p.getTargetBlockExact((int) range);
            Location center = b != null
                ? b.getLocation().add(0.5, 1.2, 0.5)
                : p.getEyeLocation().add(p.getLocation().getDirection().multiply(Math.min(range, 8)));
            double radius = c.p("radius", 6.0) + c.tierIndex() * c.p("radius-per-tier", 0.5);
            double pull = c.p("pull", 0.35);
            double damage = c.p("slam-damage", 6.0) * c.magnitude();
            int duration = Math.max(10, c.pi("pull-ticks", 40));
            c.castSound("block.respawn_anchor.charge", 0.6f);
            int[] t = {0};
            Bukkit.getScheduler().runTaskTimer(c.plugin(), task -> {
                t[0] += 2;
                if (!p.isOnline()) { task.cancel(); return; }
                c.fx().ring(center, radius * (1 - (double) t[0] / duration) + 0.5, c.skin().particleColor());
                c.fx().burst(center, Particle.REVERSE_PORTAL, 15, 0.4, 0.05);
                if (t[0] < duration) {
                    for (LivingEntity e : c.targets().enemiesNear(p, center, radius, 16)) {
                        Vector v = center.toVector().subtract(e.getLocation().toVector());
                        if (v.lengthSquared() > 0.5) e.setVelocity(v.normalize().multiply(pull));
                    }
                    return;
                }
                task.cancel();
                for (LivingEntity e : c.targets().enemiesNear(p, center, radius * 0.75, 16)) {
                    if (c.targets().hit(p, e, damage)) e.setVelocity(new Vector(0, c.p("slam-lift", 0.6), 0));
                }
                c.fx().burst(center, Particle.EXPLOSION, 3, 0.8, 0);
                c.fx().sound(center, "entity.warden.sonic_boom", 0.8f, 1.4f);
            }, 2L, 2L);
            return true;
        }
    }

    // ------------------------------------------------------------------ ice

    /** Freezes enemies nearby: heavy slowness, weakness and a short, sharp levitation. */
    static final class DeepFreeze implements Ability {
        public String id() { return "deep_freeze"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            double radius = c.p("radius", 6.0) + c.tierIndex() * c.p("radius-per-tier", 0.5);
            int slowTicks = c.effectTicks("slow-fraction", 0.15);
            int slowAmp = c.pi("slowness-amplifier", 3) + c.tier().amplifierBonus();
            int levAmp = c.pi("levitation-amplifier", 20);
            int levTicks = Math.max(1, c.pi("levitation-ticks", 40));
            boolean any = false;
            for (LivingEntity e : c.targets().enemiesNear(p, p.getLocation(), radius, 16)) {
                if (!c.targets().hit(p, e, c.p("damage", 2.0) * c.magnitude())) continue;
                any = true;
                e.addPotionEffect(c.effect(PotionEffectType.SLOWNESS, slowTicks, slowAmp));
                e.addPotionEffect(c.effect(PotionEffectType.WEAKNESS, slowTicks, c.pi("weakness-amplifier", 0)));
                if (levAmp > 0) e.addPotionEffect(c.effect(PotionEffectType.LEVITATION, levTicks, levAmp));
                c.fx().burst(e.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 25, 0.4, 0.02);
            }
            c.fx().ring(p.getLocation(), radius, c.skin().particleColor());
            c.fx().burst(p.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 80, radius / 2.5, 0.02);
            c.castSound("block.glass.break", 0.6f);
            return any || c.def().params().getBoolean("spend-on-miss", true);
        }
    }

    // ------------------------------------------------------------------ wealth

    /** Hero of the Village burst for cheap trades (passive XP boost is configured on the gem). */
    static final class Blessing implements Ability {
        public String id() { return "village_blessing"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            int secs = Math.max(1, c.pi("duration-seconds", 10));
            int ticks = Math.min(secs * 20, (int) (c.cooldownSeconds() * 20 * 0.9));
            p.addPotionEffect(c.effect(PotionEffectType.HERO_OF_THE_VILLAGE, ticks, c.pi("hero-amplifier", 254)));
            c.fx().burst(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 40, 0.8, 0);
            c.fx().burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 30, 0.6, 0.2);
            c.castSound("entity.villager.celebrate", 1.0f);
            return true;
        }
    }

    // ------------------------------------------------------------------ storm

    /** Lightning on the nearest enemies. Purely visual bolts (no fire, no block damage); creepers it hits become charged. */
    static final class Discharge implements Ability {
        public String id() { return "static_discharge"; }
        public boolean cast(CastContext c) {
            Player p = c.player();
            double radius = c.p("radius", 8.0) + c.tierIndex() * c.p("radius-per-tier", 1.0);
            int max = c.pi("max-targets", 2) + c.tierIndex();
            double damage = c.p("damage", 6.0) * c.magnitude();
            List<LivingEntity> targets = c.targets().enemiesNear(p, p.getLocation(), radius, max);
            if (targets.isEmpty()) {
                c.fx().burst(p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30, 0.6, 0.1);
                return c.def().params().getBoolean("spend-on-miss", false);
            }
            for (LivingEntity e : targets) {
                e.getWorld().strikeLightningEffect(e.getLocation());
                if (e instanceof Creeper creeper && c.def().params().getBoolean("charge-creepers", true)) creeper.setPowered(true);
                if (c.targets().hit(p, e, damage)) {
                    e.addPotionEffect(c.effect(PotionEffectType.SLOWNESS, 40, 1));
                }
                c.fx().burst(e.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30, 0.4, 0.2);
            }
            c.castSound("entity.lightning_bolt.thunder", 1.6f);
            return true;
        }
    }
}
