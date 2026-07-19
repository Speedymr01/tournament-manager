package com.tdm.tournament.listener;

import com.tdm.api.TDMAPI;
import com.tdm.api.event.TDMGameEndEvent;
import com.tdm.spleef.api.SpleefAPI;
import com.tdm.spleef.api.event.SpleefGameEndEvent;
import com.tdm.tournament.TournamentManager;
import com.tdm.tournament.TournamentPlugin;
import com.tdm.tournament.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Listens for TDM and Spleef game-end events and maps results
 * back to tournament matches.
 */
public class GameEndListener implements Listener {

    private final TournamentPlugin plugin;
    private final TournamentManager manager;

    public GameEndListener(TournamentPlugin plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================== Spleef Integration ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpleefGameEnd(SpleefGameEndEvent event) {
        // Get the arena name from the game
        String arenaName = event.getGame().getArena().getName();

        // Look up which tournament match is using this arena
        resolveMatchForArena(arenaName, event.getWinners());
    }

    // ==================== TDM Integration ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTDMGameEnd(TDMGameEndEvent event) {
        // TDM doesn't have a direct arena name concept in its end event.
        // We map via the players involved - find active matches containing
        // players from this TDM game.

        // Get the TDM API to find out who was playing
        Optional<TDMAPI> apiOpt = getTDMAPI();
        if (apiOpt.isEmpty()) return;

        TDMAPI api = apiOpt.get();
        List<Player> players = api.getPlayersInGame();

        // Find which tournament this game belongs to by looking up players
        for (Player player : players) {
            UUID playerId = player.getUniqueId();

            // Search through active tournaments for this player's team
            for (com.tdm.tournament.model.Tournament t : manager.getAllTournaments()) {
                if (t.getState() != TournamentState.ACTIVE) continue;
                TournamentTeam team = t.getTeamByPlayer(playerId);
                if (team == null) continue;

                // Check if this team has an active match
                Match activeMatch = manager.getActiveMatchForTeam(t.getId(), team.getId());
                if (activeMatch != null && activeMatch.getStatus() == MatchStatus.IN_PROGRESS) {
                    // Determine winner based on TDMGameEndEvent
                    UUID winnerTeamId = determineTDMWinner(event, t, activeMatch);
                    if (winnerTeamId != null) {
                        manager.completeMatch(t.getId(), activeMatch.getId(), winnerTeamId);
                        plugin.getLogger().info("TDM match completed: " + t.getName()
                                + " match " + activeMatch.getId());
                    }
                    return; // handled
                }
            }
        }
    }

    // ==================== Arena-based Match Resolution ====================

    /**
     * When a match is delegated to Spleef, we store the arena name on the match.
     * When the Spleef game ends on that arena, we resolve back to the match.
     */
    private void resolveMatchForArena(String arenaName, List<Player> winners) {
        for (com.tdm.tournament.model.Tournament t : manager.getAllTournaments()) {
            if (t.getState() != TournamentState.ACTIVE) continue;

            for (Match match : t.getMatches()) {
                if (match.getStatus() != MatchStatus.IN_PROGRESS) continue;
                if (arenaName.equals(match.getArenaName())) {
                    // Map the winner players back to a team
                    UUID winnerTeamId = determineSpleefWinner(winners, t, match);
                    if (winnerTeamId != null) {
                        manager.completeMatch(t.getId(), match.getId(), winnerTeamId);
                        plugin.getLogger().info("Spleef match completed: " + t.getName()
                                + " match " + match.getId());
                    }
                    return;
                }
            }
        }
    }

    /**
     * Determine which tournament team won based on Spleef's winner list.
     */
    private UUID determineSpleefWinner(List<Player> winners,
                                        com.tdm.tournament.model.Tournament t, Match match) {
        if (winners == null || winners.isEmpty()) return null;

        // If solo tournament, the winner is the team containing the winning player
        if (t.getTeamSize() == 1 && !winners.isEmpty()) {
            UUID winnerId = winners.get(0).getUniqueId();
            TournamentTeam team = t.getTeamByPlayer(winnerId);
            return team != null ? team.getId() : null;
        }

        // For team tournaments, find which team has ALL its members in the winners list
        for (TournamentTeam team : t.getTeams()) {
            if (team.getId().equals(match.getTeam1Id()) || team.getId().equals(match.getTeam2Id())) {
                boolean allWon = true;
                for (UUID memberId : team.getMembers()) {
                    boolean found = false;
                    for (Player winner : winners) {
                        if (winner.getUniqueId().equals(memberId)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        allWon = false;
                        break;
                    }
                }
                if (allWon) return team.getId();
            }
        }

        // Fallback: first winner's team
        if (!winners.isEmpty()) {
            TournamentTeam team = t.getTeamByPlayer(winners.get(0).getUniqueId());
            if (team != null) return team.getId();
        }

        return null;
    }

    /**
     * Determine which tournament team won based on TDM's game end event.
     */
    private UUID determineTDMWinner(TDMGameEndEvent event,
                                     com.tdm.tournament.model.Tournament t, Match match) {
        // TDM uses the Team enum (RED/BLUE etc). We need to map this back
        // to our tournament teams. Since we assigned players to TDM teams,
        // we check which team's members are on the winning TDM team.

        com.tdm.GameManager.Team tdmWinner = event.getWinner();
        if (tdmWinner == null) return null;

        // Get the TDM API to check player teams
        Optional<TDMAPI> apiOpt = getTDMAPI();
        if (apiOpt.isEmpty()) return null;

        TDMAPI api = apiOpt.get();

        // For each of the two teams in this match, check if they were on the winning TDM team
        for (UUID teamId : new UUID[]{match.getTeam1Id(), match.getTeam2Id()}) {
            if (teamId == null) continue;
            TournamentTeam team = t.getTeam(teamId);
            if (team == null) continue;

            boolean allOnWinningTeam = true;
            for (UUID memberId : team.getMembers()) {
                com.tdm.GameManager.Team playerTdmTeam = api.getPlayerTeam(memberId);
                if (playerTdmTeam != tdmWinner) {
                    allOnWinningTeam = false;
                    break;
                }
            }

            if (allOnWinningTeam && team.getMembers().size() > 0) {
                return team.getId();
            }
        }

        return null;
    }

    // ==================== API Lookup ====================

    private Optional<TDMAPI> getTDMAPI() {
        if (!Bukkit.getPluginManager().isPluginEnabled("TeamDeathmatch")) {
            return Optional.empty();
        }
        var registration = Bukkit.getServicesManager().getRegistration(TDMAPI.class);
        if (registration == null) return Optional.empty();
        return Optional.ofNullable(registration.getProvider());
    }
}
