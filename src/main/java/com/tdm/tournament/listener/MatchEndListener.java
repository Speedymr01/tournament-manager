package com.tdm.tournament.listener;

import com.tdm.tournament.TournamentManager;
import com.tdm.tournament.TournamentPlugin;
import com.tdm.tournament.api.MatchCompleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for {@link MatchCompleteEvent} fired by any {@link com.tdm.tournament.api.MinigameProvider}
 * and routes results back to the TournamentManager.
 *
 * This is the sole bridge between minigame plugins and tournament logic —
 * no hardcoded references to Spleef or TDM.
 */
public class MatchEndListener implements Listener {

    private final TournamentPlugin plugin;
    private final TournamentManager manager;

    public MatchEndListener(TournamentPlugin plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMatchComplete(MatchCompleteEvent event) {
        plugin.getLogger().info("Match completed: provider=" + event.getProviderName()
                + " matchId=" + event.getMatchId()
                + " arena=" + event.getArena()
                + " tie=" + event.isTie()
                + " winners=" + event.getWinningPlayers().size());

        manager.completeMatch(
                event.getMatchId(),
                event.getWinningPlayers(),
                event.isTie()
        );
    }
}
