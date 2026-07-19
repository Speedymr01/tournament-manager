# TournamentManager

Orchestrates tournaments using **TDMAPI** and **SpleefAPI** via Bukkit's `ServicesManager`.

## Overview

TournamentManager is a private Paper plugin that connects to existing minigame plugins (TeamDeathmatch, Spleef) and adds tournament-level organization on top. Instead of running games directly, it delegates actual matches to TDM/Spleef and consumes their API events to determine winners and advance brackets.

## Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| Paper API | Hard | Minecraft server API |
| TeamDeathmatch | Soft | PvP match API (`TDMAPI`) |
| Spleef | Soft | Spleef match API (`SpleefAPI`) |

## Supported Formats

- **Single Elimination** — classic bracket, lose and you're out.
- **Swiss System** — play a set number of rounds, best overall record wins.

## Tournament Lifecycle

1. **Admin creates** a tournament (via GUI or command).
2. **Players join** via GUI (solo or pre-made teams).
3. **Admin starts** the tournament.
4. Plugin creates **matches** — delegates actual games to TDM/Spleef via their APIs.
5. **Results** come back via `TDMGameEndEvent` / `SpleefGameEndEvent`.
6. Plugin advances bracket / updates Swiss standings.
7. **Champion** crowned, stats recorded.

## Commands

- `/tournament` — Main command (aliases: `/tourney`, `/t`)

## Permissions

- `tournament.admin` — Admin commands (default: op)
- `tournament.player` — Join and view tournaments (default: true)

## Building

```powershell
./build.ps1
```

Requires JDK 21+.
