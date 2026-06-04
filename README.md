# ItemAlchemyAddon

A feature-rich Fabric add-on for [ItemAlchemy](https://github.com/Pitan76/itemalchemy) that introduces a portable, professionally designed alchemical interface — the **Alchemical Tome** — with full filtering, sorting, search, an unlearning system, and per-tick EMC synchronisation.

Built on [MCPitanLib](https://github.com/Pitan76/mcpitanlib) for clean, abstraction-friendly modding.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting Started](#getting-started)
- [User Guide](#user-guide)
  - [Crafting the Alchemical Tome](#crafting-the-alchemical-tome)
  - [Burning Mode](#burning-mode)
  - [Unlearning Mode](#unlearning-mode)
  - [Purchasing Items](#purchasing-items)
  - [Search](#search)
  - [Tabs](#tabs)
  - [Edit EMC Overlay](#edit-emc-overlay)
- [Commands](#commands)
- [Keybindings](#keybindings)
- [Configuration](#configuration)
- [Compatibility](#compatibility)
- [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Credits](#credits)
- [License](#license)
- [Contributing](#contributing)

---

## Overview

**ItemAlchemyAddon** extends Pitan's *ItemAlchemy* mod with a portable, book-shaped item — the **Alchemical Tome** — that brings the entire EMC transmutation experience into a polished, modern UI. Instead of physically standing in front of a block, players carry the Tome in their inventory and open a comprehensive interface that lets them:

- Burn items into EMC (with optional automatic learning)
- Browse, filter, sort, and search every learnable item in the game
- Purchase items from their accumulated EMC pool
- Unlearn previously learned items in batch

The mod is designed to feel native to vanilla Minecraft, with custom textures, hover states, tooltips, and faithfully recreated tab navigation that mirrors Creative-mode UX.

---

## Features

### Alchemical Tome Item

- Portable book-shaped item that opens a full GUI on right-click
- Maximum stack size: 1
- Crafted from one `itemalchemy:alchemy_table` and one `minecraft:writable_book`
- Crafts in any 2x2 or 3x3 grid (works in the player's inventory crafting)

### Two Operational Modes

A single toggle button switches the interface between:

- **Burning Mode** — convert items to EMC and (optionally) learn them
- **Unlearning Mode** — select items in bulk to remove them from your learned list

Mode-specific buttons are shown or hidden automatically; only the relevant interactions are active in each mode.

### Powerful Item Browser

- **Filter modes** (cycle): `Known`, `All`, `Unknown`, `EMC = 0`
- **Sort modes** (cycle): `ID`, `Alphabetical`, `EMC descending`, `EMC ascending`
- **Visual markers**:
  - Unlearned items with EMC are visually dimmed
  - Items with no EMC value are marked with a small cross icon
- **Search bar** that scans both display names *and* registry IDs (so `flint` finds `minecraft:flint` even when the language is set to Polish)

### Reworked Creative Tab Navigation

- 7 tabs visible at once, fixed slots, snap-step scrolling (no awkward smooth scroll)
- Custom-textured `tab_active` / `tab_inactive` graphics
- Left/right scroll arrows for keyboard- and mouse-only users
- Hover tooltips on every tab matching vanilla Creative behaviour
- Inventory and Operator/Command-Block tabs are filtered out (always empty)
- The first tab is renamed to **"All Items"** to reflect its repurposed role

### Custom Scrollbar

- Custom `slider.png` / `slider_hovered.png` textures
- Becomes highlighted only while clicked, not on hover
- Preserves grab offset — the slider stays anchored to the exact pixel under your cursor when dragged

### Granular Purchase Logic

| Action | Result |
|--------|--------|
| Left-click | Buy 1 item to cursor (or +1 if cursor already holds the same item) |
| Right-click | Buy 1 item to inventory |
| Shift + Left-click | Fill cursor stack to maximum |
| Shift + Right-click | Buy a full stack to inventory |

Purchase is gated server-side — items that have not been learned cannot be bought even if they appear in a non-`Known` filter.

### Per-Tick EMC Synchronisation

Player EMC is synchronised every server tick via a `PropertyDelegate` (split into two `int`s for full `long` precision), so passively gained EMC always reflects accurately in the GUI.

### Background Dimming

The interface uses Minecraft's standard background dimming so it composes correctly with overlay mods like JEI or EMI.

### EMC Editing Overlay

Hover over **any item in any inventory screen** and press `` ` `` (grave / tilde) to open a quick-edit overlay that lets operators set the EMC value of that item. Internally executes `/itemalchemy setemc` so it respects existing permission gates.

### Recipe-Based EMC Reload

A multi-pass algorithm (up to 10 passes, supporting transitive dependencies) computes EMC values for items with no explicit value by walking through crafting, smelting, blasting, smoking, and campfire-cooking recipes.

---

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.20.1 |
| Java | 17 or newer |
| Fabric Loader | 0.18.6+ |
| Fabric API | 0.92.3+1.20.1 |
| MCPitanLib | 1.20.1:3.6.5 (bundled) |
| ItemAlchemy | 1.2.5-SNAPSHOT (bundled) |

> MCPitanLib and ItemAlchemy are bundled inside the JAR via Loom's `include` directive — you do **not** need to install them separately.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.20.1.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1 and place it in your `mods/` folder.
3. Download the latest `ItemAlchemyAddon-x.x.x.jar` from the [Releases](../../releases) page.
4. Drop it into your `mods/` folder.
5. Launch the game.

> The JAR includes both the addon and its dependencies (MCPitanLib + ItemAlchemy). You only need Fabric API installed separately.

---

## Getting Started

After your first launch:

1. Place an `itemalchemy:alchemy_table` and a `minecraft:writable_book` into a crafting grid as shown below to obtain an **Alchemical Tome**.
2. Hold the Tome and right-click to open the interface.
3. Drop items onto the burn icon (or shift-click them from your inventory) to begin accumulating EMC.

---

## User Guide

### Crafting the Alchemical Tome

The Tome is shaped 2 high × 1 wide and can be crafted in either the player inventory grid or a crafting table. The pattern:

```
[ alchemy_table   ]
[ writable_book   ]
```

Result: 1 × Alchemical Tome.

### Burning Mode

Burning mode is the default mode. In this mode:

- **Drag** an item onto the burn icon to burn the entire dragged stack.
- **Left-click** on the burn icon while holding a stack on your cursor to burn the whole stack.
- **Right-click** on the burn icon while holding a stack to burn a single item.
- **Shift + Left-click** an inventory slot to burn the whole stack from that slot.
- **Shift + Right-click** an inventory slot to burn one item from that slot.

A toggle next to the burn icon controls **Learn Mode**:
- **On** — burned items are added to your team's learned list (default)
- **Off** — burned items grant EMC but are *not* learned (useful when intentionally avoiding registration)

A status icon next to the toggle reflects its current state at a glance.

### Unlearning Mode

Click the **Unlearn Mode** toggle to enter unlearning mode. The burn icon and learn toggle are hidden, and **Confirm** / **Deny** buttons appear instead.

- **Left-click** an item in the list to mark it for removal (a cross icon appears on top of the item).
- **Click and drag** across multiple items to mass-mark them.
- **Confirm** removes all marked items from the team's learned list and exits unlearning mode.
- **Deny**, or clicking the **Unlearn Mode** toggle again, exits without making any changes.

Unlearning is irreversible — the player will need to re-burn an item (with Learn Mode on) to add it back to the learned list.

### Purchasing Items

Click any *learned* item in the list to purchase it. The exact behaviour depends on which mouse button and modifier you use:

| Action | Effect |
|--------|--------|
| Left-click | Place 1 unit on the cursor (stacks if cursor already holds the same item) |
| Right-click | Drop 1 unit into your inventory (game chooses the slot) |
| Shift + Left-click | Fill the cursor stack to the maximum the player can afford |
| Shift + Right-click | Drop a full stack into your inventory (or as many as the player can afford) |

EMC is deducted in real time and verified server-side.

### Search

The search bar lives at the top of the interface (activation area: the entire bar region — clicking anywhere on it activates it).

- Filters the *currently active* tab and respects the active filter and sort.
- Matches against both the localized item name and the registry ID, so search works regardless of game language.
- Press `Escape` *or* click anywhere outside the bar (including tabs, buttons, or items) to deactivate it.
- Maximum query length: 40 characters.

### Tabs

- 7 tabs are visible at any time at the top of the GUI.
- Use the left/right arrow buttons or the scroll wheel (while hovering over the tab strip) to step through tabs one slot at a time.
- The active tab is rendered with the `tab_active` texture and slightly raised; inactive tabs use the `tab_inactive` texture.
- Hover any tab for ~half a second to see its display name as a tooltip.

The first tab — **All Items** — shows every registered item in the game, while the rest mirror vanilla Creative item-group categories (Building Blocks, Natural, Functional, Redstone, Tools, Combat, Food & Drinks, Ingredients, Spawn Eggs, etc.). Empty tabs (Inventory, Operator/Command Block, Saved Hotbar) are filtered out automatically.

### Edit EMC Overlay

While hovering over **any item slot in any inventory screen**, press `` ` `` (grave / tilde — configurable in Controls) to open the EMC editing overlay.

- The overlay shows the item's current EMC value.
- Type a new value and press **Enter** to apply it.
- Press **Escape** to cancel.

Internally this issues `/itemalchemy setemc <item> <value>`, so the change is subject to whatever permission level your server sets for that command (operator-only by default).

---

## Commands

### `/itemalchemyaddon reloademc`

Recalculates EMC values for items based on their crafting recipes.

The algorithm walks through every recipe of the supported types and, for each output that does not yet have an EMC value, attempts to derive one by summing the EMC of its ingredients. This repeats for up to 10 passes, allowing items whose ingredients only gain an EMC value in a later pass to be resolved transitively.

**Supported recipe types:**

- `crafting`
- `smelting`
- `blasting`
- `smoking`
- `campfire_cooking`

Output is reported live in chat as the algorithm runs.

> Tip: Run this command after installing new content packs or recipe-adding mods so newly available items receive an EMC valuation.

---

## Keybindings

All keybindings appear under the **Item Alchemy Addon** category in *Options → Controls → Key Binds*.

| Action | Default Key | Description |
|--------|-------------|-------------|
| Edit EMC | `` ` `` (grave) | Opens the EMC editing overlay for the hovered item |

---

## Configuration

This mod uses ItemAlchemy's existing configuration files. No additional configuration files are introduced.

The Alchemical Tome reads its learned-items list from the team data managed by ItemAlchemy, so multi-player teams share their learned pool exactly as they do with the base table.

---

## Compatibility

| Mod | Status |
|-----|--------|
| ItemAlchemy | **Required** — addon depends on it directly |
| MCPitanLib | **Required** — bundled |
| Fabric API | **Required** |
| JEI / REI / EMI | Compatible — the addon's GUI uses standard background dimming so overlay mods render correctly |
| Inventory-mod tweaks (e.g. mouse-tweaks) | Compatible at the slot-click layer |
| Other ItemAlchemy add-ons | Compatible — does not modify ItemAlchemy's internal state beyond what its public API allows |

The mod is currently locked to **Minecraft 1.20.1** on **Fabric**. Cross-version support (via MCPitanLib's compatibility layer) is on the roadmap but not yet complete.

---

## Building from Source

### Prerequisites

- JDK 17 or newer
- Git

### Clone & Build

```bash
git clone https://github.com/<your-username>/ItemAlchemyAddon.git
cd ItemAlchemyAddon
./gradlew build
```

The compiled JAR will be in `build/libs/`.

### Run a Development Client

```bash
./gradlew runClient
```

### Run a Development Server

```bash
./gradlew runServer
```

### Generate Data

```bash
./gradlew runDatagen
```

---

## Project Structure

```
src/
├── main/
│   ├── java/pl/viko/itemalchemyaddon/
│   │   ├── ItemAlchemyAddon.java           Mod entry point
│   │   ├── command/                        Server commands (/itemalchemyaddon reloademc)
│   │   ├── item/                           Custom items (Alchemical Tome)
│   │   ├── mixin/                          Server-side mixins (EMCManager override)
│   │   ├── networking/                     C2S packet definitions and registration
│   │   └── screen/                         Server-side screen handler
│   └── resources/
│       ├── assets/itemalchemyaddon/        Textures, models, lang files
│       ├── data/itemalchemyaddon/          Recipes
│       ├── fabric.mod.json                 Fabric metadata
│       └── itemalchemyaddon.mixins.json    Common mixin config
└── client/
    ├── java/pl/viko/itemalchemyaddon/
    │   ├── client/                         Client entry point and event handlers
    │   ├── mixin/                          Client-side mixins (HandledScreen accessor)
    │   ├── screen/                         Client-side screens (GUI + EMC editor)
    │   └── util/                           Client-side utilities (keybindings)
    └── resources/
        └── itemalchemyaddon.client.mixins.json    Client mixin config
```

---

## Credits

- **[Pitan](https://github.com/Pitan76)** — Author of [ItemAlchemy](https://github.com/Pitan76/itemalchemy) and [MCPitanLib](https://github.com/Pitan76/mcpitanlib), the foundations this addon is built on.
- **[FabricMC](https://fabricmc.net/)** — The mod loader and toolchain.
- **The Minecraft modding community** — For decades of shared knowledge.

---

## License

This project is licensed under the **MIT License** — see [LICENSE.txt](LICENSE.txt) for details.

```
MIT License

Copyright (c) 2026 Vikotoya

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
...
```

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Issues** — Use the [Issues](../../issues) tab to report bugs or request features. Include your Minecraft version, Fabric Loader version, mod list, and a `latest.log` excerpt for crashes.
2. **Pull Requests** — Fork the repository, create a feature branch, and open a PR against `master`. Keep PRs focused (one feature or fix per PR).
3. **Code style** — Match the existing style: 4-space indentation, Javadoc on public APIs, English-only comments, and no version-specific Minecraft API calls inside files that should remain version-portable (use MCPitanLib equivalents wherever possible).
4. **Testing** — Run `./gradlew runClient` and `./gradlew runServer` before submitting. Verify that:
   - The Tome opens, displays items, and respects all four filter and sort modes.
   - Burning, learning, unlearning, and purchasing all work end-to-end.
   - Search returns expected results in at least two different game languages.

---

*Made with care for the Minecraft modding community.*
