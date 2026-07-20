package com.tdm.tournament.gui;

import com.tdm.tournament.TournamentManager;
import com.tdm.tournament.TournamentPlugin;
import com.tdm.tournament.api.MinigameProvider;
import com.tdm.tournament.bracket.BracketGenerator;
import com.tdm.tournament.model.*;
import com.tdm.tournament.swiss.SwissSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Admin inventory GUIs — fully provider-agnostic.
 * Minigame providers are discovered at runtime from registered
 * {@link MinigameProvider} instances.
 */
public class AdminGUI {

    private final TournamentPlugin plugin;
    private final TournamentManager manager;
    private final List<MinigameProvider> providers;

    private final Map<UUID, CreateContext> createContexts = new HashMap<>();

    private static final Component TITLE_MAIN = Component.text("Tournament Admin", NamedTextColor.DARK_AQUA);
    private static final Component TITLE_CREATE = Component.text("Create Tournament", NamedTextColor.DARK_AQUA);
    private static final Component TITLE_MANAGE_LIST = Component.text("Manage Tournaments", NamedTextColor.DARK_AQUA);

    public AdminGUI(TournamentPlugin plugin, TournamentManager manager, List<MinigameProvider> providers) {
        this.plugin = plugin;
        this.manager = manager;
        this.providers = providers;
    }

