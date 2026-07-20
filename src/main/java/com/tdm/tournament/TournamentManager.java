package com.tdm.tournament;

import com.tdm.tournament.api.MinigameProvider;
import com.tdm.tournament.bracket.BracketGenerator;
import com.tdm.tournament.model.*;
import com.tdm.tournament.swiss.SwissSystem;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core logic for tournament creation, lifecycle, and match delegation.
 * Works with any {@link MinigameProvider} — no hardcoded minigame names.
 */
public class TournamentManager {

    private final TournamentPlugin plugin;
    private final Map<UUID, Tournament> tournaments;
    private final Map<UUID, String> activeMatches;       // team UUID -> matchId (string)
    private List<MinigameProvider> providers;

    public TournamentManager(TournamentPlugin plugin, List<MinigameProvider> providers) {
        this.plugin = plugin;
        this.tournaments = new HashMap<>();
        this.activeMatches = new HashMap<>();
        this.providers = new ArrayList<>(providers);
    }

    public void refreshProviders(List<MinigameProvider> providers) {
        this.providers = new ArrayList<>(providers);
    }

    // ==================== Provider Queries ====================

    public List<MinigameProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    public Optional<MinigameProvider> getProvider(String name) {
        for (MinigameProvider p : providers) {
            if (p.getPluginName().equalsIgnoreCase(name)
                    || p.getDisplayName().equalsIgnoreCase(name)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public Set<String> getProviderNames() {
        return providers.stream()
                .map(MinigameProvider::getPluginName)
                .collect(Collectors.toSet());
    }

    // ==================== Tournament CRUD ====================

    public Tournament createTournament(String name, TournamentFormat format,
                                       String providerName, int maxTeams, int teamSize) {
        Tournament t = new Tournament(name, format, providerName, maxTeams, teamSize);
        if (format == TournamentFormat.SWISS) {
            t.setSwissRounds(SwissSystem.calculateRounds(maxTeams));
        }
        tournaments.put(t.getId(), t);
        plugin.verbose("Created tournament '" + name + "' (ID: " + t.getId().toString().substring(0, 8)
                + ", format: " + format + ", provider: " + providerName
                + ", maxTeams: " + maxTeams + ", teamSize: " + teamSize + ")");
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
        return tournaments.values().stream()
                .filter(t -> t.getState() == TournamentState.OPEN)
                .collect(Collectors.toList());
    }

    public List<Tournament> getTournamentsForPlayer(UUID playerId) {
        return tournaments.values().stream()
                .filter(t -> t.isPlayerInTournament(playerId))
                .collect(Collectors.toList());
    }

    // ==================== Team Management ====================

    public boolean joinTournament(UUID tournamentId, Player player, String teamName) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;
        if (t.getState() != TournamentState.OPEN) return false;
        if (t.isPlayerInTournament(player.getUniqueId())) return false;

        String name = (teamName != null && !teamName.isEmpty()) ? teamName : player.getName();
        TournamentTeam team = new TournamentTeam(name, player.getUniqueId());
        return t.addTeam(team);
    }

    public boolean leaveTournament(UUID tournamentId, Player player) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;
        if (t.getState() != TournamentState.OPEN) return false;

        TournamentTeam team = t.getTeamByPlayer(player.getUniqueId());
        if (team == null) return false;

        team.removeMember(player.getUniqueId());
        if (team.getSize() == 0) {
            t.removeTeam(team.getId());
        }
        return true;
    }

    // ==================== Tournament Lifecycle ====================

    public boolean startTournament(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null || !t.canStart()) return false;

        // Verify the provider is still available
        Optional<MinigameProvider> providerOpt = getProvider(t.getProviderName());
        if (providerOpt.isEmpty() || !providerOpt.get().isEnabled()) {
            return false;
        }

        t.setState(TournamentState.ACTIVE);
        t.setStartedTime(System.currentTimeMillis());

        plugin.verbose("Starting tournament '" + t.getName() + "' with " + t.getTeamCount() + " teams");

        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            BracketGenerator.generateBracket(t);
            int matchCount = t.getMatches().size();
            int readyCount = BracketGenerator.getReadyMatches(t).size();
            plugin.verbose("Bracket generated: " + matchCount + " total matches, " + readyCount + " ready to play");
        } else {
            SwissSystem.generateRound(t);
            plugin.verbose("Swiss round generated: " + t.getRoundMatches(t.getMaxRound()).size() + " matches");
        }

