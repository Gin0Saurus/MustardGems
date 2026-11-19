# Facet Gems

Eight gem powers for SMP servers - tiers to climb, real PvP stakes, a server-wide race for the
top tier - built to be **impossible to dupe** and **easy to configure**.

> Tested by CI on Paper **26.1.2, 26.2 and 26.3** (Java 25). Other versions/forks are not claimed.

## What's in it

| | |
|---|---|
| **8 gems** | Strength, Speed, Life, Fire, Gravity, Ice, Wealth, Storm - one active ability + passives each |
| **4 tiers** | Rough → Cut → Flawless → Mythic. Upgrade with shards of the same gem (`/gem upgrade`) |
| **PvP stakes** | Gems drop on death as loot. Kills pay shards, with an anti-farm limit per victim |
| **Server race** | The top tier can only be reached a set number of times per server (default 20) |
| **Themes** | Same powers, different concept: ships with **Elements** and **Condiments**. Make your own in minutes |
| **Anti-dupe** | Every gem has a unique ID + version in a ledger. Copies, clones and rollbacks are worthless and removed |
| **Protection** | Bound gems can't be dropped, stored (chests, shulkers, bundles, ender chests, frames, hoppers...), burned, blown up or despawned |
| **Fair targeting** | Abilities go through normal damage events, so claim/region/spawn protection still applies. Never hits villagers or pets |
| **No surprises** | No telemetry, no licence checks, no internet access. Works offline |

## Install (60 seconds)

1. Drop `FacetGems-x.y.z.jar` into `plugins/` and restart.
2. Players type `/gem choose <gem>` for a free starter gem (or disable that in `config.yml`).
3. Optional: textures - see [docs/RESOURCE-PACK.md](docs/RESOURCE-PACK.md).

Everything else is optional. Every number is in `config.yml` (rules) and `gems.yml`
(what each gem does); every name/colour is in `themes/`. `/gem reload` applies changes and lists
any mistakes - a bad value is clamped, never a crash.

## Commands

| Command | Who | |
|---|---|---|
| `/gem` | players | Your gem, cooldown and next upgrade |
| `/gem list` · `/gem info <gem>` | players | What every gem does |
| `/gem choose <gem>` | players | One free starter gem |
| `/gem upgrade` | players | Spend shards (hold your gem) |
| `/gem unbind` | players | Make your gem tradeable |
| `/gem recover` | players | Replace a lost gem; every other copy dies |
| `/gem give <player> <gem> [tier]` | admin | Give an unbound gem |
| `/gem giveshard <player> <gem> [amount]` | admin | Give shards |
| `/gem settier <player> <tier>` · `/gem clear <player>` | admin | Adjust or destroy a player's gem |
| `/gem inspect` · `/gem debug` · `/gem reload` · `/gem selftest` | admin | Support tools |

Permissions: `facet.use`, `facet.attune`, `facet.starter` (everyone), `facet.admin` (ops).

## Docs

- [Themes - turn it into your own concept](docs/THEMES.md)
- [Resource pack](docs/RESOURCE-PACK.md)
- [FAQ & support](docs/FAQ.md)

## For developers

```
core/   pure-Java rules (ledger, cooldowns, limits, tiers) - no Minecraft dependency
gems/   the Paper plugin: engine/ (concept-agnostic) + gems/ (this concept)
ci/     boots real Paper servers and runs the in-server self-test
tools/  resource pack generator
```

`gradle :core:check :gems:assemble` builds `FacetGems-<v>.jar` and `FacetGems-<v>-Free.jar`.
Every push runs the core tests and boots Paper 26.1.2 / 26.2 / 26.3 with the plugin.

The old MustardGems 1.7/1.8 jars are kept in `releases/` for reference.
