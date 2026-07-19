package com.tdm.tournament.model;

/**
 * Status of a single tournament match.
 */
public enum MatchStatus {
    /** Not yet played. */
    PENDING,
    /** Delegated to TDM/Spleef and currently being played. */
    IN_PROGRESS,
    /** Finished; a winner has been determined. */
    FINISHED
}
