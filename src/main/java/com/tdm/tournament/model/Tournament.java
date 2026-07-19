package com.tdm.tournament.model;

import java.util.*;

/**
 * Represents a tournament with its configuration, teams, and matches.
 */
public class Tournament {

    private final UUID id;
    private String name;
    private TournamentFormat format;
    private String providerName;     // name of the MinigameProvider
    private TournamentState state;
    private int maxTeams;
    private int teamSize;        // 1 = solo, 2 = duos, etc.
    private int swissRounds;     // rounds for Swiss format
    private final List<TournamentTeam> teams;
    private final List<Match> matches;
    private final long createdTime;
    private long startedTime;
    private long endedTime;
    private UUID winnerTeamId;   // the champion team

    public Tournament(String name, TournamentFormat format, String providerName,
                      int maxTeams, int teamSize) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.format = format;
        this.providerName = providerName;
        this.state = TournamentState.OPEN;
        this.maxTeams = maxTeams;
        this.teamSize = teamSize;
        this.swissRounds = 0;
        this.teams = new ArrayList<>();
        this.matches = new ArrayList<>();
        this.createdTime = System.currentTimeMillis();
        this.startedTime = 0;
        this.endedTime = 0;
        this.winnerTeamId = null;
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public TournamentFormat getFormat() { return format; }
    public void setFormat(TournamentFormat format) { this.format = format; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public TournamentState getState() { return state; }
    public void setState(TournamentState state) { this.state = state; }

    public int getMaxTeams() { return maxTeams; }
    public void setMaxTeams(int maxTeams) { this.maxTeams = maxTeams; }

    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = teamSize; }

    public int getSwissRounds() { return swissRounds; }
    public void setSwissRounds(int swissRounds) { this.swissRounds = swissRounds; }

    public List<TournamentTeam> getTeams() { return Collections.unmodifiableList(teams); }
    public List<Match> getMatches() { return Collections.unmodifiableList(matches); }

    public long getCreatedTime() { return createdTime; }
    public long getStartedTime() { return startedTime; }
    public void setStartedTime(long startedTime) { this.startedTime = startedTime; }
    public long getEndedTime() { return endedTime; }
    public void setEndedTime(long endedTime) { this.endedTime = endedTime; }

    public UUID getWinnerTeamId() { return winnerTeamId; }
    public void setWinnerTeamId(UUID winnerTeamId) { this.winnerTeamId = winnerTeamId; }

    // --- Team management ---

    public boolean addTeam(TournamentTeam team) {
        if (teams.size() >= maxTeams) return false;
        if (state != TournamentState.OPEN) return false;
        teams.add(team);
        return true;
    }

    public boolean removeTeam(UUID teamId) {
        if (state != TournamentState.OPEN) return false;
        return teams.removeIf(t -> t.getId().equals(teamId));
    }

    public TournamentTeam getTeam(UUID teamId) {
        for (TournamentTeam t : teams) {
            if (t.getId().equals(teamId)) return t;
        }
        return null;
    }

    public TournamentTeam getTeamByPlayer(UUID playerId) {
        for (TournamentTeam t : teams) {
            if (t.containsPlayer(playerId)) return t;
        }
        return null;
    }

    public boolean isPlayerInTournament(UUID playerId) {
        return getTeamByPlayer(playerId) != null;
    }

    public int getTeamCount() { return teams.size(); }

    // --- Match management ---

    public void addMatch(Match match) {
        matches.add(match);
    }

    public Match getMatch(int matchId) {
        for (Match m : matches) {
            if (m.getId() == matchId) return m;
        }
        return null;
    }

    public List<Match> getRoundMatches(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : matches) {
            if (m.getRound() == round) result.add(m);
        }
        return result;
    }

    public int getMaxRound() {
        int max = -1;
        for (Match m : matches) {
            if (m.getRound() > max) max = m.getRound();
        }
        return max;
    }

    public int nextMatchId() {
        return matches.size();
    }

    // --- State checks ---

    public boolean isFull() {
        return teams.size() >= maxTeams;
    }

    public boolean canStart() {
        return state == TournamentState.OPEN && teams.size() >= 2;
    }
}
