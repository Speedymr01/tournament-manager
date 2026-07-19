package com.tdm.tournament;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * TournamentManager — orchestrates tournaments using TDMAPI and SpleefAPI
 * via Bukkit's ServicesManager.
 *
 * Soft dependencies: TeamDeathmatch, Spleef
 */
public class TournamentPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("TournamentManager v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TournamentManager disabled!");
    }
}