    // ======================== MAIN MENU ========================

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE_MAIN);

        inv.setItem(2, makeItem(Material.EMERALD_BLOCK,
                Component.text("Create Tournament", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Set up a new tournament", NamedTextColor.GRAY)));

        inv.setItem(4, makeItem(Material.COMPASS,
                Component.text("Manage Tournaments", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("View and control existing tournaments", NamedTextColor.GRAY)));

        // Installed minigames button
        inv.setItem(6, makeInstalledMinigamesItem());

        inv.setItem(8, makeItem(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> handleMainMenuClick(p, s));
    }

    private boolean handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 2 -> openCreateMenu(player);
            case 4 -> openManageTournamentsList(player);
            case 6 -> openInstalledMinigames(player);
            case 8 -> player.closeInventory();
            default -> { return false; }
        }
        return true;
    }

    // ======================== INSTALLED MINIGAMES ========================

    private ItemStack makeInstalledMinigamesItem() {
        List<Component> lore = new ArrayList<>();
        if (providers.isEmpty()) {
            lore.add(Component.text("No providers found!", NamedTextColor.RED));
            lore.add(Component.text("Install Spleef, TDM, or similar", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text(providers.size() + " provider(s) available", NamedTextColor.GREEN));
            for (MinigameProvider p : providers) {
                String arenas = p.getAvailableArenas().size() + " arena(s)";
                lore.add(Component.text("• " + p.getDisplayName(), NamedTextColor.WHITE)
                        .append(Component.text(" (" + arenas + ")", NamedTextColor.GRAY)));
            }
        }
        return makeItem(Material.COMMAND_BLOCK,
                Component.text("Installed Minigames", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                lore.toArray(new Component[0]));
    }

    private void openInstalledMinigames(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Installed Minigames", NamedTextColor.DARK_AQUA));

        if (providers.isEmpty()) {
            inv.setItem(13, makeItem(Material.BARRIER,
                    Component.text("No minigame providers found!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Install Spleef or TeamDeathmatch to use tournaments", NamedTextColor.GRAY)));
        } else {
            int slot = 10;
            for (MinigameProvider p : providers) {
                List<String> arenas = p.getAvailableArenas();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Plugin: " + p.getPluginName(), NamedTextColor.GRAY));
                lore.add(Component.text("Arenas: " + (arenas.isEmpty() ? "(auto)" : String.join(", ", arenas)), NamedTextColor.GRAY));
                lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                        .append(Component.text("Available", NamedTextColor.GREEN)));

                inv.setItem(slot++, makeItem(p.getIcon(),
                        Component.text(p.getDisplayName(), NamedTextColor.WHITE, TextDecoration.BOLD),
                        lore.toArray(new Component[0])));
            }
        }

        inv.setItem(26, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 26) { openMainMenu(player); return true; }
            return false;
        });
    }

    // ======================== CREATE TOURNAMENT ========================

    public void openCreateMenu(Player player) {
        if (providers.isEmpty()) {
            player.sendMessage(Component.text("No minigame providers available! "
                    + "Install Spleef, TeamDeathmatch, or a compatible plugin.", NamedTextColor.RED));
            return;
        }
        createContexts.put(player.getUniqueId(), new CreateContext(providers));
        renderCreateMenu(player);
    }

    private void renderCreateMenu(Player player) {
        CreateContext ctx = createContexts.get(player.getUniqueId());
        if (ctx == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CREATE);
        MinigameProvider currentProvider = ctx.providers.get(ctx.providerIndex);
        String arenaInfo = currentProvider.getAvailableArenas().isEmpty()
                ? "Auto" : currentProvider.getAvailableArenas().get(0);

        // Fill borders first, then overlay content slots
        fillBorders(inv);
        inv.setItem(10, makeItem(
                ctx.format == TournamentFormat.SINGLE_ELIMINATION ? Material.IRON_SWORD : Material.STONE_SWORD,
                Component.text("Format: " + formatName(ctx.format), NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Click to toggle: Single Elimination / Swiss", NamedTextColor.GRAY)));
        inv.setItem(11, makeItem(currentProvider.getIcon(),
                Component.text("Game: " + currentProvider.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Click to cycle through installed minigames", NamedTextColor.GRAY)));
        inv.setItem(12, makeItem(Material.PLAYER_HEAD,
                Component.text("Team Size: " + ctx.teamSize, NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Click to change", NamedTextColor.GRAY)));
        inv.setItem(13, makeItem(Material.OAK_SIGN,
                Component.text("Max Teams: " + ctx.maxTeams, NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Click to change", NamedTextColor.GRAY)));
        inv.setItem(14, makeItem(Material.MAP,
                Component.text("Arena: " + arenaInfo, NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Uses first available arena from provider", NamedTextColor.GRAY)));
        inv.setItem(22, makeItem(Material.NAME_TAG,
                Component.text("Name: " + (ctx.name.isEmpty() ? "(not set)" : ctx.name), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("Type name in chat after opening", NamedTextColor.GRAY)));
        inv.setItem(26, makeItem(Material.LIME_DYE,
                Component.text("Create Tournament", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Name: " + (ctx.name.isEmpty() ? "NOT SET" : ctx.name), NamedTextColor.WHITE),
                Component.text("Format: " + formatName(ctx.format), NamedTextColor.GRAY),
                Component.text("Game: " + currentProvider.getDisplayName(), NamedTextColor.GRAY),
                Component.text("Team Size: " + ctx.teamSize + " | Max Teams: " + ctx.maxTeams, NamedTextColor.GRAY)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> handleCreateMenuClick(p, s));
    }

    private boolean handleCreateMenuClick(Player player, int slot) {
        CreateContext ctx = createContexts.get(player.getUniqueId());
        if (ctx == null) return false;

        switch (slot) {
            case 10 -> {
                ctx.format = (ctx.format == TournamentFormat.SINGLE_ELIMINATION)
                        ? TournamentFormat.SWISS
                        : TournamentFormat.SINGLE_ELIMINATION;
                renderCreateMenu(player);
            }
            case 11 -> {
                ctx.providerIndex = (ctx.providerIndex + 1) % ctx.providers.size();
                renderCreateMenu(player);
            }
            case 12 -> {
                ctx.teamSize = (ctx.teamSize % 4) + 1;
                renderCreateMenu(player);
            }
            case 13 -> {
                int[] options = {2, 4, 8, 16, 32, 64};
                int idx = 0;
                for (int i = 0; i < options.length; i++) {
                    if (options[i] == ctx.maxTeams) { idx = (i + 1) % options.length; break; }
                }
                ctx.maxTeams = options[idx];
                renderCreateMenu(player);
            }
            case 22 -> {
                player.closeInventory();
                player.sendMessage(Component.text("Type the tournament name in chat:", NamedTextColor.AQUA));
                plugin.setChatInputHandler(player.getUniqueId(), input -> {
                    ctx.name = input;
                    renderCreateMenu(player);
                });
            }
            case 26 -> {
                if (ctx.name.isEmpty()) {
                    player.sendMessage(Component.text("Please set a tournament name first!", NamedTextColor.RED));
                    return true;
                }
                MinigameProvider prov = ctx.providers.get(ctx.providerIndex);
                Tournament t = manager.createTournament(
                        ctx.name, ctx.format, prov.getPluginName(), ctx.maxTeams, ctx.teamSize);
                player.sendMessage(Component.text("Tournament '" + ctx.name + "' created! ID: "
                        + t.getId().toString().substring(0, 8), NamedTextColor.GREEN));
                createContexts.remove(player.getUniqueId());
                player.closeInventory();
            }
            default -> { return false; }
        }
        return true;
    }

    // ======================== MANAGE TOURNAMENTS LIST ========================

    public void openManageTournamentsList(Player player) {
        List<Tournament> all = new ArrayList<>(manager.getAllTournaments());
        openPaginatedList(player, all, 0);
    }

    private void openPaginatedList(Player player, List<Tournament> tournaments, int page) {
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) tournaments.size() / pageSize));
        int start = page * pageSize;
        int end = Math.min(start + pageSize, tournaments.size());
        List<Tournament> pageItems = tournaments.subList(start, end);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Manage Tournaments", NamedTextColor.DARK_AQUA));

        int slot = 0;
        for (Tournament t : pageItems) {
            inv.setItem(slot++, makeTournamentItem(t));
        }

        if (page > 0) {
            inv.setItem(45, makeItem(Material.ARROW,
                    Component.text("Previous Page", NamedTextColor.YELLOW)));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, makeItem(Material.ARROW,
                    Component.text("Next Page", NamedTextColor.YELLOW)));
        }
        inv.setItem(49, makeItem(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED)));

        int finalPage = page;
        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) ->
                handlePaginatedListClick(p, s, tournaments, finalPage));
    }

    private boolean handlePaginatedListClick(Player player, int slot,
                                              List<Tournament> tournaments, int page) {
        int pageSize = 45;
        if (slot == 45 && page > 0) {
            openPaginatedList(player, tournaments, page - 1);
            return true;
        }
        if (slot == 53 && (page + 1) * pageSize < tournaments.size()) {
            openPaginatedList(player, tournaments, page + 1);
            return true;
        }
        if (slot == 49) { player.closeInventory(); return true; }

        if (slot >= 0 && slot < pageSize) {
            int index = page * pageSize + slot;
            if (index < tournaments.size()) {
                openManageTournament(player, tournaments.get(index));
                return true;
            }
        }
        return false;
    }

    // ======================== MANAGE SINGLE TOURNAMENT ========================

    private void openManageTournament(Player player, Tournament t) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Manage: " + t.getName(), NamedTextColor.DARK_AQUA));

        inv.setItem(4, makeTournamentInfoItem(t));

        int slot = 10;
        if (t.getState() == TournamentState.OPEN) {
            inv.setItem(slot++, makeItem(Material.LIME_DYE,
                    Component.text("Start Tournament", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Begin the tournament", NamedTextColor.GRAY)));
        }
        if (t.getState() == TournamentState.ACTIVE) {
            inv.setItem(slot++, makeItem(Material.REDSTONE_BLOCK,
                    Component.text("Force End", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("End the tournament immediately", NamedTextColor.GRAY)));
        }
        if (t.getState() != TournamentState.FINISHED && t.getState() != TournamentState.CANCELLED) {
            inv.setItem(slot++, makeItem(Material.BARRIER,
                    Component.text("Cancel Tournament", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Component.text("Cancel the tournament", NamedTextColor.GRAY)));
        }

        inv.setItem(slot++, makeItem(Material.FILLED_MAP,
                Component.text("View Bracket / Standings", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("See current bracket or standings", NamedTextColor.GRAY)));

        inv.setItem(slot++, makeItem(Material.PLAYER_HEAD,
                Component.text("View Teams (" + t.getTeamCount() + ")", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("List all registered teams", NamedTextColor.GRAY)));

        if (t.getState() == TournamentState.ACTIVE) {
            List<Match> ready = manager.getReadyMatches(t.getId());
            List<Match> active = manager.getActiveMatches(t.getId());
            inv.setItem(22, makeItem(Material.CLOCK,
                    Component.text("Ready: " + ready.size() + " | Active: " + active.size(), NamedTextColor.WHITE),
                    Component.text("Matches ready to start / in progress", NamedTextColor.GRAY)));
        }

        inv.setItem(26, makeItem(Material.ARROW,
                Component.text("Back to List", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> handleManageClick(p, s, t));
    }

    private boolean handleManageClick(Player player, int slot, Tournament t) {
        if (slot == 26) { openManageTournamentsList(player); return true; }

        int idx = 10;
        if (t.getState() == TournamentState.OPEN) {
            if (slot == idx) { promptStartTournament(player, t); return true; }
            idx++;
        }
        if (t.getState() == TournamentState.ACTIVE) {
            if (slot == idx) { promptForceEndTournament(player, t); return true; }
            idx++;
        }
        if (t.getState() != TournamentState.FINISHED && t.getState() != TournamentState.CANCELLED) {
            if (slot == idx) { promptCancelTournament(player, t); return true; }
            idx++;
        }
        if (slot == idx) { openBracketOrStandings(player, t); return true; }
        if (slot == idx + 1) { openTeamList(player, t); return true; }
        return false;
    }

    // ======================== ACTIONS ========================

    public void promptStartTournament(Player player, String tournamentIdStr) {
        Tournament t = findTournamentByIdPrefix(tournamentIdStr);
        if (t == null) {
            player.sendMessage(Component.text("Tournament not found!", NamedTextColor.RED));
            return;
        }
        promptStartTournament(player, t);
    }

    private void promptStartTournament(Player player, Tournament t) {
        if (!t.canStart()) {
            player.sendMessage(Component.text("Cannot start '" + t.getName()
                    + "': need at least 2 teams.", NamedTextColor.RED));
            return;
        }

        boolean success = manager.startTournament(t.getId());
        if (success) {
            player.sendMessage(Component.text("Tournament '" + t.getName() + "' started!", NamedTextColor.GREEN));
            player.closeInventory();
        } else {
            player.sendMessage(Component.text("Failed to start tournament. Check provider availability.", NamedTextColor.RED));
        }
    }

    public void promptCancelTournament(Player player, String tournamentIdStr) {
        Tournament t = findTournamentByIdPrefix(tournamentIdStr);
        if (t == null) {
            player.sendMessage(Component.text("Tournament not found!", NamedTextColor.RED));
            return;
        }
        promptCancelTournament(player, t);
    }

    private void promptCancelTournament(Player player, Tournament t) {
        if (manager.cancelTournament(t.getId())) {
            player.sendMessage(Component.text("Tournament '" + t.getName() + "' cancelled.", NamedTextColor.YELLOW));
            player.closeInventory();
        } else {
            player.sendMessage(Component.text("Cannot cancel a finished/cancelled tournament.", NamedTextColor.RED));
        }
    }

    private void promptForceEndTournament(Player player, Tournament t) {
        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            if (t.getWinnerTeamId() != null) {
                manager.finishTournament(t.getId());
                player.sendMessage(Component.text("Tournament ended. Champion determined.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("No champion yet. Cancel instead.", NamedTextColor.RED));
            }
        } else {
            UUID winner = SwissSystem.getWinner(t);
            if (winner != null) {
                t.setWinnerTeamId(winner);
                manager.finishTournament(t.getId());
                player.sendMessage(Component.text("Tournament ended based on current standings.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Not enough data to determine winner. Cancel instead.", NamedTextColor.RED));
            }
        }
        player.closeInventory();
    }

    // ======================== BRACKET / STANDINGS VIEW ========================

    private void openBracketOrStandings(Player player, Tournament t) {
        if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
            openBracketView(player, t);
        } else {
            openSwissStandings(player, t);
        }
    }

    private void openBracketView(Player player, Tournament t) {
        int maxRound = t.getMaxRound();
        if (maxRound < 0) {
            player.sendMessage(Component.text("No bracket generated yet.", NamedTextColor.RED));
            return;
        }

        int cols = Math.min(maxRound + 1, 9);
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Bracket: " + t.getName(), NamedTextColor.DARK_AQUA));

        for (int round = 0; round < cols; round++) {
            String roundName = round == maxRound ? "Finals"
                    : round == maxRound - 1 ? "Semis"
                    : round == maxRound - 2 ? "Quarters"
                    : "R" + (round + 1);
            inv.setItem(round, makeItem(Material.PAPER,
                    Component.text(roundName, NamedTextColor.WHITE, TextDecoration.BOLD)));
        }

        for (int round = 0; round < cols; round++) {
            List<Match> roundMatches = t.getRoundMatches(round);
            for (int i = 0; i < roundMatches.size() && i < 5; i++) {
                Match m = roundMatches.get(i);
                int slot = 9 + (round * 5) + i;
                if (slot >= 54) break;
                inv.setItem(slot, makeMatchItem(t, m));
            }
        }

        inv.setItem(53, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 53) { openManageTournament(player, t); return true; }
            return false;
        });
    }

    private void openSwissStandings(Player player, Tournament t) {
        List<SwissSystem.SwissStanding> standings = SwissSystem.getStandings(t);
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Standings: " + t.getName(), NamedTextColor.DARK_AQUA));

        for (int i = 0; i < Math.min(standings.size(), 45); i++) {
            SwissSystem.SwissStanding s = standings.get(i);
            TournamentTeam team = t.getTeam(s.teamId);
            String teamName = team != null ? team.getName() : "Unknown";
            String medal = i == 0 ? "1st" : i == 1 ? "2nd" : i == 2 ? "3rd" : (i + 1) + "th";
            NamedTextColor color = i == 0 ? NamedTextColor.GOLD
                    : i == 1 ? NamedTextColor.GRAY
                    : i == 2 ? NamedTextColor.RED : NamedTextColor.WHITE;

            inv.setItem(i + 9, makeItem(
                    i == 0 ? Material.GOLD_INGOT : i == 1 ? Material.IRON_INGOT : i == 2 ? Material.COPPER_INGOT : Material.PAPER,
                    Component.text(medal + ". " + teamName, color, TextDecoration.BOLD),
                    Component.text("Wins: " + s.wins + " / Matches: " + s.matchesPlayed, NamedTextColor.GRAY)));
        }

        inv.setItem(53, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 53) { openManageTournament(player, t); return true; }
            return false;
        });
    }

    // ======================== TEAM LIST ========================

    private void openTeamList(Player player, Tournament t) {
        List<TournamentTeam> teams = t.getTeams();
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Teams: " + t.getName(), NamedTextColor.DARK_AQUA));

        for (int i = 0; i < Math.min(teams.size(), 45); i++) {
            TournamentTeam team = teams.get(i);
            List<String> memberNames = team.getMemberNames();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Members:", NamedTextColor.GRAY));
            for (String name : memberNames) {
                lore.add(Component.text(" - " + name, NamedTextColor.GRAY));
            }
            inv.setItem(i, makeItem(Material.PLAYER_HEAD,
                    Component.text((i + 1) + ". " + team.getName(), NamedTextColor.WHITE, TextDecoration.BOLD),
                    lore.toArray(new Component[0])));
        }

        inv.setItem(53, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 53) { openManageTournament(player, t); return true; }
            return false;
        });
    }

    // ======================== ITEM BUILDERS ========================

    private ItemStack makeItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore.length > 0) {
                meta.lore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeTournamentItem(Tournament t) {
        Material icon = switch (t.getState()) {
            case OPEN -> Material.EMERALD;
            case ACTIVE -> Material.FIREWORK_ROCKET;
            case FINISHED -> Material.GOLD_INGOT;
            case CANCELLED -> Material.BARRIER;
        };
        NamedTextColor stateColor = switch (t.getState()) {
            case OPEN -> NamedTextColor.GREEN;
            case ACTIVE -> NamedTextColor.AQUA;
            case FINISHED -> NamedTextColor.GOLD;
            case CANCELLED -> NamedTextColor.RED;
        };

        return makeItem(icon,
                Component.text(t.getName(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("State: ", NamedTextColor.GRAY).append(Component.text(t.getState().name(), stateColor)),
                Component.text("Format: " + formatName(t.getFormat()) + " | Game: " + t.getProviderName(), NamedTextColor.GRAY),
                Component.text("Teams: " + t.getTeamCount() + "/" + t.getMaxTeams(), NamedTextColor.GRAY),
                Component.text("ID: " + t.getId().toString().substring(0, 8), NamedTextColor.DARK_GRAY));
    }

    private ItemStack makeTournamentInfoItem(Tournament t) {
        return makeItem(Material.ENDER_EYE,
                Component.text(t.getName(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("State: " + t.getState().name(), NamedTextColor.AQUA),
                Component.text("Format: " + formatName(t.getFormat()) + " | Game: " + t.getProviderName(), NamedTextColor.GRAY),
                Component.text("Teams: " + t.getTeamCount() + "/" + t.getMaxTeams(), NamedTextColor.GRAY),
                Component.text("Team Size: " + t.getTeamSize(), NamedTextColor.GRAY),
                Component.text("Created: " + new Date(t.getCreatedTime()).toString(), NamedTextColor.DARK_GRAY));
    }

    private ItemStack makeMatchItem(Tournament t, Match m) {
        String team1Name = m.getTeam1Id() != null ? safeTeamName(t, m.getTeam1Id()) : "(bye)";
        String team2Name = m.getTeam2Id() != null ? safeTeamName(t, m.getTeam2Id()) : "(bye)";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: " + m.getStatus().name(), statusColor(m.getStatus())));
        if (m.getWinnerId() != null) {
            lore.add(Component.text("Winner: " + safeTeamName(t, m.getWinnerId()), NamedTextColor.GOLD));
        }
        if (m.getArenaName() != null && !m.getArenaName().isEmpty()) {
            lore.add(Component.text("Arena: " + m.getArenaName(), NamedTextColor.GRAY));
        }

        Material icon = switch (m.getStatus()) {
            case PENDING -> Material.PAPER;
            case IN_PROGRESS -> Material.FIRE_CHARGE;
            case FINISHED -> Material.MAP;
        };

        return makeItem(icon,
                Component.text(team1Name + " vs " + team2Name, NamedTextColor.WHITE),
                lore.toArray(new Component[0]));
    }

    // ======================== HELPERS ========================

    private void fillBorders(Inventory inv) {
        ItemStack border = makeItem(Material.GRAY_STAINED_GLASS_PANE,
                Component.text("", NamedTextColor.GRAY));
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, border);
        }
        for (int slot : new int[]{10, 11, 12, 13, 14, 22, 26}) {
            inv.setItem(slot, null);
        }
    }

    private static String safeTeamName(Tournament t, UUID teamId) {
        TournamentTeam team = t.getTeam(teamId);
        return team != null ? team.getName() : "???";
    }

    private static String formatName(TournamentFormat format) {
        return switch (format) {
            case SINGLE_ELIMINATION -> "Single Elim";
            case SWISS -> "Swiss";
        };
    }

    private static NamedTextColor statusColor(MatchStatus status) {
        return switch (status) {
            case PENDING -> NamedTextColor.GRAY;
            case IN_PROGRESS -> NamedTextColor.YELLOW;
            case FINISHED -> NamedTextColor.GREEN;
        };
    }

    private Tournament findTournamentByIdPrefix(String prefix) {
        for (Tournament t : manager.getAllTournaments()) {
            if (t.getId().toString().startsWith(prefix)) return t;
        }
        try {
            return manager.getTournament(UUID.fromString(prefix));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ======================== CONTEXT ========================

    private static class CreateContext {
        String name = "";
        TournamentFormat format = TournamentFormat.SINGLE_ELIMINATION;
        int teamSize = 1;
        int maxTeams = 16;
        int providerIndex = 0;
        List<MinigameProvider> providers;

        CreateContext(List<MinigameProvider> providers) {
            this.providers = providers;
        }
    }
}
