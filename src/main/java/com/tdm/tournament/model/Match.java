package com.tdm.tournament.model;

import java.util.UUID;

/**
 * A single match in a tournament, between two teams.
 * A null team indicates a "bye" (team advances without playing).
 */
public class Match {

    private final int id;                  // unique within the tournament
    private final int round;               // 0 = first round, 1 = second, etc.
    private final UUID team1Id;            // null = bye placeholder
    private final UUID team2Id;            // null = bye placeholder
    private UUID winnerId;                 // null if not yet decided
    private MatchStatus status;
    private String arenaName;              // which arena was used (optional, for display)

    public Match(int id, int round, UUID team1Id, UUID team2Id) {
        this.id = id;
        this.round = round;
        this.team1Id = team1Id;
        this.team2Id = team2Id;
        this.status = MatchStatus.PENDING;
        this.winnerId = null;
        this.arenaName = null;
    }

    public Match(int id, int round, UUID team1Id, UUID team2Id,
                 UUID winnerId, MatchStatus status, String arenaName) {
        this.id = id;
        this.round = round;
        this.team1Id = team1Id;
        this.team2Id = team2Id;
        this.winnerId = winnerId;
        this.status = status;
        this.arenaName = arenaName;
    }

    public int getId() { return id; }
    public int getRound() { return round; }
    public UUID getTeam1Id() { return team1Id; }
    public UUID getTeam2Id() { return team2Id; }

    /** Returns true if this is a bye (one team is null). */
    public boolean isBye() {
        return team1Id == null || team2Id == null;
    }

    /** Returns the non-null team in a bye match, or null if both are set. */
    public UUID getByeTeam() {
        if (team1Id == null) return team2Id;
        if (team2Id == null) return team1Id;
        return null;
    }

    public UUID getWinnerId() { return winnerId; }
    public void setWinnerId(UUID winnerId) { this.winnerId = winnerId; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public String getArenaName() { return arenaName; }
    public void setArenaName(String arenaName) { this.arenaName = arenaName; }
}