        return true;
    }

    public boolean cancelTournament(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;
        if (t.getState() == TournamentState.FINISHED || t.getState() == TournamentState.CANCELLED) {
            return false;
        }

        // Cancel any active matches
        for (Match m : t.getMatches()) {
            if (m.getStatus() == MatchStatus.IN_PROGRESS) {
                cancelMatch(t.getId(), m.getId());
            }
        }

        t.setState(TournamentState.CANCELLED);
        t.setEndedTime(System.currentTimeMillis());
        return true;
    }

    public boolean finishTournament(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return false;

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

    // ==================== Match Delegation via Providers ====================

    /**
     * Delegate a match to its minigame provider to start playing.
     */
    public boolean startMatch(UUID tournamentId, int matchId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) {
            plugin.verbose("startMatch failed: tournament not found (ID: " + tournamentId + ")");
            return false;
        }

        Match match = t.getMatch(matchId);
        if (match == null) {
            plugin.verbose("startMatch failed: match " + matchId + " not found in tournament '" + t.getName() + "'");
            return false;
        }

        Optional<MinigameProvider> providerOpt = getProvider(t.getProviderName());
        if (providerOpt.isEmpty() || !providerOpt.get().isEnabled()) {
            plugin.verbose("startMatch failed: provider '" + t.getProviderName() + "' not available");
            return false;
        }

        MinigameProvider provider = providerOpt.get();

        // Get players for each team
        TournamentTeam team1 = t.getTeam(match.getTeam1Id());
        TournamentTeam team2 = t.getTeam(match.getTeam2Id());
        if (team1 == null || team2 == null) {
            plugin.verbose("startMatch failed: team1=" + (team1 != null) + " team2=" + (team2 != null));
            return false;
        }

        List<UUID> team1Players = team1.getMembers();
        List<UUID> team2Players = team2.getMembers();

        plugin.verbose("Starting match " + matchId + " in '" + t.getName()
                + "': " + team1.getName() + " (" + team1Players.size() + " players) vs "
                + team2.getName() + " (" + team2Players.size() + " players)");

        // Pick an arena (first available, or from match preference)
        List<String> arenas = provider.getAvailableArenas();
        if (arenas.isEmpty()) {
            plugin.getLogger().warning("Provider " + provider.getPluginName() + " has no arenas available!");
            plugin.verbose("startMatch failed: no arenas from provider " + provider.getPluginName());
            return false;
        }

        String arena = arenas.get(0); // simple: first arena
        String matchIdStr = tournamentId + ":" + matchId;

        plugin.verbose("Calling provider.createMatch(arena=" + arena + ", matchId=" + matchIdStr + ")");
        boolean created = provider.createMatch(arena, team1Players, team2Players, matchIdStr);
        if (!created) {
            plugin.verbose("startMatch failed: provider.createMatch returned false");
            return false;
        }

        match.setStatus(MatchStatus.IN_PROGRESS);
        match.setArenaName(arena);
        activeMatches.put(match.getTeam1Id(), matchIdStr);
        activeMatches.put(match.getTeam2Id(), matchIdStr);
        plugin.verbose("Match " + matchId + " started successfully on arena '" + arena + "'");
        return true;
    }

    /**
     * Called by {@link com.tdm.tournament.listener.MatchEndListener} when a
     * {@link com.tdm.tournament.api.MatchCompleteEvent} is received.
     */
    public void completeMatch(String matchIdStr, List<UUID> winningPlayers, boolean tie) {
        // Parse the match ID string: "tournamentId:matchId"
        String[] parts = matchIdStr.split(":", 2);
        if (parts.length != 2) {
            plugin.verbose("completeMatch: invalid matchIdStr '" + matchIdStr + "'");
            return;
        }

        UUID tournamentId;
        int matchId;
        try {
            tournamentId = UUID.fromString(parts[0]);
            matchId = Integer.parseInt(parts[1]);
        } catch (IllegalArgumentException e) {
            plugin.verbose("completeMatch: failed to parse '" + matchIdStr + "': " + e.getMessage());
            return;
        }

        Tournament t = getTournament(tournamentId);
        if (t == null) {
            plugin.verbose("completeMatch: tournament not found (ID: " + tournamentId + ")");
            return;
        }

        Match match = t.getMatch(matchId);
        if (match == null) {
            plugin.verbose("completeMatch: match " + matchId + " not found in '" + t.getName() + "'");
            return;
        }

        plugin.verbose("completeMatch called for '" + t.getName() + "' match " + matchId
                + " (tie=" + tie + ", winners=" + winningPlayers.size() + " players)");

        // Clean up active match tracking
        activeMatches.remove(match.getTeam1Id());
        activeMatches.remove(match.getTeam2Id());

        if (tie || winningPlayers.isEmpty()) {
            // Tie — no winner advances; reset match to PENDING so it can be replayed
            match.setStatus(MatchStatus.PENDING);
            match.setWinnerId(null);
            match.setArenaName(null);
            plugin.verbose("Match " + matchId + " in '" + t.getName() + "' ended in a tie — reset to PENDING for replay.");
            plugin.getLogger().info("Match " + matchId + " in " + t.getName() + " ended in a tie. Match reset to PENDING — click Start Next Match to replay.");
            return;
        }

        // Determine which team won based on the winning players
        UUID winnerId = determineWinnerTeam(t, match, winningPlayers);
        if (winnerId == null) {
            plugin.verbose("completeMatch: could not determine winning team for match " + matchId);
            plugin.getLogger().warning("Could not determine winning team for match " + matchId);
            match.setStatus(MatchStatus.FINISHED);
            return;
        }

        match.setWinnerId(winnerId);
        match.setStatus(MatchStatus.FINISHED);
        plugin.verbose("Match " + matchId + " winner: team " + winnerId.toString().substring(0, 8));

        // Format-specific advancement
        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            BracketGenerator.advanceWinner(t, matchId);
            if (BracketGenerator.isComplete(t)) {
                plugin.verbose("Tournament '" + t.getName() + "' is complete! Champion determined.");
                finishTournament(tournamentId);
            }
        } else if (t.getFormat() == TournamentFormat.SWISS) {
            checkSwissRoundComplete(t);
        }

        plugin.getLogger().info("Match " + matchId + " in " + t.getName() + " completed.");
    }

    private UUID determineWinnerTeam(Tournament t, Match match, List<UUID> winningPlayers) {
        // Check which team's members match the winning players
        for (UUID teamId : List.of(match.getTeam1Id(), match.getTeam2Id())) {
            if (teamId == null) continue;
            TournamentTeam team = t.getTeam(teamId);
            if (team == null) continue;

            boolean allWinners = true;
            for (UUID memberId : team.getMembers()) {
                if (!winningPlayers.contains(memberId)) {
                    allWinners = false;
                    break;
                }
            }
            if (allWinners && !team.getMembers().isEmpty()) {
                return team.getId();
            }
        }

        // Fallback: first winning player's team
        if (!winningPlayers.isEmpty()) {
            TournamentTeam team = t.getTeamByPlayer(winningPlayers.get(0));
            if (team != null && (team.getId().equals(match.getTeam1Id())
                    || team.getId().equals(match.getTeam2Id()))) {
                return team.getId();
            }
        }

        return null;
    }

    private void checkSwissRoundComplete(Tournament t) {
        int maxRound = t.getMaxRound();
        boolean roundComplete = true;
        for (Match m : t.getRoundMatches(maxRound)) {
            if (m.getStatus() != MatchStatus.FINISHED) {
                roundComplete = false;
                break;
            }
        }

        if (roundComplete) {
            if (SwissSystem.isComplete(t)) {
                finishTournament(t.getId());
            } else {
                SwissSystem.generateRound(t);
            }
        }
    }

    /**
     * Cancel a match via its provider.
     */
    private void cancelMatch(UUID tournamentId, int matchId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return;

        Match match = t.getMatch(matchId);
        if (match == null) return;

        String matchIdStr = tournamentId + ":" + matchId;
        Optional<MinigameProvider> providerOpt = getProvider(t.getProviderName());
        providerOpt.ifPresent(p -> p.cancelMatch(matchIdStr));

        match.setStatus(MatchStatus.PENDING);
        activeMatches.remove(match.getTeam1Id());
        activeMatches.remove(match.getTeam2Id());
    }

    // ==================== Queries ====================

    public Match getActiveMatchForTeam(UUID tournamentId, UUID teamId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return null;

        String matchIdStr = activeMatches.get(teamId);
        if (matchIdStr == null) return null;

        for (Match m : t.getMatches()) {
            String expected = tournamentId + ":" + m.getId();
            if (expected.equals(matchIdStr)) return m;
        }
        return null;
    }

    public List<Match> getActiveMatches(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return Collections.emptyList();

        return t.getMatches().stream()
                .filter(m -> m.getStatus() == MatchStatus.IN_PROGRESS)
                .collect(Collectors.toList());
    }

    public List<Match> getReadyMatches(UUID tournamentId) {
        Tournament t = getTournament(tournamentId);
        if (t == null) return Collections.emptyList();

        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            return BracketGenerator.getReadyMatches(t);
        } else {
            return SwissSystem.getReadyMatches(t);
        }
    }

    public Match getNextMatchForPlayer(UUID playerId) {
        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.ACTIVE) continue;
            TournamentTeam team = t.getTeamByPlayer(playerId);
            if (team == null) continue;

            Match active = getActiveMatchForTeam(t.getId(), team.getId());
            if (active != null) return active;

            for (Match m : getReadyMatches(t.getId())) {
                if (team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id())) {
                    return m;
                }
            }
        }
        return null;
    }

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
