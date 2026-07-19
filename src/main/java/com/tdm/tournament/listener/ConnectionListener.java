package com.tdm.tournament.listener;

import com.tdm.tournament.TournamentManager;
import com.tdm.tournament.TournamentPlugin;
import com.tdm.tournament.model.Match;
import com.tdm.tournament.model.MatchStatus;
import com.tdm.tournament.model.Tournament;
import com.tdm.tournament.model.TournamentState;
import com.tdm.tournament.model.TournamentTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles player disconnects during tournaments.
 * If a player disconnects during an active match, the match is forfeited.
 */
public class ConnectionListener implements Listener {

    private final TournamentPlugin plugin;
    private final TournamentManager manager;

    public ConnectionListener(TournamentPlugin plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        var playerId = player.getUniqueId();

        // Check if this player is in any active tournament match
        for (Tournament t : manager.getAllTournaments()) {
            if (t.getState() != TournamentState.ACTIVE) continue;

            var team = t.getTeamByPlayer(playerId);
            if (team == null) continue;

            Match activeMatch = manager.getActiveMatchForTeam(t.getId(), team.getId());
            if (activeMatch == null || activeMatch.getStatus() != MatchStatus.IN_PROGRESS) continue;

            // Player disconnected during a match — forfeit their team
            UUID otherTeamId;
            if (team.getId().equals(activeMatch.getTeam1Id())) {
                otherTeamId = activeMatch.getTeam2Id();
            } else {
                otherTeamId = activeMatch.getTeam1Id();
            }

            if (otherTeamId != null) {
                // Get winning players from the other team
                TournamentTeam otherTeam = t.getTeam(otherTeamId);
                List<UUID> winningPlayers = (otherTeam != null)
                        ? new ArrayList<>(otherTeam.getMembers())
                        : List.of();
                String matchIdStr = t.getId() + ":" + activeMatch.getId();
                manager.completeMatch(matchIdStr, winningPlayers, false);
                plugin.getLogger().warning("Match " + activeMatch.getId()
                        + " in tournament " + t.getName()
                        + " forfeited: " + player.getName() + " disconnected");
            }
        }
    }
}
