package me.tkacjstp.fullBright;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.HashSet;
import java.util.UUID;

public final class FullBright extends JavaPlugin implements  Listener {

    private final  HashSet<UUID> fullBrightEnabled = new HashSet<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("light").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    updateScoreboard(player);

                    if (fullBrightEnabled.contains(player.getUniqueId())) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 1, false, false, false));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (fullBrightEnabled.contains(uuid)) {
                fullBrightEnabled.remove(uuid);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            } else {
                fullBrightEnabled.add(uuid);
            }

            return true;
        }

        return  false;
    }

    @EventHandler
    public  void onJoin(PlayerJoinEvent event) {
        updateScoreboard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fullBrightEnabled.remove(event.getPlayer().getUniqueId());
    }

    private void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        Objective objective = board.registerNewObjective("stats", "dummy", "§a[ 정보 ]");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Location loc = player.getLocation();

        String x = String.format("%.2f", loc.getX());
        String y = String.format("%.2f", loc.getY());
        String z = String.format("%.2f", loc.getZ());
        String biome = loc.getBlock().getBiome().toString();

        objective.getScore("§fX: §e" + x).setScore(4);
        objective.getScore("§fY: §e" + y).setScore(3);
        objective.getScore("§fZ: §e" + z).setScore(2);
        objective.getScore("§7---").setScore(1);
        objective.getScore("§fBiome: §b" + biome).setScore(0);

        player.setScoreboard(board);
    }

    private String formatBiomeName(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
        }

        return sb.toString().trim();
    }
}








