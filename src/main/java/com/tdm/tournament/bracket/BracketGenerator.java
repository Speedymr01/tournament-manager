package com.tdm.tournament.bracket;

import com.tdm.tournament.model.Match;
import com.tdm.tournament.model.MatchStatus;
import com.tdm.tournament.model.Tournament;
import com.tdm.tournament.model.TournamentTeam;

import java.util.*;

/**
 * Generates and manages single-elimination bracket matches.
 *
 * Algorithm:
 * 1. Sort teams by seed (0 = unseeded, or 1..N for seeded).
 * 2. Calculate total slots = smallest power of 2 >= team count.
 * 3. First round: top teams get byes if needed; remaining teams are paired.
 * 4. Subsequent rounds: winners from previous round are paired.
 */
public class BracketGenerator {

    /**
     * Generate the full bracket for a single-elimination tournament.
     * Replaces any existing matches.
     */
    public static void generateBracket(Tournament tournament) {
        List<TournamentTeam> teams = new ArrayList<>(tournament.getTeams());

        // Remove existing matches before regenerating
        tournament.getMatches().clear();

        int numTeams = teams.size();
        if (numTeams < 2) return;

        // Sort by seed (lower seed first). Unseeded (0) go last.
        teams.sort(Comparator.comparingInt(TournamentTeam::getSeed));

        int totalSlots = nextPowerOf2(numTeams);
        int numByes = totalSlots - numTeams;
        int firstRoundMatches = totalSlots / 2;

        // Assign teams to bracket positions
        // Standard single-elimination placement: best vs worst
        List<UUID> bracketPositions = new ArrayList<>(totalSlots);
        for (int i = 0; i < totalSlots; i++) bracketPositions.add(null);

        int[] seededOrder = getSeededOrder(totalSlots);
        for (int i = 0; i < numTeams; i++) {
            bracketPositions.set(seededOrder[i], teams.get(i).getId());
        }

        // Generate first round matches
        int matchId = 0;
        List<UUID> roundWinners = new ArrayList<>();

        for (int i = 0; i < firstRoundMatches; i++) {
            UUID team1 = bracketPositions.get(i * 2);
            UUID team2 = bracketPositions.get(i * 2 + 1);

            Match match = new Match(matchId++, 0, team1, team2);

            // If it's a bye, auto-advance the playing team
            if (match.isBye()) {
                UUID advancingTeam = match.getByeTeam();
                match.setWinnerId(advancingTeam);
                match.setStatus(MatchStatus.FINISHED);
                roundWinners.add(advancingTeam);
            } else {
                match.setStatus(MatchStatus.PENDING);
                roundWinners.add(null); // placeholder
            }

            tournament.addMatch(match);
        }

        // Generate subsequent rounds
        int round = 1;
        while (roundWinners.size() > 1) {
            List<UUID> nextWinners = new ArrayList<>();

            for (int i = 0; i < roundWinners.size(); i += 2) {
                UUID team1 = roundWinners.get(i);
                UUID team2 = (i + 1 < roundWinners.size()) ? roundWinners.get(i + 1) : null;

                Match match = new Match(matchId++, round, team1, team2);

                // If both null (impossible but safe), or if it's a bye
                if (match.isBye()) {
                    UUID advancingTeam = match.getByeTeam();
                    match.setWinnerId(advancingTeam);
                    match.setStatus(MatchStatus.FINISHED);
                    nextWinners.add(advancingTeam);
                } else {
                    match.setStatus(MatchStatus.PENDING);
                    nextWinners.add(null); // placeholder
                }

                tournament.addMatch(match);
            }

            roundWinners = nextWinners;
            round++;
        }
    }

    /**
     * After a match finishes, update the bracket by propagating the winner
     * to the next round's match.
     *
     * @return the next Match the winner advances to, or null if tournament over
     */
    public static Match advanceWinner(Tournament tournament, int matchId) {
        Match finished = tournament.getMatch(matchId);
        if (finished == null || finished.getWinnerId() == null) return null;

        UUID winnerId = finished.getWinnerId();

        // Find the match in the next round that needs this winner
        int nextRound = finished.getRound() + 1;
        List<Match> nextRoundMatches = tournament.getRoundMatches(nextRound);

        int positionInRound = 0;
        List<Match> currentRound = tournament.getRoundMatches(finished.getRound());
        for (int i = 0; i < currentRound.size(); i++) {
            if (currentRound.get(i).getId() == matchId) {
                positionInRound = i;
                break;
            }
        }

        int targetIndex = positionInRound / 2;
        int teamSlot = positionInRound % 2; // 0 = team1, 1 = team2

        if (targetIndex < nextRoundMatches.size()) {
            Match nextMatch = nextRoundMatches.get(targetIndex);

            // Check if this slot is already filled (should not happen normally)
            if (teamSlot == 0 && nextMatch.getTeam1Id() == null) {
                return null; // slot already filled via different path? shouldn't happen
            }
            if (teamSlot == 1 && nextMatch.getTeam2Id() == null) {
                return null;
            }

            // Check if this is a bye in the next round (the other team is null)
            if (nextMatch.isBye()) {
                UUID advancingTeam = nextMatch.getByeTeam() != null
                        ? nextMatch.getByeTeam() : winnerId;
                nextMatch.setWinnerId(advancingTeam);
                nextMatch.setStatus(MatchStatus.FINISHED);
                return advanceWinner(tournament, nextMatch.getId());
            }

            return nextMatch;
        }

        // No next round — tournament is over
        tournament.setWinnerTeamId(winnerId);
        return null;
    }

    // --- Helpers ---

    /** Smallest power of 2 >= n. */
    private static int nextPowerOf2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    /**
     * Returns an array of indices that places teams in standard
     * single-elimination bracket order (1 vs last, 2 vs second-last, etc.)
     * for a bracket of the given slot size.
     */
    private static int[] getSeededOrder(int slots) {
        int[] order = new int[slots];
        // Fill with a standard tournament seeding order
        // 1, slots, 2, slots-1, 3, slots-2, ...
        int low = 0;
        int high = slots - 1;
        int seed = 1;
        boolean takeLow = true;
        while (low <= high) {
            if (takeLow) {
                order[low++] = seed++;
            } else {
                order[high--] = seed++;
            }
            takeLow = !takeLow;
        }
        // Convert from 1-indexed to 0-indexed
        for (int i = 0; i < slots; i++) {
            order[i] = order[i] - 1;
        }
        return order;
    }

    /**
     * Get a list of matches that are ready to be played in the current round.
     * These are PENDING matches in the earliest round where both teams are known.
     */
    public static List<Match> getReadyMatches(Tournament tournament) {
        List<Match> ready = new ArrayList<>();
        int maxRound = tournament.getMaxRound();

        for (int round = 0; round <= maxRound; round++) {
            List<Match> roundMatches = tournament.getRoundMatches(round);
            for (Match m : roundMatches) {
                if (m.getStatus() == MatchStatus.PENDING
                        && m.getTeam1Id() != null
                        && m.getTeam2Id() != null
                        && !m.isBye()) {
                    ready.add(m);
                }
            }
            if (!ready.isEmpty()) break; // only earliest round with ready matches
        }

        return ready;
    }

    /**
     * Check if the tournament bracket is complete (champion determined).
     */
    public static boolean isComplete(Tournament tournament) {
        return tournament.getWinnerTeamId() != null;
    }
}
