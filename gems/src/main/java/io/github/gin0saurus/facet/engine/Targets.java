package io.github.gin0saurus.facet.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Who an ability may affect, and how to hit them so that protection plugins still apply.
 *
 * <p>Enemies are: hostile mobs, and players who are not on the caster's scoreboard team.
 * Villagers, wandering traders, pets, armor stands and passive animals are never targets.
 * Damage goes through the normal damage event with the caster as the attacker, so spawn
 * protection, region plugins and PvP toggles that cancel that event also cancel the ability.
 */
public final class Targets {

    private boolean affectPlayers = true;
    private boolean affectMobs = true;

    public void configure(boolean affectPlayers, boolean affectMobs) {
        this.affectPlayers = affectPlayers;
        this.affectMobs = affectMobs;
    }

    public boolean isEnemy(Player caster, Entity e) {
        if (e == caster || !(e instanceof LivingEntity le) || le.isDead()) return false;
        if (e instanceof AbstractVillager) return false;
        if (e instanceof Tameable t && t.isTamed()) return false;
        if (e instanceof Player p) {
            if (!affectPlayers) return false;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
            if (!caster.canSee(p)) return false;
            return !allies(caster, p);
        }
        return affectMobs && e instanceof Enemy;
    }

    /** Players on the same scoreboard team are allies. No team means no allies. */
    public boolean allies(Player a, Player b) {
        if (a.equals(b)) return true;
        Scoreboard sb = a.getScoreboard();
        Team ta = sb.getEntryTeam(a.getName());
        return ta != null && ta.equals(sb.getEntryTeam(b.getName()));
    }

    public List<LivingEntity> enemiesNear(Player caster, Location center, double radius, int max) {
        List<LivingEntity> out = new ArrayList<>();
        if (center.getWorld() == null) return out;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (isEnemy(caster, e) && e.getLocation().distanceSquared(center) <= radius * radius) out.add((LivingEntity) e);
        }
        out.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    public List<Player> alliesNear(Player caster, double radius) {
        List<Player> out = new ArrayList<>();
        out.add(caster);
        for (Player p : caster.getWorld().getPlayers()) {
            if (p.equals(caster) || p.isDead()) continue;
            if (p.getLocation().distanceSquared(caster.getLocation()) <= radius * radius && allies(caster, p)) out.add(p);
        }
        return out;
    }

    /**
     * Damages the target as the caster. Returns true only if the hit actually landed (no plugin
     * or game rule cancelled it), so debuffs are only applied where damage would be allowed.
     */
    public boolean hit(Player caster, LivingEntity target, double damage) {
        EntityDamageEvent before = target.getLastDamageCause();
        target.setNoDamageTicks(0);
        target.damage(Math.max(0.5, damage), caster);
        EntityDamageEvent after = target.getLastDamageCause();
        return after != before && after instanceof EntityDamageByEntityEvent ev && ev.getDamager().equals(caster);
    }
}
