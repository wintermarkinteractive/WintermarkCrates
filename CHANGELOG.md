# Changelog

## 1.0.0 - 2026-11-24
First public release.

- Turn any block into a crate with `/crate set <crate>`; crate blocks can't be broken or blown up.
- Rolling opening animation that slows down and stops on the prize; only the prize stays visible at the end. The prize is always given, also when the menu is closed early or the player logs out.
- Keys as tagged items (renamed copies don't work, can't be placed), `/crate givekey` and `/crate giveall` (console-friendly).
- Preview menu with real chance percentages (left-click or `/crate preview`).
- Rewards with weights: items, console commands, messages and server-wide broadcasts.
- Mass open with sneak + right-click.
- Holograms (display entities, never saved into the world) and particle circles around crates.
- Everything configurable in `config.yml` with MiniMessage colors; new settings are added to existing configs automatically.
