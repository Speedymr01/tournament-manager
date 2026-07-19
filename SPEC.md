# tournament-manager — Spec

## Overview
Private Paper plugin that orchestrates tournaments using the **TDMAPI** and **SpleefAPI**
via Bukkit's `ServicesManager`. The other plugins expose the APIs; this one consumes them.

## Dependencies
- **Hard**: Paper API
- **Soft**: TeamDeathmatch (`TDMAPI`), Spleef (`SpleefAPI`)
- **(optional)**: Vault / economy for prize pools

## Supported Formats
1. **Single Elimination** — classic bracket, lose and you're out.
2. **Swiss System** — play a set number of rounds, best overall record wins.

## Tournament Lifecycle
1. **Admin creates** a tournament (via GUI or command).
2. **Players join** via GUI (solo or pre-made teams).
3. **Admin starts** the tournament.
4. Plugin creates **matches** — delegates actual games to TDM/Spleef via their APIs.
5. **Results** come back via `TDMGameEndEvent` / `SpleefGameEndEvent`.
6. Plugin advances bracket / updates Swiss standings.
7. **Champion** crowned, stats recorded.

## GUI (Admin & Player)
- **Admin GUI** — create/delete tournaments, manage teams, force start/end/cancel,
  view bracket tree or Swiss table.
- **Player GUI** — view open tournaments, join/leave, see upcoming matches,
  bracket/standings, match history.
- Style mirrors TDM's intuitive inventory GUI.

## API Integration (consumption only)
- Lookup `TDMAPI` / `SpleefAPI` from `Bukkit.getServicesManager()`.
- Call `api.createGame(arena, teams)` to start matches.
- Listen for API events (`TDMGameEndEvent`, `SpleefGameEndEvent`, etc.) to determine winners.
- Plugin does **not** expose its own API — self-contained.

## CI/CD
- Spleef-style workflow **minus Modrinth** — build, test, create GitHub Release on tag push.
- No Modrinth project (private repo).

## Versioning
- Start at `v0.1.0`
