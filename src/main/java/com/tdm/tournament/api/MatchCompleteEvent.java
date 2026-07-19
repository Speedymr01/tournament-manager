package com.tdm.tournament.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fired by a {@link MinigameProvider} when a tournament match has completed.
 *
 * The Tournament plugin listens for this event to determine the winner
 * and advance brackets / update standings.
 */
public class MatchCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String providerName;
    private final String matchId;
    private final List<UUID> winningPlayers;
    private final String arena;
    private final boolean tie;

    /**
     * @param providerName  the provider's {@link MinigameProvider#getPluginName()}
     * @param matchId       the tournament-assigned match ID from {@link MinigameProvider#createMatch}
     * @param winningPlayers  players on the winning side (empty list if tie)
     * @param arena         the arena the match was played on
     * @param tie           true if the match ended in a tie
     */
    public MatchCompleteEvent(String providerName, String matchId,
                              List<UUID> winningPlayers, String arena, boolean tie) {
        this.providerName = providerName;
        this.matchId = matchId;
        this.winningPlayers = winningPlayers != null
                ? Collections.unmodifiableList(winningPlayers)
                : Collections.emptyList();
        this.arena = arena;
        this.tie = tie;
    }

    /** The name of the provider that ran the match. */
    public String getProviderName() { return providerName; }

    /** The tournament-assigned match ID. */
    public String getMatchId() { return matchId; }

    /** UUIDs of players on the winning side. Empty if tie. */
    public List<UUID> getWinningPlayers() { return winningPlayers; }

    /** Arena the match was played on. */
    public String getArena() { return arena; }

    /** Whether the match ended in a tie (no winner). */
    public boolean isTie() { return tie; }

    // ---- HandlerList boilerplate ----

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
