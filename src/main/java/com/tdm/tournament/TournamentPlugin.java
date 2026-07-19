package com.tdm.tournament;

import com.tdm.tournament.command.TournamentCommand;
import com.tdm.tournament.gui.AdminGUI;
import com.tdm.tournament.gui.PlayerGUI;
import com.tdm.tournament.listener.ConnectionListener;
import com.tdm.tournament.listener.GameEndListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * TournamentManager — orchestrates tournaments using TDMAPI and SpleefAPI
 * via Bukkit's ServicesManager.
 *
 * Soft dependencies: TeamDeathmatch, Spleef
 */
public class TournamentPlugin extends JavaPlugin implements Listener {

    private TournamentManager tournamentManager;
    private AdminGUI adminGUI;
    private PlayerGUI playerGUI;

    // GUI click handlers: player UUID -> handler (returns true if handled)
    private final Map<UUID, BiFunction<Player, Integer, Boolean>> guiHandlers = new HashMap<>();
    // Chat input handlers: player UUID -> handler
    private final Map<UUID, Consumer<String>> chatInputHandlers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.tournamentManager = new TournamentManager(this);
        this.adminGUI = new AdminGUI(this, tournamentManager);
        this.playerGUI = new PlayerGUI(this, tournamentManager);

        // Register command
        TournamentCommand command = new TournamentCommand(this, tournamentManager, adminGUI, playerGUI);
        var cmd = getCommand("tournament");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this); // for GUI/chat handlers
        getServer().getPluginManager().registerEvents(new GameEndListener(this, tournamentManager), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this, tournamentManager), this);

        getLogger().info("TournamentManager v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll((Listener) this);
        guiHandlers.clear();
        chatInputHandlers.clear();
        getLogger().info("TournamentManager disabled!");
    }

    // ======================== GUI Handler System ========================

    /**
     * Set the GUI click handler for a player.
     * The handler receives (player, clickedSlot) and returns true if handled.
     */
    public void setGuiHandler(UUID playerId, BiFunction<Player, Integer, Boolean> handler) {
        guiHandlers.put(playerId, handler);
    }

    /**
     * Set a chat input handler for a player (for name prompts, etc).
     */
    public void setChatInputHandler(UUID playerId, Consumer<String> handler) {
        chatInputHandlers.put(playerId, handler);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        BiFunction<Player, Integer, Boolean> handler = guiHandlers.get(player.getUniqueId());
        if (handler == null) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0) return;

        handler.apply(player, slot);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Don't clear the handler immediately — the handler might re-open a new GUI
        // Only clear if the player closed the inventory (not switching to another GUI)
        // We use a short delay to allow re-opening
        Player player = (Player) event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.getOpenInventory().getTopInventory().equals(event.getInventory())) {
                // Player didn't open a new inventory, so clear handler
                guiHandlers.remove(player.getUniqueId());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> handler = chatInputHandlers.get(player.getUniqueId());
        if (handler == null) return;

        event.setCancelled(true);
        String message = event.getMessage();

        // Run on main thread
        getServer().getScheduler().runTask(this, () -> {
            handler.accept(message);
            chatInputHandlers.remove(player.getUniqueId());
        });
    }
}
