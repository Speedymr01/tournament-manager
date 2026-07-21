package com.tdm.tournament.api;

import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * Interface for minigame plugins (Spleef, TDM, etc.) that want to
 * participate in tournament matches.
 *
 * Implementations should register themselves via Bukkit's ServicesManager
 * during their plugin's onEnable():
 * <pre>
 *   getServer().getServicesManager().register(
 *       MinigameProvider.class, myProvider, this, ServicePriority.Normal
 *   );
 * </pre>
 *
 * The Tournament plugin discovers all registered providers at runtime
 * and displays them in the admin GUI.
 */
public interface MinigameProvider {

    /**
     * Internal name, e.g. "Spleef" or "TeamDeathmatch".
     * Should match the plugin name.
     */
    String getPluginName();

    /** Human-readable label shown in GUIs, e.g. "Spleef" or "TDM". */
    String getDisplayName();

    /** Icon shown in the admin GUI for this provider. */
    Material getIcon();

    /** Whether the backing plugin is currently loaded and usable. */
    boolean isEnabled();

    /**
     * List of arena names available for matches.
     * May be empty if the provider doesn't support arena listing.
     */
    List<String> getAvailableArenas();

    /**
     * Create and start a match on the given arena.
     *
     * @param arena   arena identifier (from {@link #getAvailableArenas()})
     * @param team1   UUIDs of players on the first team
     * @param team2   UUIDs of players on the second team
     * @param matchId tournament-assigned match ID for correlation
     * @return true if the match was successfully created
     */
    boolean createMatch(String arena, List<UUID> team1, List<UUID> team2, String matchId);

    /**
     * Forcefully cancel a running match.
     *
     * @param matchId the tournament-assigned match ID passed to {@link #createMatch}
     */
    void cancelMatch(String matchId);

    /**
     * Open a configuration menu for this minigame.
     * Override this to allow admins to edit config values in-game.
     * Default implementation sends a message that no config is available.
     */
    default void openConfigMenu(org.bukkit.entity.Player player) {
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "No in-game config available for " + getDisplayName(),
                net.kyori.adventure.text.format.NamedTextColor.RED));
    }
}
