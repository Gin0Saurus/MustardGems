package io.github.gin0saurus.facet.gems.abilities;

import io.github.gin0saurus.facet.core.Tiers;
import io.github.gin0saurus.facet.engine.Fx;
import io.github.gin0saurus.facet.engine.Targets;
import io.github.gin0saurus.facet.engine.Theme;
import io.github.gin0saurus.facet.gems.GemDef;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Everything an ability needs for one cast. */
public record CastContext(Plugin plugin, Player player, GemDef def, Tiers.Tier tier, int tierIndex,
                          int cooldownSeconds, Theme.Skin skin, Fx fx, Targets targets) {

    public double magnitude() { return tier.magnitude(); }

    public double p(String key, double def) { return this.def.p(key, def); }

    public int pi(String key, int def) { return this.def.pi(key, def); }

    /**
     * A buff/debuff length in ticks, as a fraction of this cast's real cooldown, scaled by tier,
     * and never longer than 90% of the cooldown - so a buff can never outlast its own cooldown
     * no matter how the tiers are configured.
     */
    public int effectTicks(String fractionKey, double defFraction) {
        double secs = cooldownSeconds * p(fractionKey, defFraction) * Math.sqrt(magnitude());
        secs = Math.min(secs, cooldownSeconds * 0.9);
        return Math.max(20, (int) Math.round(secs * 20));
    }

    public PotionEffect effect(PotionEffectType type, int ticks, int amplifier) {
        return new PotionEffect(type, ticks, Math.max(0, Math.min(254, amplifier)), false, true, true);
    }

    public void sound(String key, float volume, float pitch) { fx.sound(player.getLocation(), key, volume, pitch); }

    /** The theme's cast sound, if it set one, otherwise the ability's default. */
    public void castSound(String fallback, float pitch) {
        String key = skin.castSound() == null || skin.castSound().isBlank() ? fallback : skin.castSound();
        sound(key, 1.0f, pitch);
    }
}
