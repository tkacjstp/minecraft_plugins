package me.tkacjstp.fullBright;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;


import java.util.HashSet;
import java.util.UUID;

public final class FullBright extends JavaPlugin implements  Listener {

    private final  HashSet<UUID> fullBrightEnabled = new HashSet<>();
    private final java.util.Map<UUID, Location> deathLocations = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("light").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(this, 0L, 2L); // 2틱(0.1초)마다 실행
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
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        Integer.MAX_VALUE,
                        255,
                        false,
                        false,
                        false));
            }

            return true;
        }

        return  false;
    }

    @EventHandler
    public  void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (fullBrightEnabled.contains(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fullBrightEnabled.remove(event.getPlayer().getUniqueId());
    }


    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Bukkit.getLogger().info("내 플러그인: 유저 사망 감지됨!");
        Player player = event.getEntity();
        deathLocations.put(player.getUniqueId(), player.getLocation());
    }



    private void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();

        Objective objective = board.registerNewObjective("stats", "dummy", "§a[ 정보 ]");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Location loc = player.getLocation();

        String x = String.format("%.2f", loc.getX());
        String y = String.format("%.2f", loc.getY());
        String z = String.format("%.2f", loc.getZ());
        String biome = formatBiomeName(loc.getBlock().getBiome().toString());

        objective.getScore("§fX: §e" + x).setScore(11);
        objective.getScore("§fY: §e" + y).setScore(10);
        objective.getScore("§fZ: §e" + z).setScore(9);
        objective.getScore("§fBiome: §b" + biome).setScore(8);
        objective.getScore("§1 ").setScore(7);

        Location dLoc = deathLocations.get(player.getUniqueId());
        if (dLoc != null) {
            String currentWorld = player.getWorld().getName();
            String deathWorld = dLoc.getWorld().getName();

            String translatedWorld;
            if (deathWorld.contains("nether")) translatedWorld = "§c[ 네더 ]";
            else if (deathWorld.contains("the_end")) translatedWorld = "§d[ 엔더 ]";
            else translatedWorld = "§a[ 오버월드 ]";

            if (currentWorld.equals(deathWorld)) {
                int distance = (int) player.getLocation().distance(dLoc);

                if (distance < 2 && !player.isDead()) {
                    deathLocations.remove(player.getUniqueId());
                    player.setScoreboard(board);
                    return;
                }

                double dY = dLoc.getBlockY();
                for (int i = (int) (-64 - dY); i < (int) (220 - dY); i += 1) {
                    player.spawnParticle(Particle.END_ROD, dLoc.clone().add(0, i, 0), 5, 0.1, 0.1, 0.1, 0);
                }
                    objective.getScore("§f거리: §b" + distance + "m").setScore(3);

                    objective.getScore("§7--------------------").setScore(6);
                    objective.getScore("§e§l사망 위치 정보").setScore(5);
                    objective.getScore(translatedWorld).setScore(4);
                    objective.getScore("§fX: §7" + dLoc.getBlockX() + " Y: §7" + dLoc.getBlockY() + " Z: §7" + dLoc.getBlockZ()).setScore(2);
                    objective.getScore("§7-------------------- ").setScore(1);

            } else {
                objective.getScore("§7(다른 월드에 있음)").setScore(3);

                objective.getScore("§7--------------------").setScore(6);
                objective.getScore("§e§l사망 위치 정보").setScore(5);
                objective.getScore(translatedWorld).setScore(4);
                objective.getScore("§fX: §7" + dLoc.getBlockX() + " Y: §7" + dLoc.getBlockY() + " Z: §7" + dLoc.getBlockZ()).setScore(2);
                objective.getScore("§7-------------------- ").setScore(1);

            }
        }

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








