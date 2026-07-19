package com.tdm.tournament.command;

import com.tdm.tournament.TournamentManager;
import com.tdm.tournament.TournamentPlugin;
import com.tdm.tournament.gui.AdminGUI;
import com.tdm.tournament.gui.PlayerGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /tournament command.
 * Routes players to the appropriate GUI or handles text commands.
 */
public class TournamentCommand implements CommandExecutor, TabCompleter {

    private final TournamentPlugin plugin;
    private final TournamentManager manager;
    private final AdminGUI adminGUI;
    private final PlayerGUI playerGUI;

    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "admin", "create", "start", "cancel", "delete", "list"
    );

    private static final List<String> PLAYER_SUBCOMMANDS = Arrays.asList(
            "join", "leave", "view", "matches", "history"
    );

    public TournamentCommand(TournamentPlugin plugin, TournamentManager manager,
                             AdminGUI adminGUI, PlayerGUI playerGUI) {
        this.plugin = plugin;
        this.manager = manager;
        this.adminGUI = adminGUI;
        this.playerGUI = playerGUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // Open player GUI by default
            playerGUI.openTournamentList(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "admin" -> {
                if (!player.hasPermission("tournament.admin")) {
                    player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                adminGUI.openMainMenu(player);
            }
            case "create" -> {
                if (!player.hasPermission("tournament.admin")) {
                    player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                adminGUI.openCreateMenu(player);
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /tournament join <tournament-id>", NamedTextColor.RED));
                    return true;
                }
                playerGUI.promptJoinTournament(player, args[1]);
            }
            case "leave" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /tournament leave <tournament-id>", NamedTextColor.RED));
                    return true;
                }
                playerGUI.promptLeaveTournament(player, args[1]);
            }
            case "view" -> {
                playerGUI.openTournamentList(player);
            }
            case "matches" -> {
                playerGUI.openMyMatches(player);
            }
            case "history" -> {
                playerGUI.openMatchHistory(player);
            }
            case "list" -> {
                // Admin list
                if (!player.hasPermission("tournament.admin")) {
                    player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                adminGUI.openManageTournamentsList(player);
            }
            case "start" -> {
                if (!player.hasPermission("tournament.admin")) {
                    player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /tournament start <tournament-id>", NamedTextColor.RED));
                    return true;
                }
                adminGUI.promptStartTournament(player, args[1]);
            }
            case "cancel" -> {
                if (!player.hasPermission("tournament.admin")) {
                    player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /tournament cancel <tournament-id>", NamedTextColor.RED));
                    return true;
                }
                adminGUI.promptCancelTournament(player, args[1]);
            }
            default -> {
                player.sendMessage(Component.text("Unknown subcommand. Use /tournament to open the GUI.", NamedTextColor.RED));
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission("tournament.admin")) {
                subs.addAll(ADMIN_SUBCOMMANDS);
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && sender.hasPermission("tournament.admin")) {
            String sub = args[0].toLowerCase();
            if (List.of("start", "cancel", "delete", "join", "leave").contains(sub)) {
                return manager.getAllTournaments().stream()
                        .map(t -> t.getId().toString().substring(0, 8))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
