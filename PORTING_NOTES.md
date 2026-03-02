# FarmHelper v2 -> Fabric Porting Notes

## Scope

Target runtime:
- Minecraft `1.21.11`
- Fabric Loader `0.18.4`
- Yarn mappings `1.21.11+build.4`

Legacy source:
- `/Users/max/Downloads/projects/FarmHelper-master`

## Current migration status

Implemented:
- Bootstrapped Fabric mod structure.
- Config persistence with migration sanitization.
- Legacy-style settings model for macro/failsafe/hud/discord/feature toggles.
- Macro lifecycle state machine.
- Crop-specific macro logic in the movement executor (legacy macro families mapped to dedicated movement modes, yaw/pitch tuning, and mode-specific key patterns).
- Client runtime snapshot capture (position/yaw/pitch/velocity/inventory/status-effects/nearby-blocks/chat).
- Client-side movement executor with configurable patterns, full legacy macro type list, and per-type profiles.
- Detector-based failsafe pipeline covering legacy failsafe categories.
- Improved failsafe lifecycle with configurable hold delay, optional in-chat reaction, and optional macro restart policy.
- In-game settings UI screens + command/keybind entry points.
- Discord webhook notifications (macro/failsafe/status updates).
- Modular feature system with all legacy feature IDs registered plus concrete command-driven modules for:
  - `auto_sell`
  - `visitors_macro`
  - `pests_destroyer`
  - `auto_composter`
  - `auto_pest_exchange`
  - `auto_god_pot`
  - `auto_repellent`
  - `auto_cookie`
  - `auto_bazaar`
  - `pet_swapper`
  - `plot_cleaning_helper`
- Macro-exclusive timed action base class and client action queue for maintainable feature orchestration.
- Client automation executor that handles pathing, entity interaction, GUI actions, hotbar selection, and nearest-block mining as reusable primitives.
- A* pathfinder service with stuck recovery + replanning, used by the automation executor.
- GUI decision engine with screen-title checks, name/lore slot matching, and retry-aware actions.
- Pest target heuristics using legacy-aligned texture signature fragments and metadata scoring.
- Feature modules upgraded to explicit state machines for visitors/pests/autosell/composter/pest-exchange/god-pot/repellent/cookie/bazaar/pet-swapper/plot-cleaning.
- Legacy resources imported to `assets/farmhelperfabric/legacy/farmhelper/`.

Pending:
- Further tune crop-state edge behaviors (anti-stuck, lane correctness, and wall hugging) to close remaining parity gaps.
- Port event-driven failsafes (packet/chat/block-based detections).
- Improve route/menu heuristics with world-specific edge-case handling and recovery branches.
- Add optional alternate navigation backend (e.g., Baritone bridge) behind the same pathing interface.

## Security posture during port

Current Fabric project includes Discord webhook notifications only.
Remote control over websockets/Discord bot is not ported and remains disabled by default.

Legacy networking modules to treat as opt-in and security-reviewed before any port:
- `remote/*` (Discord + websocket remote control)
- `feature/impl/BanInfoWS.java` (analytics websocket)
- webhook helpers in `util/LogUtils.java`

Recommendation:
- Keep all remote functionality disabled by default.
- Add explicit in-game consent and visible status indicators before enabling any outbound integration.
- Require token storage hardening and endpoint allowlists before reintroducing remote features.
