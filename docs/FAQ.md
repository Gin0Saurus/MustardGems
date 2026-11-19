# FAQ & support

**Before asking for help,** run `/gem debug` and copy everything it prints (it's also in the
console). It lists versions, your theme and any config problems - nothing is ever sent anywhere.

### Players
**How do I get a gem?** `/gem choose <gem>` once for free (if the server allows it), mine ores
for shards, or take one from someone in PvP.

**How do I use it?** Hold it and right-click. The action bar shows Ready or the seconds left.

**How do I upgrade?** Collect shards of *your* gem type, hold the gem, `/gem upgrade`. `/gem`
shows the cost. The top tier may also need an item and is limited per server.

**I lost my gem (/clear, crash, void).** `/gem recover`. You get a fresh copy and every other
copy anywhere becomes worthless, so it can't be duplicated this way.

**Can I trade my gem?** `/gem unbind`, then drop or hand it over. Whoever right-clicks it owns it.

**Why can't I put my gem in a chest?** Bound gems stay in your inventory (owners can turn this off:
`gems.prevent-storing`). Unbound gems can go anywhere.

### Owners
**Players are on teams - will abilities hit teammates?** No. Players on the same scoreboard team
are allies (Life heals them, nothing damages them). Most team/clan plugins use scoreboard teams.

**Will it break my claims/spawn protection?** No. Abilities deal damage through the normal damage
event with the caster as attacker, so anything that blocks PvP there blocks the ability too.
Abilities never break or place blocks.

**keepInventory?** Respected: with it on, gems never drop.

**A gem disappeared from a chest/inventory.** It was a copy (duped, cloned in creative or from
before a rollback). The log says where and why ("Removed a gem item from ... (stale)").
The real one is safe; the owner can `/gem recover` if they lost it.

**Backups:** back up `plugins/FacetGems/data.yml` with your world. If it's ever corrupted, the
plugin keeps the broken file, loads `data.yml.bak`, and tells you in the console.

**Does it phone home / need a licence?** No. No telemetry, no licence server, no internet access.

**Folia?** Not supported yet.
