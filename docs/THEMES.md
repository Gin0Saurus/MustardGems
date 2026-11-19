# Themes

A theme changes what players **see** - names, colours, item materials, lore, tier names, sounds.
It never changes what gems **do** (that's `gems.yml`), so switching theme is always safe:
nobody loses a gem, tier or cooldown.

## Switch theme

```yaml
# config.yml
theme: condiments
```
`/gem reload`. Gems and shards in online players' inventories update instantly (everyone else's
on their next join). Ownership, tiers and cooldowns are untouched.

## Make your own

1. Copy `plugins/FacetGems/themes/elements.yml` to `themes/frost.yml` (any name).
2. Edit the names and colours. Example:

```yaml
name: Frost Kingdom
item-names:
  gem: "{color}<bold>{name}</bold>"
  shard: "{color}{name} Shard"
tiers:
  rough: "<gray>Novice"
  cut: "<white>Adept"
  flawless: "<aqua>Master"
  mythic: "<gradient:#a0e9ff:#ffffff><bold>Eternal</bold></gradient>"
gems:
  strength:
    name: Glacier Heart
    color: "#9BE7FF"
    material: diamond
    ability-name: Avalanche
    ability-description: "Strength, Haste and Resistance for a short burst."
    cast-sound: block.glass.break
```
3. `theme: frost` in `config.yml`, `/gem reload`. Problems are listed in chat and console.

**Keys per gem:** `name`, `color` (`#RRGGBB`), `material` (any vanilla item id), `model`
(resource-pack item model, see RESOURCE-PACK.md), `ability-name`, `ability-description`,
`cast-sound` (any sound id, e.g. `entity.blaze.shoot`), `lore` (list of lines).

Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html): `<red>`, `<bold>`,
`<gradient:#a:#b>`, `<#hex>`.

**Never** rename the gem ids (`strength`, `speed`, ...) - they're stored on items. Rename the
`name:` instead.
