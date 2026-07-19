package com.tdm.tournament.gui;

import com.tdm.tournament.TournamentManager;
import com.tdm.tournament.TournamentPlugin;
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
 * Player-facing inventory GUIs for browsing and joining tournaments.
 *
 * Menus:
 *  - Tournament List (open tournaments, paginated)
 *  - Tournament Details (info + join/leave)
 *  - My Matches (current/upcoming matches for the player)
 *  - Match History (past matches)
 */
public class PlayerGUI {

    private final TournamentPlugin plugin;
    private final TournamentManager manager;

    private static final Component TITLE_LIST = Component.text("Open Tournaments", NamedTextColor.DARK_AQUA);
    private static final Component TITLE_MY_MATCHES = Component.text("My Matches", NamedTextColor.DARK_AQUA);
    private static final Component TITLE_MY_HISTORY = Component.text("Match History", NamedTextColor.DARK_AQUA);

    public PlayerGUI(TournamentPlugin plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ======================== OPEN TOURNAMENTS LIST ========================

    public void openTournamentList(Player player) {
        List<Tournament> openTournaments = manager.getOpenTournaments();
        openPaginatedTournamentList(player, openTournaments, 0);
    }

    private void openPaginatedTournamentList(Player player, List<Tournament> tournaments, int page) {
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) tournaments.size() / pageSize));
        int start = page * pageSize;
        int end = Math.min(start + pageSize, tournaments.size());
        List<Tournament> pageItems = tournaments.subList(start, end);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_LIST);

        int slot = 0;
        for (Tournament t : pageItems) {
            inv.setItem(slot, makeOpenTournamentItem(player, t));
            slot++;
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, makeItem(Material.ARROW,
                    Component.text("Previous Page", NamedTextColor.YELLOW)));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, makeItem(Material.ARROW,
                    Component.text("Next Page", NamedTextColor.YELLOW)));
        }

        // My matches button
        inv.setItem(48, makeItem(Material.CLOCK,
                Component.text("My Matches", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("View your current matches", NamedTextColor.GRAY)));

        inv.setItem(49, makeItem(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED)));

        inv.setItem(50, makeItem(Material.BOOK,
                Component.text("Match History", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("View your past matches", NamedTextColor.GRAY)));

        int finalPage = page;
        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) ->
                handleTournamentListClick(p, s, tournaments, finalPage));
    }

    private boolean handleTournamentListClick(Player player, int slot,
                                               List<Tournament> tournaments, int page) {
        int pageSize = 45;

        if (slot == 45 && page > 0) {
            openPaginatedTournamentList(player, tournaments, page - 1);
            return true;
        }
        if (slot == 53 && (page + 1) * pageSize < tournaments.size()) {
            openPaginatedTournamentList(player, tournaments, page + 1);
            return true;
        }
        if (slot == 48) {
            openMyMatches(player);
            return true;
        }
        if (slot == 49) {
            player.closeInventory();
            return true;
        }
        if (slot == 50) {
            openMatchHistory(player);
            return true;
        }

        if (slot >= 0 && slot < pageSize) {
            int index = page * pageSize + slot;
            if (index < tournaments.size()) {
                Tournament t = tournaments.get(index);
                openTournamentDetails(player, t);
                return true;
            }
        }
        return false;
    }

    // ======================== TOURNAMENT DETAILS ========================

    public void openTournamentDetails(Player player, Tournament t) {
        boolean isInTournament = t.isPlayerInTournament(player.getUniqueId());
        boolean isFull = t.isFull();

        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(t.getName(), NamedTextColor.DARK_AQUA));

        // Tournament info
        inv.setItem(4, makeTournamentDetailItem(t));

        // Join / Leave buttons
        if (t.getState() == TournamentState.OPEN) {
            if (isInTournament) {
                inv.setItem(11, makeItem(Material.REDSTONE,
                        Component.text("Leave Tournament", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("Click to leave this tournament", NamedTextColor.GRAY)));
            } else if (isFull) {
                inv.setItem(11, makeItem(Material.BARRIER,
                        Component.text("Tournament Full", NamedTextColor.GRAY),
                        Component.text("This tournament has reached max teams", NamedTextColor.DARK_GRAY)));
            } else {
                inv.setItem(11, makeItem(Material.EMERALD,
                        Component.text("Join Tournament", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text("Click to join this tournament", NamedTextColor.GRAY)));
            }
        }

        // Show my team info if joined
        if (isInTournament) {
            TournamentTeam team = t.getTeamByPlayer(player.getUniqueId());
            if (team != null) {
                inv.setItem(15, makeItem(Material.PLAYER_HEAD,
                        Component.text("Your Team: " + team.getName(), NamedTextColor.AQUA),
                        Component.text("Members: " + String.join(", ", team.getMemberNames()), NamedTextColor.GRAY)));
            }
        }

        // View bracket/standings (if tournament has started)
        if (t.getState() == TournamentState.ACTIVE || t.getState() == TournamentState.FINISHED) {
            inv.setItem(22, makeItem(Material.FILLED_MAP,
                    Component.text("View Bracket / Standings", NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text("See the current tournament progress", NamedTextColor.GRAY)));
        }

        inv.setItem(26, makeItem(Material.ARROW,
                Component.text("Back to List", NamedTextColor.YELLOW)));

        Tournament finalT = t;
        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> handleTournamentDetailsClick(p, s, finalT));
    }

    private boolean handleTournamentDetailsClick(Player player, int slot, Tournament t) {
        if (slot == 26) {
            openTournamentList(player);
            return true;
        }

        boolean isInTournament = t.isPlayerInTournament(player.getUniqueId());

        if (slot == 11 && t.getState() == TournamentState.OPEN) {
            if (isInTournament) {
                // Leave
                if (manager.leaveTournament(t.getId(), player)) {
                    player.sendMessage(Component.text("Left tournament '" + t.getName() + "'", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("Failed to leave tournament.", NamedTextColor.RED));
                }
                openTournamentDetails(player, t);
            } else if (!t.isFull()) {
                // Join
                String teamName = player.getName();
                if (manager.joinTournament(t.getId(), player, teamName)) {
                    player.sendMessage(Component.text("Joined tournament '" + t.getName() + "'!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Failed to join tournament.", NamedTextColor.RED));
                }
                openTournamentDetails(player, t);
            }
            return true;
        }

        if (slot == 22 && (t.getState() == TournamentState.ACTIVE || t.getState() == TournamentState.FINISHED)) {
            // View bracket/standings (reuse admin bracket view logic, but read-only)
            // For simplicity, we use a simplified view here
            if (t.getFormat() == TournamentFormat.SINGLE_ELIMINATION) {
                openPlayerBracketView(player, t);
            } else {
                openPlayerStandingsView(player, t);
            }
            return true;
        }

        return false;
    }

    // ======================== PLAYER BRACKET / STANDINGS VIEW ========================

    private void openPlayerBracketView(Player player, Tournament t) {
        int maxRound = t.getMaxRound();
        if (maxRound < 0) {
            player.sendMessage(Component.text("No bracket data available.", NamedTextColor.RED));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Bracket: " + t.getName(), NamedTextColor.DARK_AQUA));

        // Round headers
        int cols = Math.min(maxRound + 1, 9);
        for (int round = 0; round < cols; round++) {
            String roundName = round == maxRound ? "Finals" :
                    round == maxRound - 1 ? "Semis" : "R" + (round + 1);
            inv.setItem(round, makeItem(Material.PAPER,
                    Component.text(roundName, NamedTextColor.WHITE, TextDecoration.BOLD)));
        }

        for (int round = 0; round < cols; round++) {
            List<Match> roundMatches = t.getRoundMatches(round);
            for (int i = 0; i < roundMatches.size() && i < 5; i++) {
                Match m = roundMatches.get(i);
                int slot = 9 + (round * 5) + i;
                if (slot >= 54) break;

                String team1Name = "???";
                String team2Name = "???";
                if (m.getTeam1Id() != null) {
                    TournamentTeam team = t.getTeam(m.getTeam1Id());
                    if (team != null) team1Name = team.getName();
                }
                if (m.getTeam2Id() != null) {
                    TournamentTeam team = t.getTeam(m.getTeam2Id());
                    if (team != null) team2Name = team.getName();
                }

                Component itemName = Component.text(team1Name + " vs " + team2Name, NamedTextColor.WHITE);
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Status: " + m.getStatus().name(), statusColor(m.getStatus())));

                if (m.getWinnerId() != null) {
                    TournamentTeam winner = t.getTeam(m.getWinnerId());
                    lore.add(Component.text("Winner: " + (winner != null ? winner.getName() : "???"), NamedTextColor.GOLD));
                }

                Material icon = switch (m.getStatus()) {
                    case PENDING -> Material.PAPER;
                    case IN_PROGRESS -> Material.FIRE_CHARGE;
                    case FINISHED -> Material.MAP;
                };

                inv.setItem(slot, makeItem(icon, itemName, lore.toArray(new Component[0])));
            }
        }

        inv.setItem(53, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 53) {
                openTournamentDetails(player, t);
                return true;
            }
            return false;
        });
    }

    private void openPlayerStandingsView(Player player, Tournament t) {
        List<SwissSystem.SwissStanding> standings = SwissSystem.getStandings(t);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Standings: " + t.getName(), NamedTextColor.DARK_AQUA));

        for (int i = 0; i < Math.min(standings.size(), 45); i++) {
            SwissSystem.SwissStanding s = standings.get(i);
            TournamentTeam team = t.getTeam(s.teamId);
            String teamName = team != null ? team.getName() : "Unknown";
            String medal = i == 0 ? "1st" : i == 1 ? "2nd" : i == 2 ? "3rd" : (i + 1) + "th";
            NamedTextColor color = i == 0 ? NamedTextColor.GOLD :
                    i == 1 ? NamedTextColor.GRAY :
                            i == 2 ? NamedTextColor.RED : NamedTextColor.WHITE;

            inv.setItem(i + 9, makeItem(
                    i == 0 ? Material.GOLD_INGOT : i == 1 ? Material.IRON_INGOT : i == 2 ? Material.COPPER_INGOT : Material.PAPER,
                    Component.text(medal + ". " + teamName, color, TextDecoration.BOLD),
                    Component.text("Wins: " + s.wins + " / Matches: " + s.matchesPlayed, NamedTextColor.GRAY)));
        }

        inv.setItem(53, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 53) {
                openTournamentDetails(player, t);
                return true;
            }
            return false;
        });
    }

    // ======================== MY MATCHES ========================

    public void openMyMatches(Player player) {
        UUID playerId = player.getUniqueId();
        List<Match> upcomingMatches = new ArrayList<>();

        // Gather all pending/in-progress matches for this player across all tournaments
        for (Tournament t : manager.getAllTournaments()) {
            if (t.getState() != TournamentState.ACTIVE) continue;
            TournamentTeam team = t.getTeamByPlayer(playerId);
            if (team == null) continue;

            for (Match m : t.getMatches()) {
                if (m.getStatus() == MatchStatus.FINISHED) continue;
                if (team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id())) {
                    upcomingMatches.add(m);
                }
            }
        }

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MY_MATCHES);

        if (upcomingMatches.isEmpty()) {
            inv.setItem(13, makeItem(Material.BARRIER,
                    Component.text("No matches found", NamedTextColor.GRAY),
                    Component.text("Join an active tournament to get matches", NamedTextColor.DARK_GRAY)));
        } else {
            for (int i = 0; i < Math.min(upcomingMatches.size(), 18); i++) {
                Match m = upcomingMatches.get(i);
                Tournament parentTournament = findTournamentForMatch(playerId, m.getId());
                String tourneyName = parentTournament != null ? parentTournament.getName() : "???";

                String team1Name = "???", team2Name = "???";
                if (parentTournament != null) {
                    if (m.getTeam1Id() != null) {
                        TournamentTeam t = parentTournament.getTeam(m.getTeam1Id());
                        if (t != null) team1Name = t.getName();
                    }
                    if (m.getTeam2Id() != null) {
                        TournamentTeam t = parentTournament.getTeam(m.getTeam2Id());
                        if (t != null) team2Name = t.getName();
                    }
                }

                inv.setItem(i, makeItem(
                        m.getStatus() == MatchStatus.IN_PROGRESS ? Material.FIRE_CHARGE : Material.PAPER,
                        Component.text(team1Name + " vs " + team2Name, NamedTextColor.WHITE, TextDecoration.BOLD),
                        Component.text("Tournament: " + tourneyName, NamedTextColor.GRAY),
                        Component.text("Status: " + m.getStatus().name(), statusColor(m.getStatus()))));
            }
        }

        inv.setItem(26, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 26) {
                openTournamentList(player);
                return true;
            }
            return false;
        });
    }

    // ======================== MATCH HISTORY ========================

    public void openMatchHistory(Player player) {
        List<Match> history = manager.getPlayerMatchHistory(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MY_HISTORY);

        if (history.isEmpty()) {
            inv.setItem(22, makeItem(Material.BARRIER,
                    Component.text("No match history", NamedTextColor.GRAY),
                    Component.text("Play in tournaments to see your history here", NamedTextColor.DARK_GRAY)));
        } else {
            int count = 0;
            for (int i = history.size() - 1; i >= 0 && count < 45; i--) {
                Match m = history.get(i);
                Tournament parentTournament = findTournamentForMatch(player.getUniqueId(), m.getId());
                String tourneyName = parentTournament != null ? parentTournament.getName() : "???";

                boolean playerWon = false;
                String opponent = "???";
                if (parentTournament != null) {
                    UUID playerTeamId = null;
                    TournamentTeam playerTeam = parentTournament.getTeamByPlayer(player.getUniqueId());
                    if (playerTeam != null) {
                        playerTeamId = playerTeam.getId();
                        playerWon = m.getWinnerId() != null && m.getWinnerId().equals(playerTeamId);

                        UUID opponentId = m.getTeam1Id() != null && m.getTeam1Id().equals(playerTeamId)
                                ? m.getTeam2Id() : m.getTeam1Id();
                        if (opponentId != null) {
                            TournamentTeam ot = parentTournament.getTeam(opponentId);
                            if (ot != null) opponent = ot.getName();
                        }
                    }
                }

                Component display = Component.text((playerWon ? "W" : "L") + " vs " + opponent,
                        playerWon ? NamedTextColor.GREEN : NamedTextColor.RED);

                inv.setItem(count++, makeItem(
                        playerWon ? Material.GREEN_DYE : Material.RED_DYE,
                        display,
                        Component.text("Tournament: " + tourneyName, NamedTextColor.GRAY),
                        Component.text("Status: " + m.getStatus().name(), statusColor(m.getStatus()))));
            }
        }

        inv.setItem(53, makeItem(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)));

        player.openInventory(inv);
        plugin.setGuiHandler(player.getUniqueId(), (p, s) -> {
            if (s == 53) {
                openTournamentList(player);
                return true;
            }
            return false;
        });
    }

    // ======================== CHAT-BASED JOIN/LEAVE ========================

    public void promptJoinTournament(Player player, String tournamentIdStr) {
        Tournament t = findTournamentByIdPrefix(tournamentIdStr);
        if (t == null) {
            player.sendMessage(Component.text("Tournament not found!", NamedTextColor.RED));
            return;
        }
        if (t.getState() != TournamentState.OPEN) {
            player.sendMessage(Component.text("That tournament is not open for joining.", NamedTextColor.RED));
            return;
        }
        if (t.isPlayerInTournament(player.getUniqueId())) {
            player.sendMessage(Component.text("You're already in that tournament!", NamedTextColor.RED));
            return;
        }

        String teamName = player.getName();
        if (manager.joinTournament(t.getId(), player, teamName)) {
            player.sendMessage(Component.text("Joined tournament '" + t.getName() + "'!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to join tournament. It may be full.", NamedTextColor.RED));
        }
    }

    public void promptLeaveTournament(Player player, String tournamentIdStr) {
        Tournament t = findTournamentByIdPrefix(tournamentIdStr);
        if (t == null) {
            player.sendMessage(Component.text("Tournament not found!", NamedTextColor.RED));
            return;
        }
        if (!t.isPlayerInTournament(player.getUniqueId())) {
            player.sendMessage(Component.text("You're not in that tournament!", NamedTextColor.RED));
            return;
        }
        if (t.getState() != TournamentState.OPEN) {
            player.sendMessage(Component.text("Cannot leave a tournament that has already started.", NamedTextColor.RED));
            return;
        }

        if (manager.leaveTournament(t.getId(), player)) {
            player.sendMessage(Component.text("Left tournament '" + t.getName() + "'.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Failed to leave tournament.", NamedTextColor.RED));
        }
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

    private ItemStack makeOpenTournamentItem(Player player, Tournament t) {
        boolean joined = t.isPlayerInTournament(player.getUniqueId());
        Material icon = joined ? Material.EMERALD_BLOCK : Material.EMERALD;

        return makeItem(icon,
                Component.text(t.getName(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("Format: " + formatName(t.getFormat()) + " | Game: " + t.getGameType().name(), NamedTextColor.GRAY),
                Component.text("Teams: " + t.getTeamCount() + "/" + t.getMaxTeams(), NamedTextColor.GRAY),
                Component.text("Team Size: " + t.getTeamSize(), NamedTextColor.GRAY),
                joined
                        ? Component.text("✓ Joined", NamedTextColor.GREEN)
                        : Component.text("Click to view details", NamedTextColor.YELLOW));
    }

    private ItemStack makeTournamentDetailItem(Tournament t) {
        return makeItem(Material.ENDER_EYE,
                Component.text(t.getName(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("Format: " + formatName(t.getFormat()), NamedTextColor.GRAY),
                Component.text("Game: " + t.getGameType().name(), NamedTextColor.GRAY),
                Component.text("Teams: " + t.getTeamCount() + "/" + t.getMaxTeams(), NamedTextColor.GRAY),
                Component.text("Team Size: " + t.getTeamSize(), NamedTextColor.GRAY));
    }

    // ======================== HELPERS ========================

    private Tournament findTournamentForMatch(UUID playerId, int matchId) {
        for (Tournament t : manager.getAllTournaments()) {
            TournamentTeam team = t.getTeamByPlayer(playerId);
            if (team == null) continue;
            if (t.getMatch(matchId) != null) return t;
        }
        return null;
    }

    private Tournament findTournamentByIdPrefix(String prefix) {
        for (Tournament t : manager.getAllTournaments()) {
            if (t.getId().toString().startsWith(prefix)) return t;
        }
        try {
            UUID uuid = UUID.fromString(prefix);
            return manager.getTournament(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
}
