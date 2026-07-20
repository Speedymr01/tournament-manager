package com.tdm.tournament;

import com.tdm.tournament.api.MinigameProvider;
import com.tdm.tournament.command.TournamentCommand;
import com.tdm.tournament.gui.AdminGUI;
import com.tdm.tournament.gui.PlayerGUI;
import com.tdm.tournament.listener.ConnectionListener;
import com.tdm.tournament.listener.MatchEndListener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * TournamentManager — orchestrates tournaments using any minigame plugin
 * that implements {@link MinigameProvider}.
 *
 * Providers are discovered at runtime via Bukkit's ServicesManager.
 * No hard dependency on any specific minigame plugin.
 */
public class TournamentPlugin extends JavaPlugin implements Listener {

    private TournamentManager tournamentManager;
    private AdminGUI adminGUI;
    private PlayerGUI playerGUI;
    private final List<MinigameProvider> providers = new ArrayList<>();

    // GUI click handlers: player UUID -> handler (returns true if handled)
    private final Map<UUID, BiFunction<Player, Integer, Boolean>> guiHandlers = new HashMap<>();
    // Chat input handlers: player UUID -> handler
    private final Map<UUID, Consumer<String>> chatInputHandlers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Discover minigame providers via ServicesManager
        discoverProviders();

        this.tournamentManager = new TournamentManager(this, providers);
        this.adminGUI = new AdminGUI(this, tournamentManager, providers);
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
        getServer().getPluginManager().registerEvents(new MatchEndListener(this, tournamentManager), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this, tournamentManager), this);

        logProviders();
        getLogger().info("TournamentManager v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll((Listener) this);
        guiHandlers.clear();
        chatInputHandlers.clear();
        providers.clear();
        getLogger().info("TournamentManager disabled!");
    }

    // ======================== Provider Discovery ========================

    private void discoverProviders() {
        providers.clear();
        var registrations = Bukkit.getServicesManager().getRegistrations(MinigameProvider.class);
        for (var reg : registrations) {
            MinigameProvider provider = reg.getProvider();
            if (provider != null && provider.isEnabled()) {
                providers.add(provider);
                getLogger().info("Discovered minigame provider: " + provider.getPluginName());
            }
        }
    }

    /** Re-scan for providers (e.g., after a plugin loads). */
    public void refreshProviders() {
        discoverProviders();
        tournamentManager.refreshProviders(providers);
    }

    public List<MinigameProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    public Optional<MinigameProvider> getProvider(String name) {
        for (MinigameProvider p : providers) {
            if (p.getPluginName().equalsIgnoreCase(name)
                    || p.getDisplayName().equalsIgnoreCase(name)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private void logProviders() {
        if (providers.isEmpty()) {
            getLogger().warning("No MinigameProvider plugins found! Tournament matches cannot be played.");
            getLogger().warning("Install Spleef, TeamDeathmatch, or any other compatible plugin.");
        } else {
            getLogger().info("Found " + providers.size() + " minigame provider(s):");
            for (MinigameProvider p : providers) {
                getLogger().info("  - " + p.getDisplayName() + " (" + p.getPluginName() + ")");
            }
        }
    }

    // ======================== GUI Handler System ========================

    public void setGuiHandler(UUID playerId, BiFunction<Player, Integer, Boolean> handler) {
        guiHandlers.put(playerId, handler);
    }

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
        Player player = (Player) event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.getOpenInventory().getTopInventory().equals(event.getInventory())) {
                guiHandlers.remove(player.getUniqueId());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> handler = chatInputHandlers.get(player.getUniqueId());
        if (handler == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        getServer().getScheduler().runTask(this, () -> {
            handler.accept(message);
            chatInputHandlers.remove(player.getUniqueId());
        });
    }
}
