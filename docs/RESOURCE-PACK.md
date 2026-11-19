# Resource pack (optional)

Without a pack, gems use vanilla items (red dye, sugar, emerald...) with coloured names and a
glint - everything works. The pack gives every gem and shard its own texture.

Two packs are included, one per bundled theme:

| Theme | File |
|---|---|
| Elements | `FacetGems-elements-pack.zip` |
| Condiments | `FacetGems-condiments-pack.zip` |

## Turn it on

1. Upload the zip for your theme somewhere with a **direct download link** (GitHub release
   assets, your web host, mc-packs.net). Google Drive/Dropbox share links do not work.
2. Get its SHA-1:
   - Windows PowerShell: `Get-FileHash FacetGems-elements-pack.zip -Algorithm SHA1`
   - macOS/Linux: `sha1sum FacetGems-elements-pack.zip`
3. In `config.yml`:
```yaml
resource-pack:
  enabled: true
  url: "https://your.host/FacetGems-elements-pack.zip"
  sha1: "paste the 40-character hash here"
```
4. `/gem reload`. Players get the pack on join (they can decline; it's cosmetic).

Already using a server pack? Merge the `assets/facet/` folder from our zip into yours instead and
leave `resource-pack.enabled: false`.

## Custom themes

Gems use the item model `facet:<gem-id>_gem` and shards `facet:<gem-id>_shard` (change the
namespace with `model-namespace` in the theme). To make textures for your own theme, edit the
colours in your theme file and run `python3 tools/make_packs.py` from the source, or replace the
PNGs in `assets/facet/textures/item/`.

> Known limitation: the pack's `pack.mcmeta` was written for 26.x client formats. If a client
> shows "incompatible", tell us your client version via the support page.
