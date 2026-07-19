package com.tdm.tournament.swiss;

import com.tdm.tournament.model.Match;
import com.tdm.tournament.model.MatchStatus;
import com.tdm.tournament.model.Tournament;
import com.tdm.tournament.model.TournamentTeam;

import java.util.*;

/**
 * Swiss-system tournament management.
 *
 * In each round, teams with similar records are paired.
 * Pairings avoid rematches.
 * After a configurable number of rounds, the team with the best record wins.
 */
public class SwissSystem {

    private SwissSystem() {}

    /**
     * Calculate the optimal number of swiss rounds for a given number of teams.
     * Typically ceil(log2(N)), at least 3, at most 10.
     */
    public static int calculateRounds(int numTeams) {
        int rounds = (int) Math.ceil(Math.log(numTeams) / Math.log(2));
        return Math.max(3, Math.min(rounds, 10));
    }

    /**
     * Generate pairings for a new round in a Swiss tournament.
     * Teams are sorted by score (wins), then paired top-half vs bottom-half,
     * avoiding previous matchups.
     *
     * @return list of new Match objects for the round, or empty if tournament over
     */
    public static List<Match> generateRound(Tournament tournament) {
        List<TournamentTeam> teams = tournament.getTeams();
        int numTeams = teams.size();
        if (numTeams < 2) return Collections.emptyList();

        // Determine current round number
        int currentRound = tournament.getMaxRound() + 1;
        if (currentRound < 0) currentRound = 0;

        int maxRounds = tournament.getSwissRounds();
        if (maxRounds <= 0) {
            maxRounds = calculateRounds(numTeams);
            tournament.setSwissRounds(maxRounds);
        }

        if (currentRound >= maxRounds) {
            return Collections.emptyList(); // tournament complete
        }

        // Build a map of team wins and previous opponents
        Map<UUID, Integer> wins = new HashMap<>();
        Map<UUID, Set<UUID>> previousOpponents = new HashMap<>();

        for (TournamentTeam team : teams) {
            wins.put(team.getId(), 0);
            previousOpponents.put(team.getId(), new HashSet<>());
        }

        for (Match match : tournament.getMatches()) {
            if (match.getStatus() != MatchStatus.FINISHED) continue;
            UUID winner = match.getWinnerId();
            if (winner != null) {
                wins.merge(winner, 1, Integer::sum);
            }
            // Track opponents
            UUID team1 = match.getTeam1Id();
            UUID team2 = match.getTeam2Id();
            if (team1 != null && team2 != null) {
                previousOpponents.get(team1).add(team2);
                previousOpponents.get(team2).add(team1);
            }
        }

        // Sort teams by wins descending
        List<TournamentTeam> sortedTeams = new ArrayList<>(teams);
        sortedTeams.sort((a, b) -> Integer.compare(
                wins.getOrDefault(b.getId(), 0),
                wins.getOrDefault(a.getId(), 0)));

        // Greedy pairing: top unpaired vs bottom unpaired, skip rematches
        List<TournamentTeam> unpaired = new ArrayList<>(sortedTeams);
        List<Match> newMatches = new ArrayList<>();
        int matchId = tournament.nextMatchId();

        while (unpaired.size() >= 2) {
            TournamentTeam top = unpaired.get(0);
            TournamentTeam bottom = null;

            // Find the best opponent for top (avoid rematches)
            for (int i = unpaired.size() - 1; i >= 1; i--) {
                TournamentTeam candidate = unpaired.get(i);
                if (!previousOpponents.get(top.getId()).contains(candidate.getId())) {
                    bottom = candidate;
                    break;
                }
            }

            if (bottom == null) {
                // No valid opponent found (all possible are rematches) — just pair with last
                bottom = unpaired.get(unpaired.size() - 1);
            }

            // Remove paired teams
            unpaired.remove(top);
            unpaired.remove(bottom);

            Match match = new Match(matchId++, currentRound,
                    top.getId(), bottom.getId());
            match.setStatus(MatchStatus.PENDING);
            newMatches.add(match);
        }

        // If odd team left, they get a bye (auto win)
        if (unpaired.size() == 1) {
            TournamentTeam byeTeam = unpaired.get(0);
            Match bye = new Match(matchId++, currentRound,
                    byeTeam.getId(), null);
            bye.setWinnerId(byeTeam.getId());
            bye.setStatus(MatchStatus.FINISHED);
            newMatches.add(bye);
        }

        // Add matches to tournament
        for (Match m : newMatches) {
            tournament.addMatch(m);
        }

        return newMatches;
    }

    /**
     * Get the current standings for a Swiss tournament.
     * Returns a map of team ID -> (wins, tiebreakers).
     */
    public static List<SwissStanding> getStandings(Tournament tournament) {
        Map<UUID, SwissStanding> standingMap = new HashMap<>();

        for (TournamentTeam team : tournament.getTeams()) {
            standingMap.put(team.getId(), new SwissStanding(team.getId(), 0, 0));
        }

        for (Match match : tournament.getMatches()) {
            if (match.getStatus() != MatchStatus.FINISHED) continue;
            UUID winner = match.getWinnerId();
            if (winner != null) {
                standingMap.get(winner).wins++;
            }
            // Both teams get a match counted
            if (match.getTeam1Id() != null) standingMap.get(match.getTeam1Id()).matchesPlayed++;
            if (match.getTeam2Id() != null) standingMap.get(match.getTeam2Id()).matchesPlayed++;
        }

        List<SwissStanding> result = new ArrayList<>(standingMap.values());
        result.sort((a, b) -> {
            // Sort by wins descending, then by matches played ascending
            int cmp = Integer.compare(b.wins, a.wins);
            if (cmp != 0) return cmp;
            return Integer.compare(a.matchesPlayed, b.matchesPlayed);
        });
        return result;
    }

    /**
     * Check if the Swiss tournament is complete (all rounds played).
     */
    public static boolean isComplete(Tournament tournament) {
        int rounds = tournament.getSwissRounds();
        if (rounds <= 0) return false;
        return tournament.getMaxRound() >= rounds - 1;
    }

    /**
     * Get the winning team ID from Swiss standings (best record).
     */
    public static UUID getWinner(Tournament tournament) {
        if (!isComplete(tournament)) return null;
        List<SwissStanding> standings = getStandings(tournament);
        if (standings.isEmpty()) return null;
        return standings.get(0).teamId;
    }

    /**
     * Returns matches that are ready to be played in the current swiss round.
     * These are PENDING matches in the latest round.
     */
    public static List<Match> getReadyMatches(Tournament tournament) {
        int maxRound = tournament.getMaxRound();
        if (maxRound < 0) return Collections.emptyList();

        List<Match> roundMatches = tournament.getRoundMatches(maxRound);
        List<Match> ready = new ArrayList<>();
        for (Match m : roundMatches) {
            if (m.getStatus() == MatchStatus.PENDING && !m.isBye()) {
                ready.add(m);
            }
        }
        return ready;
    }

    public static class SwissStanding {
        public final UUID teamId;
        public int wins;
        public int matchesPlayed;

        public SwissStanding(UUID teamId, int wins, int matchesPlayed) {
            this.teamId = teamId;
            this.wins = wins;
            this.matchesPlayed = matchesPlayed;
        }
    }
}
