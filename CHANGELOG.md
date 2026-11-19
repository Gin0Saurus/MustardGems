# Changelog

## 1.0.0 - first public release (rebuild of MustardGems 1.8)
- New engine: every gem is a ledger record with a unique ID and version; items are only tokens.
  Copies made by dupes, creative cloning, rollbacks or keepInventory edge cases are removed.
- Protection now also covers drag, number keys, offhand swap, bundles, hoppers, item frames,
  armor stands, mobs, crafting grids and the void.
- Themes: Elements (default) and Condiments (the original Mustard SMP names).
- All ability numbers moved to gems.yml; all rules to config.yml; all text to lang/en.yml.
- Upgrades are `/gem upgrade` with shards (+ optional item); crafting recipes removed.
- Removed: resonance, energy, ore double-drops (may return as options).
- `/gem recover`, `/gem inspect`, `/gem debug`, `/gem selftest`.
- Safe saving (atomic write + backup); a corrupted data.yml is preserved and recovered from backup.
