package com.tdm.tournament;

import com.tdm.tournament.bracket.BracketGenerator;
import com.tdm.tournament.model.*;
import com.tdm.tournament.swiss.SwissSystem;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Core logic for tournament creation, lifecycle, and match delegation.
 * This is the central facade used by commands, GUIs, and listeners.
 */
public class TournamentManager {

    private final TournamentPlugin plugin;
    private final Map<UUID, Tournament> tournaments;        // id -> tournament
    private final Map<UUID, Integer> activeMatches;          // team UUID -> match id (teams in active games)

    public TournamentManager(TournamentPlugin plugin) {
        this.plugin = plugin;
        this.tournaments = new HashMap<>();
        this.activeMatches = new HashMap<>();
    }

    // ==================== Tournament CRUD ====================

    public Tournament createTournament(String name, TournamentFormat format,
                                       GameType gameType, int maxTeams, int teamSize) {
        Tournament t = new Tournament(name, format, gameType, maxTeams, teamSize);
        if (format == TournamentFormat.SWISS) {
            t.setSwissRounds(SwissSystem.calculateRounds(maxTeams));
        }
        tournaments.put(t.getId(), t);
        return t;
    }

    public void deleteTournament(UUID id) {
        tournaments.remove(id);
    }

    public Tournament getTournament(UUID id) {
        return tournaments.get(id);
    }

    public Collection<Tournament> getAllTournaments() {
        return tournaments.values();
    }

    public List<Tournament> getOpenTournaments() {
        List<Tournament> result = new ArrayList<>();
        for (Tournament t : tournaments.values()) {
            if (t.getState() == TournamentState.OPEN) result.add(t);
        }
        return result;
    }

    public List<Tournament> getTournamentsForPlayer(UUID playerId) {
        List<Tournament> result = new ArrayList<>();
        for (Tournament t : tournaments.values()) {
            if (t.isPlayerInTournament(playerId)) {
                result.add(t);
            }
        }
        return result;
    }

    // ==================== Team Management ====================

    public boolean joinTournament(UUID tournamentId, Player player, String teamName) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;
        if (t.getState() != TournamentState.OPEN) return false;
        if (t.isPlayerInTournament(player.getUniqueId())) return false;

