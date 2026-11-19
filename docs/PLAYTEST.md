# 30-minute playtest before listing (needs 2 accounts: you + an alt or a friend)

CI proves the plugin loads, items/tags/files/save-data work and nothing throws on Paper
26.1.2/26.2/26.3. It cannot click, fight or open chests. These checks need a real client.
Tick each one; anything that fails, paste `/gem debug` + what happened.

## Setup (2 min)
- [ ] Fresh Paper 26.2 test server, drop in `FacetGems-1.0.0.jar`, start. Console: "Facet Gems 1.0.0 (full edition) enabled: 8 gems".
- [ ] `/team add red` and keep both accounts OFF a team for the PvP tests.

## Getting a gem (3 min)
- [ ] Join message suggests `/gem choose`. `/gem choose fire` gives an UNBOUND Sunstone.
- [ ] Right-click it: title + sound, it's now "Bound to <you>". `/gem choose ice` again is refused.
- [ ] `/gem` shows tier, Ready, and the next upgrade cost.

## Every ability once (8 min) - `/gem give <you> <gem>` then right-click each
- [ ] Ruby: buffs appear, last less than the cooldown. Topaz: dash, alt in the path takes damage.
- [ ] Emerald: heals you; alt only gets healed when on your team (`/team join red <both>`).
- [ ] Sunstone: alt burns + knocked back, NO blocks catch fire. Onyx: pull to where you look, then slam.
- [ ] Sapphire: alt slowed + launched for ~2s; you take no fall damage. Citrine: Hero of the Village; XP orbs give extra.
- [ ] Amethyst: lightning on alt; a creeper hit becomes charged; a villager next to it is never hit.
- [ ] A villager / your tamed dog near every ability is never hurt.
- [ ] Cooldown: right-click again shows the seconds on the action bar; nothing happens until ready.

## Protection (6 min) - with a BOUND gem
- [ ] Q drop → refused. Drag out of inventory → refused.
- [ ] Chest, shulker, ender chest, furnace, crafting table, anvil, villager trade: can't put it in (click, shift-click, number keys, drag).
- [ ] Bundle: can't put it in either way. Item frame / armor stand / allay: nothing happens.
- [ ] 2x2 crafting grid in your own inventory: can't place it there.

## Death + PvP (5 min)
- [ ] Alt kills you: gem drops UNBOUND, alt picks it up, right-clicks → theirs. You got nothing back.
- [ ] Alt (with a gem) kills you again within 10 min → no shards the second time.
- [ ] `/gamerule keep_inventory true` (or keepInventory): dying keeps the gem, nothing drops.
- [ ] Throw an UNBOUND gem in lava / cactus / fire: it survives. Wait 6 min: it doesn't despawn.

## Upgrades + cap (3 min)
- [ ] `/gem giveshard <you> fire 64`, hold gem, `/gem upgrade` ×2. Flawless asks for a diamond block.
- [ ] Set `tiers.server-cap: 1`, `/gem reload`, upgrade to Mythic: broadcast says 0 left. A second player can't reach Mythic.

## Dupes + recovery (3 min)
- [ ] Creative middle-click your gem to clone it: within ~10s the clone vanishes (console says "duplicate").
- [ ] `/clear` yourself, `/gem recover`: fresh gem. If you had stashed a copy (with prevent-storing off), it is now removed.

## Theme switch (1 min)
- [ ] `theme: condiments`, `/gem reload`: your gem instantly becomes Hot Sauce, same tier and owner.

If all ticked: record the video (docs/LISTING.md shot list) and list it.
