# TournamentManager

Orchestrates tournaments using any minigame plugin that implements the **`MinigameProvider`** interface.

## Overview

TournamentManager is a Paper plugin that adds tournament-level organization on top of existing minigame plugins (Spleef, TeamDeathmatch, or any custom plugin). Instead of running games directly, it discovers **MinigameProvider** implementations at runtime via Bukkit's `ServicesManager` and delegates matches to them.

**No hard dependency on any specific minigame** — the plugin is fully generic. Any plugin that implements `MinigameProvider` can be used.

## How It Works

1. **Minigame plugins** (Spleef, TDM, etc.) implement the `MinigameProvider` interface and register it via `ServicesManager`.
2. **TournamentManager** discovers all providers on startup and displays them in the admin GUI.
3. **Admin** creates a tournament and selects which installed minigame to use.
4. When matches are ready, the tournament calls `provider.createMatch()` to delegate the game.
5. When the game ends, the provider fires a `MatchCompleteEvent`.
6. TournamentManager receives the event, determines the winner, and advances the bracket.

## Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| Paper API | Hard | Minecraft server API |

Minigame providers (Spleef, TeamDeathmatch, etc.) are discovered at runtime — no compile-time dependency.

## Supported Formats

- **Single Elimination** — classic bracket, lose and you're out.
- **Swiss System** — play a set number of rounds, best overall record wins.

## Tournament Lifecycle

1. **Admin creates** a tournament (via GUI or command).
2. **Players join** via GUI (solo or pre-made teams).
3. **Admin starts** the tournament.
4. Plugin creates **matches** — delegates actual games to the chosen minigame provider.
5. **Results** come back via `MatchCompleteEvent` from the provider.
6. Plugin advances bracket / updates Swiss standings.
7. **Champion** crowned, stats recorded.

## GUIs

### Admin
- **Main Menu** — Create Tournament, Manage Tournaments, Installed Minigames
- **Create Tournament** — Configure name, format, minigame provider, team size, max teams
- **Manage Tournaments** — Start, Cancel, Force End, View Bracket/Standings, View Teams
- **Bracket View** — Visual bracket tree by round
- **Swiss Standings** — Ranked table with win/loss records
- **Installed Minigames** — Shows all available providers and their arenas

### Player
- **Open Tournaments** — Paginated list of joinable tournaments
- **Tournament Details** — Info, Join/Leave, View Bracket/Standings
- **My Matches** — Current/upcoming matches
- **Match History** — Past matches with W/L indicators

## Commands

| Command | Description |
|---------|-------------|
| `/tournament` | Open player GUI (aliases: `/tourney`, `/t`) |
| `/tournament admin` | Open admin GUI |
| `/tournament create` | Open create tournament GUI |
| `/tournament join <id>` | Join a tournament by ID |
| `/tournament leave <id>` | Leave a tournament |
| `/tournament start <id>` | Start a tournament (admin) |
| `/tournament cancel <id>` | Cancel a tournament (admin) |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `tournament.admin` | op | Admin commands and management GUIs |
| `tournament.player` | true | Join and view tournaments |

## Creating a MinigameProvider

Any plugin can integrate with TournamentManager by implementing the `MinigameProvider` interface:

```java
public class MyGameProvider implements MinigameProvider {
    @Override public String getPluginName() { return "MyGame"; }
    @Override public String getDisplayName() { return "MyGame"; }
    @Override public Material getIcon() { return Material.DIAMOND_SWORD; }
    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public List<String> getAvailableArenas() { return List.of("arena1"); }
    
    @Override
    public boolean createMatch(String arena, List<UUID> team1,
                               List<UUID> team2, String matchId) {
        // Start your game here
        return true;
    }
    
    @Override public void cancelMatch(String matchId) { /* cleanup */ }
}
```

Register it in your plugin's `onEnable()`:
```java
getServer().getServicesManager().register(
    MinigameProvider.class, myProvider, this, ServicePriority.Normal
);
```

When the game ends, fire a `MatchCompleteEvent`:
```java
MatchCompleteEvent event = new MatchCompleteEvent(
    getPluginName(), matchId, winningPlayerUuids, arena, false);
Bukkit.getPluginManager().callEvent(event);
```

## Building

```powershell
./build.ps1
```

Requires JDK 21+.