        // Check if we need to create a new team or join an existing one
        if (t.getTeamSize() == 1) {
            // Solo: create a new team for this player
            String name = (teamName != null && !teamName.isEmpty()) ? teamName : player.getName();
            TournamentTeam team = new TournamentTeam(name, player.getUniqueId());
            return t.addTeam(team);
        } else {
            // Team mode: look for a team with space that the player can join
            // In a GUI, players would typically create named teams
            // For simplicity, create a new team
            String name = (teamName != null && !teamName.isEmpty()) ? teamName : player.getName() + "'s Team";
            TournamentTeam team = new TournamentTeam(name, player.getUniqueId());
            return t.addTeam(team);
        }
    }

    public boolean leaveTournament(UUID tournamentId, Player player) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;
        if (t.getState() != TournamentState.OPEN) return false;

        TournamentTeam team = t.getTeamByPlayer(player.getUniqueId());
        if (team == null) return false;

        team.removeMember(player.getUniqueId());

        // Remove team if empty
        if (team.getSize() == 0) {
            t.removeTeam(team.getId());
        }
        return true;
    }

    // ==================== Tournament Lifecycle ====================

    public boolean startTournament(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null || !t.canStart()) return false;

        t.setState(TournamentState.ACTIVE);
        t.setStartedTime(System.currentTimeMillis());

        // Generate the bracket or first swiss round
        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            BracketGenerator.generateBracket(t);
        } else {
            SwissSystem.generateRound(t);
        }

        return true;
    }

    public boolean cancelTournament(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;
        if (t.getState() == TournamentState.FINISHED || t.getState() == TournamentState.CANCELLED) {
            return false;
        }

        t.setState(TournamentState.CANCELLED);
        t.setEndedTime(System.currentTimeMillis());
        return true;
    }

    public boolean finishTournament(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;

        // Determine winner based on format
        UUID winnerId;
        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            winnerId = t.getWinnerTeamId();
        } else {
            winnerId = SwissSystem.getWinner(t);
        }

        t.setWinnerTeamId(winnerId);
        t.setState(TournamentState.FINISHED);
        t.setEndedTime(System.currentTimeMillis());
        return true;
    }

    // ==================== Match Delegation ====================

    /**
     * Mark a match as in-progress (delegated to TDM/Spleef).
     */
    public void startMatch(UUID tournamentId, int matchId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return;

        Match match = t.getMatch(matchId);
        if (match == null) return;

        match.setStatus(MatchStatus.IN_PROGRESS);
        activeMatches.put(match.getTeam1Id(), matchId);
        activeMatches.put(match.getTeam2Id(), matchId);
    }

    /**
     * Complete a match with a winning team.
     * Handles bracket advancement or swiss standings update.
     */
    public void completeMatch(UUID tournamentId, int matchId, UUID winnerTeamId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return;

        Match match = t.getMatch(matchId);
        if (match == null) return;

        match.setWinnerId(winnerTeamId);
        match.setStatus(MatchStatus.FINISHED);

        // Remove from active matches
        activeMatches.remove(match.getTeam1Id());
        activeMatches.remove(match.getTeam2Id());

        // Format-specific handling
        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            Match nextMatch = BracketGenerator.advanceWinner(t, matchId);

            // Check if tournament is over
            if (BracketGenerator.isComplete(t)) {
                finishTournament(tournamentId);
            }
        }

        // Check if all pending matches in the current round are done (Swiss)
        if (t.getFormat() == TournamentFormat.SWISS) {
            boolean roundComplete = true;
            int maxRound = t.getMaxRound();
            for (Match m : t.getRoundMatches(maxRound)) {
                if (m.getStatus() != MatchStatus.FINISHED) {
                    roundComplete = false;
                    break;
                }
            }

            if (roundComplete) {
                if (SwissSystem.isComplete(t)) {
                    finishTournament(tournamentId);
                } else {
                    // Generate next round
                    SwissSystem.generateRound(t);
                }
            }
        }
    }

    /**
     * Get the match a team is currently playing in, or null.
     */
    public Match getActiveMatchForTeam(UUID tournamentId, UUID teamId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return null;

        Integer matchId = activeMatches.get(teamId);
        if (matchId == null) return null;

        return t.getMatch(matchId);
    }

    /**
     * Get all currently in-progress matches for a tournament.
     */
    public List<Match> getActiveMatches(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return Collections.emptyList();

        List<Match> result = new ArrayList<>();
        for (Match m : t.getMatches()) {
            if (m.getStatus() == MatchStatus.IN_PROGRESS) {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * Get matches ready to be started for a tournament.
     */
    public List<Match> getReadyMatches(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return Collections.emptyList();

        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            return BracketGenerator.getReadyMatches(t);
        } else {
            return SwissSystem.getReadyMatches(t);
        }
    }

    // ==================== Player Queries ====================

    /**
     * Get the next match a player needs to play in their tournament.
     */
    public Match getNextMatchForPlayer(UUID playerId) {
        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.ACTIVE) continue;
            TournamentTeam team = t.getTeamByPlayer(playerId);
            if (team == null) continue;

            // Check if team is in an active match
            Match active = getActiveMatchForTeam(t.getId(), team.getId());
            if (active != null) return active;

            // Check for pending matches
            for (Match m : getReadyMatches(t.getId())) {
                if (team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id())) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Get all matches a player has played across all tournaments.
     */
    public List<Match> getPlayerMatchHistory(UUID playerId) {
        List<Match> history = new ArrayList<>();
        for (Tournament t : tournaments.values()) {
            TournamentTeam team = t.getTeamByPlayer(playerId);
            if (team == null) continue;

            for (Match m : t.getMatches()) {
                if (team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id())) {
                    history.add(m);
                }
            }
        }
        return history;
    }
}
