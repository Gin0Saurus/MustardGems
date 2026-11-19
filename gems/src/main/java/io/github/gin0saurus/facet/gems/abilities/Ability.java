package io.github.gin0saurus.facet.gems.abilities;

/**
 * An active power. Implementations read every number from the gem's params (gems.yml) with a
 * sensible default, scale strength by the tier's magnitude, and return false if nothing
 * happened (no target, blocked) so the cooldown is not spent.
 */
public interface Ability {

    String id();

    boolean cast(CastContext c);
}
