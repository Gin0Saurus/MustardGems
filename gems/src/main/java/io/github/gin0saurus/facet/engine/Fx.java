package io.github.gin0saurus.facet.engine;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Particles and sounds. Sounds are played by key ("entity.blaze.shoot"), so a theme can use any
 * vanilla or resource-pack sound and a typo simply plays nothing instead of erroring.
 */
public final class Fx {

    private boolean enabled = true;
    private double particleScale = 1.0;

    public void configure(boolean enabled, double particleScale) {
        this.enabled = enabled;
        this.particleScale = Math.max(0.0, Math.min(2.0, particleScale));
    }

    public boolean enabled() { return enabled; }

    private int n(int count) { return Math.max(1, (int) Math.round(count * particleScale)); }

    public void burst(Location at, Particle particle, int count, double spread, double speed) {
        if (!enabled || particleScale == 0) return;
        World w = at.getWorld();
        if (w == null) return;
        w.spawnParticle(particle, at, n(count), spread, spread, spread, speed);
    }

    public void dust(Location at, Color color, float size, int count, double spread) {
        if (!enabled || particleScale == 0) return;
        World w = at.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.DUST, at, n(count), spread, spread, spread, 0, new Particle.DustOptions(color, size));
    }

    /** A flat ring of coloured dust, used to show an ability's radius. */
    public void ring(Location center, double radius, Color color) {
        if (!enabled || particleScale == 0) return;
        int points = n((int) Math.max(12, radius * 10));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
            dust(p, color, 1.3f, 1, 0);
        }
    }

    public void sound(Location at, String key, float volume, float pitch) {
        if (key == null || key.isBlank() || at.getWorld() == null) return;
        Sound s = sound(key, volume, pitch);
        if (s != null) at.getWorld().playSound(s, at.getX(), at.getY(), at.getZ());
    }

    public void sound(Player player, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) return;
        Sound s = sound(key, volume, pitch);
        if (s != null) player.playSound(s);
    }

    private static Sound sound(String key, float volume, float pitch) {
        try {
            return Sound.sound(Key.key(key.trim().toLowerCase()), Sound.Source.PLAYER, volume, pitch);
        } catch (InvalidKeyException e) {
            return null;
        }
    }
}
