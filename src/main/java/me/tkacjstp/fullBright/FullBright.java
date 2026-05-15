package me.tkacjstp.fullBright;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.yaml.snakeyaml.Yaml;


import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FullBright extends JavaPlugin implements  Listener {

    private final  HashSet<UUID> fullBrightEnabled = new HashSet<>();
    private final Map<UUID, List<Location>> deathLocations = new HashMap<>();
    private final Map<UUID, List<List<ItemStack>>> deathItems = new HashMap<>();
    private final Map<UUID, Integer> trackingIndex = new HashMap<>();
    private File dataFile;
    private YamlConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataFile();
        loadDeathData();
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

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "deathData.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDeathData() {
        dataConfig.set("data", null);
        for (UUID uuid : deathLocations.keySet()) {
            dataConfig.set("data." + uuid.toString() + ".location", deathLocations.get(uuid));
            dataConfig.set("data." + uuid.toString() + ".items", deathItems.get(uuid));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDeathData() {
        if (dataConfig.getConfigurationSection("data") == null)
            return;
        for (String uuidStr : dataConfig.getConfigurationSection("data").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            List<Location> locations = (List<Location>) dataConfig.getList("data." + uuidStr + ".location");
            List<List<ItemStack>> items = (List<List<ItemStack>>) dataConfig.getList("data." + uuidStr + ".items");
            deathLocations.put(uuid, locations);
            deathItems.put(uuid, items);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (event.getDrops().isEmpty())
            return;

        Location loc = player.getLocation().getBlock().getLocation();
        loc.getBlock().setType(Material.CHEST);

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        UUID uuid = player.getUniqueId();
        deathLocations.computeIfAbsent(uuid, k -> new ArrayList<>()).add(loc);
        deathItems.computeIfAbsent(uuid, k -> new ArrayList<>()).add(drops);

        saveDeathData();
    }

    @EventHandler
    public void onCheatOpen (PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CHEST);

        for (List<Location> locs : deathLocations.values()){
            if (locs.contains(event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlockBreak (BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CHEST);
        Location breakLoc = event.getBlock().getLocation();

        for (UUID uuid : deathLocations.keySet()) {
            List<Location> locs = deathLocations.get(uuid);
            if (locs.contains(breakLoc)) {
                int index = locs.indexOf(breakLoc);

                List<ItemStack> saved = deathItems.get(uuid).get(index);
                for (ItemStack item : saved) {
                    event.getBlock().getWorld().dropItemNaturally(breakLoc, item);
                }

                locs.remove(index);
                deathItems.get(uuid).remove(index);
                event.setDropItems(false);

                if (locs.isEmpty()) {
                    deathLocations.remove(uuid);
                    deathItems.remove(uuid);
                }

                saveDeathData();
                return;
            }
        }
    }

    private void updateSystems() {
        for (Player player : getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            List<Location> locs = deathLocations.get(uuid);
            int targetIdx = trackingIndex.getOrDefault(uuid , 0);

            if (locs != null && !locs.isEmpty()) {
                if (targetIdx >= locs.size()) targetIdx = 0;
                Location target = locs.get(targetIdx);
            }
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (command.getName().equalsIgnoreCase("cf") && args.length > 0) {
                try {
                    int idx = Integer.parseInt(args[0]) - 1;
                    trackingIndex.put(player.getUniqueId(), idx);
                    player.sendMessage("§a[!] 추적 대상이 \" + (idx + 1) + \"번 상자로 변경되었습니다.");
                } catch (NumberFormatException e) {
                    player.sendMessage("§c숫자를 입력하세요.");
                }
                return true;
            }


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

        int index = trackingIndex.getOrDefault(player.getUniqueId(), 0);
        List<Location> locs = deathLocations.get(player.getUniqueId());

        Location dLoc = null;
        if (locs != null && !locs.isEmpty()){
            if (index >= locs.size()) index = 0;
            dLoc = locs.get(index);
        }
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








