# FarmHelper Fabric (1.21.11)
## everything is still work in progress
  - expect bugs expect things not to work, general farming works fine  

Fabric reimplementation workspace for migrating FarmHelper v2 (legacy 1.8.9 Forge) to modern Minecraft.

## Current migration status

Implemented in this repository:

- Fabric project scaffold for `1.21.11` (`loader 0.18.4`, Yarn mappings, Fabric API)
- Expanded config model with legacy-style macro/failsafe/discord/hud/feature settings
- In-game UI screens:
  - main config hub
  - macro settings
  - failsafe settings
  - discord/webhook settings
  - feature toggle pages
  - HUD/misc settings
- Macro runtime and movement executor:
  - legacy macro type enum (all legacy macro type options)
  - per-type strategy profiles (default pattern and timing per legacy macro type)
  - configurable pattern/movement timing
  - crop-specific movement modes ported from legacy macro families (vertical/lane, sugar cane sweep, cocoa strafe, mushroom variants, circular)
  - crop-specific yaw/pitch tuning defaults and mushroom rotate yaw-offset behavior
  - optional auto-tool selection by macro/crop profile
- Failsafe manager with queueing, reasons, cooldowns, and detector pipeline
- Failsafe detectors for legacy failsafe categories (world/disconnect/teleport/rotation/knockback/inventory/effects/chat-keyword/environment blocks/item-change/low-bps)
- Runtime snapshot pipeline (movement deltas, status effects, nearby blocks, inventory pressure, slot changes)
- Modular feature architecture (all legacy feature IDs registered with concrete modules for scheduler/leave-timer/auto-reconnect/auto-sell/visitors/pests/auto-composter/auto-pest-exchange/auto-god-pot/auto-repellent/auto-cookie/auto-bazaar/pet-swapper/plot-cleaning-helper)
- Macro-exclusive timed action framework for automation modules with clean pause/resume semantics
- Client action queue pipeline for safe command/message dispatch (rate-limited and deduplicated)
- Client automation executor with reusable action types:
  - pathing to coordinates/entities
  - entity interaction/attack
  - GUI wait/click/close actions
  - hotbar item selection by name query
  - nearest-block mining action for plot-cleaning sweeps
- A* pathfinder service with waypoint navigation and stuck-triggered replanning
- GUI decision engine with title checks, name/lore-aware slot scoring, and retry-aware action handling
- Pest entity heuristics with name/type/tag checks and legacy head-texture signature fragments
- Multi-stage GUI/pathing state machines in module flows (`auto_sell`, `visitors_macro`, `pests_destroyer`, `auto_composter`, `auto_pest_exchange`, `auto_god_pot`, `auto_repellent`, `auto_cookie`, `auto_bazaar`, `pet_swapper`, `plot_cleaning_helper`)
- Improved failsafe lifecycle (detector queueing, reactions, configurable hold delay, optional restart policy)
- Discord webhook notification service (logs, failsafes, status updates)
- Remote control intentionally disabled (no websocket/JDA remote control integration yet)
- Legacy resource import under:
  - `assets/farmhelperfabric/legacy/farmhelper/` (textures, sounds, movrec files)

## Command surface

`/fh` command includes:

- `toggle`, `start`, `stop`, `status`
- `macro type <LEGACY_ENUM_NAME>`
- `macro pattern <BASIC_ROW|S_SHAPE>`
- `feature <id> <true|false>`
- `failsafe <type>`, `failsafe clear`
- `ui`
- `config save`, `config reload`

## Build

```bash
./gradlew build
```

## Remaining port work

1. Continue refining crop-state edge cases (lane detection/anti-stuck parity) against live garden layouts.
2. Expand detector heuristics to match old packet-level behavior and server-specific checks.
3. Tune per-feature heuristics and route/menu strategies against live Hypixel behavior for parity hardening.
4. Add modern secure auth and explicit consent flow before considering remote control features.
