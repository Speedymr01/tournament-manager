package com.tdm.tournament.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * A team participating in a tournament.
 * For solo tournaments each team holds exactly one player.
 */
public class TournamentTeam {

    private final UUID id;
    private String name;
    private final List<UUID> members; // player UUIDs
    private int seed;                 // seeding for bracket placement

    public TournamentTeam(String name, UUID firstMember) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.members = new ArrayList<>();
        this.members.add(firstMember);
        this.seed = 0;
    }

    public TournamentTeam(UUID id, String name, List<UUID> members, int seed) {
        this.id = id;
        this.name = name;
        this.members = new ArrayList<>(members);
        this.seed = seed;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<UUID> getMembers() { return Collections.unmodifiableList(members); }
    public int getSeed() { return seed; }
    public void setSeed(int seed) { this.seed = seed; }

    public void addMember(UUID player) {
        if (!members.contains(player)) {
            members.add(player);
        }
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public boolean containsPlayer(UUID player) {
        return members.contains(player);
    }

    public int getSize() { return members.size(); }

    /** Get online members of this team. */
    public List<Player> getOnlineMembers() {
        List<Player> online = new ArrayList<>();
        for (UUID uid : members) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    /** Get display names of all members for tooltip. */
    public List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (UUID uid : members) {
            Player p = Bukkit.getPlayer(uid);
            names.add(p != null ? p.getName() : uid.toString().substring(0, 8));
        }
        return names;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TournamentTeam that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
