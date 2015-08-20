package com.c45y.C4CTF;

import com.c45y.C4CTF.team.ColorTeam;
import com.c45y.C4CTF.team.TeamManager;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Wool;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;


public class C4CTF extends JavaPlugin implements Listener {
    
    public TeamManager teamManager;
    private Scoreboard scoreboard;
    public Objective scoreboardObjective;

    @Override
    public void onEnable() {
        this.getConfig().options().copyDefaults(true);
        this.getConfig().addDefault("worldJoinAssign", new String[] {"world"});
        this.getConfig().addDefault("countKills", true);
        List<ItemStack> respawnKit = new ArrayList<ItemStack>();
        
        // Kit sword
        ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);
        sword.addEnchantment(Enchantment.KNOCKBACK, 2);
        sword.addEnchantment(Enchantment.DURABILITY, 3);
        respawnKit.add(sword);
        
        // Kit bow
        ItemStack bow = new ItemStack(Material.BOW, 1);
        bow.addEnchantment(Enchantment.ARROW_INFINITE, 1);
        bow.addEnchantment(Enchantment.DURABILITY, 3);
        respawnKit.add(bow);
        
        // Single arrow for INF bow
        respawnKit.add(new ItemStack(Material.ARROW, 1));
        this.getConfig().addDefault("respawnKit", respawnKit);
        
        this.saveConfig();
        this.reloadConfig();
        
        this.scoreboard = this.getServer().getScoreboardManager().getNewScoreboard();
        this.scoreboardObjective = this.scoreboard.registerNewObjective("sidebar", "dummy");
        this.scoreboardObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.scoreboardObjective.setDisplayName("Team Score");
                
        this.teamManager = new TeamManager(this);
        for (ColorTeam team: this.teamManager.getTeams()) {
            this.getServer().getPluginManager().registerEvents(team.playerHandler, this);
        }
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        this.teamManager.persistTeams();
    }
    
    public void updateScoreboard() {
        for (Player p: this.getServer().getOnlinePlayers()) {
            p.setScoreboard(this.scoreboard);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {      
        event.getPlayer().setScoreboard(this.scoreboard);
    }
    
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if( event.getPlayer().hasPermission("ctf.op")) {
            getLogger().log(Level.INFO, "Player {0} has permission ctf.op", event.getPlayer().getName());
            return;
        }
        if (!getConfig().getStringList("worldJoinAssign").contains(event.getPlayer().getWorld().getName())) {
            getLogger().log(Level.INFO, "World {0} not found in configured worlds", event.getPlayer().getWorld().getName());
            return;
        }
        if (!this.teamManager.inTeam(event.getPlayer())) {
            ColorTeam team = this.teamManager.lowestTeam();
            if (team == null) {
                getLogger().log(Level.INFO, "No teams found when adding player {0}", event.getPlayer().getName());
                return; // We probably havent created any teams yet
            }
            team.config.addPlayer(event.getPlayer());
            team.broadcast(event.getPlayer().getName() + " has joined team " + team.getName());
            team.spawnPlayer(event.getPlayer());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!this.teamManager.inTeam((OfflinePlayer) event.getWhoClicked())) {
            return;
        }
        if (event.getSlot() == 39 /* Helmet slot */) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        ColorTeam team = this.teamManager.getTeam(event.getPlayer());
        if (team == null) {
            return;
        }
        event.getPlayer().setDisplayName(team.getChatColor() + event.getPlayer().getName() + ChatColor.RESET);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ctf")) {
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("ctfadmin") && sender.hasPermission("ctf.op")) {
            if (!(sender instanceof Player)) {
                return true;
            }
            Player player = (Player) sender;
            
            if (args.length == 0) {
                player.sendMessage("Missing required arguements. [create, setspawn, reset]");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("save")) {
                this.teamManager.persistTeams();
                return true;
            }
            else if (args[0].equalsIgnoreCase("broadcast")) {
                for (ColorTeam team: this.teamManager.getTeams()) {
                    this.getServer().broadcastMessage(team.getChatColor() + "Team " + team.getName() + " has " + team.countPlayers() + " players!");
                    for (OfflinePlayer p: team.config.getPlayers()) {
                        this.getServer().broadcastMessage(team.getChatColor() + " - " + p.getName() + " (" + p.getUniqueId().toString() + ")");
                    }
                }
                return true;
            }
            else if (args[0].equalsIgnoreCase("reset")) {
                for (ColorTeam team: this.teamManager.getTeams()) {
                    team.config.reset();
                }
                return true;
            }
            
            // Decide what team we are interacting with
            ItemStack itemInHand = player.getItemInHand();
            if( itemInHand == null || itemInHand.getType() != Material.WOOL) {
                player.sendMessage("You must have the teams wool block in your hand when running commands!");
                return true;
            }
            Wool wool = (Wool) itemInHand.getData();
            
            if (args[0].equalsIgnoreCase("create")) {
                this.teamManager.addTeam(wool);
                player.sendMessage("Team " + wool.getColor().name() + " has has been created!");
            } 
            else if (args[0].equalsIgnoreCase("setspawn")) {
                ColorTeam team = this.teamManager.getTeam(wool);
                if (team == null) {
                    player.sendMessage("Invalid team, do you need to \"/ctf create\" it first?");
                    return true;
                }
                team.config.setSpawn(player.getLocation());
                player.sendMessage("Team " + team.getName() + " has had their spawn set!");
            }
            return true;
        }
        return false;
    }
}
