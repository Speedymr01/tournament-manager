package com.tdm.tournament.model;

/**
 * States in a tournament's lifecycle.
 */
public enum TournamentState {
    /** Admin created it; players can join. */
    OPEN,
    /** Admin started it; matches are being played. */
    ACTIVE,
    /** All matches complete; a champion has been crowned. */
    FINISHED,
    /** Admin cancelled the tournament early. */
    CANCELLED
}
