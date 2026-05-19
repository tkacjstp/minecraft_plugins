package me.tkacjstp.fullBright;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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


import java.io.File;
import java.util.*;



public final class FullBright extends JavaPlugin implements  Listener {

    private final HashSet<UUID> fullBrightEnabled = new HashSet<>();
    private final Map<UUID, List<Location>> deathLocations = new HashMap<>();
    private final Map<UUID, List<List<ItemStack>>> deathItems = new HashMap<>();
    private final Map<UUID, Integer> trackingIndex = new HashMap<>();
    private File dataFile;
    private YamlConfiguration dataConfig;

    @Override
    public void onEnable() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(this, 0L, 2L); // 2틱(0.1초)마다 실행

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("light").setExecutor(this);
        getCommand("cf").setExecutor(this);

        //saveDefaultConfig();
        createDataFile();
        new BukkitRunnable() {
            @Override
            public void run() {
                loadDeathData();
            }

        }.runTaskLater(this, 20L);


    }

    @Override
    public void onDisable() {
        saveDeathData();
        getLogger().info("deathdata saved");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (command.getName().equalsIgnoreCase("cf")) {
                if (args.length > 0) {
                    try {
                        int idx = Integer.parseInt(args[0]) - 1;
                        trackingIndex.put(uuid, idx);
                        player.sendMessage("§a[!] 추적 대상이 " + (idx + 1) + "번 상자로 변경되었습니다.");
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c숫자를 입력하세요.");
                    }
                }
                return true;
            }

            if (command.getName().equalsIgnoreCase("light")) {
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
        }
        return false;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (event.getDrops().isEmpty())
            return;

        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack item : event.getDrops()) {
            if (item != null && item.getType() != Material.AIR) {
                drops.add(item.clone());
            }
        }


        Location loc = player.getLocation().getBlock().getLocation();
        loc.getBlock().setType(Material.CHEST);

        UUID uuid = player.getUniqueId();
        deathLocations.computeIfAbsent(uuid, k -> new ArrayList<>()).add(loc);
        deathItems.computeIfAbsent(uuid, k -> new ArrayList<>()).add(drops);

        event.getDrops().clear();

        saveDeathData();
    }

    @EventHandler
    public void onCheatOpen (PlayerInteractEvent event) {
        if (event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.CHEST) {
                Location loc = event.getClickedBlock().getLocation();

                for (List<Location> locList : deathLocations.values()) {
                    if (locList.contains(loc)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onBlockBreak (BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CHEST)
            return;

        Location breakLoc = event.getBlock().getLocation();

        for (UUID uuid : deathLocations.keySet()) {
            List<Location> locs = deathLocations.get(uuid);
            if (locs == null) continue;

            for (int i = 0; i < locs.size(); i++) {
                if (locs.get(i).equals(breakLoc)) {
                    event.setCancelled(false);
                    breakLoc.getBlock().setType(Material.AIR);

                    List<List<ItemStack>> allItems = deathItems.get(uuid);
                    if (allItems != null && allItems.size() > i) {
                        List<ItemStack> items = allItems.get(i);
                        if (items != null) {
                            for (ItemStack item : items) {
                                breakLoc.getWorld().dropItemNaturally(breakLoc, item);
                            }
                        }

                        allItems.remove(i);
                    }
                    locs.remove(i);
                    event.getPlayer().sendMessage("§a[시스템] 회수 완료.");
                    return;
                }
                saveDeathData();
            }
        }
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

        try {
            List<Location> locs = deathLocations.get(player.getUniqueId());
            if (locs != null && !locs.isEmpty()) {
                int index = trackingIndex.getOrDefault(player.getUniqueId(), 0);
                if (index >= locs.size()) index = locs.size() - 1;
                if (index < 0) index = 0;

                Location dLoc = locs.get(index);

                if (dLoc != null) {
                    String currentWorld = player.getWorld().getName();
                    String deathWorld = dLoc.getWorld().getName();

                    String translatedWorld;
                    if (deathWorld.contains("nether")) translatedWorld = "§c[ 네더 ]";
                    else if (deathWorld.contains("the_end")) translatedWorld = "§d[ 엔더 ]";
                    else translatedWorld = "§a[ 오버월드 ]";

                    if (currentWorld.equals(deathWorld)) {
                        int distance = (int) player.getLocation().distance(dLoc);

                        /*if (distance < 2 && !player.isDead()) {
                            deathLocations.remove(player.getUniqueId());
                            player.setScoreboard(board);
                            return;
                        }*/

                        double dY = dLoc.getBlockY();
                        for (int i = (int) (-64 - dY); i < (int) (220 - dY); i += 1) {
                            player.spawnParticle(Particle.END_ROD, dLoc.clone().add(0, i, 0), 5, 0.1, 0.1, 0.1, 0);
                        }
                        objective.getScore("§f거리: §b" + distance + "m").setScore(3);

                        objective.getScore("§7--------------------").setScore(6);
                        objective.getScore("§e§l사망 위치 정보").setScore(5);
                        objective.getScore(translatedWorld).setScore(4);
                        objective.getScore("§fX: §7" + dLoc.getBlockX() + " Y: §7" + dLoc.getBlockY() + " Z: §7" + dLoc.getBlockZ()).setScore(2);
                        objective.getScore("§f상자 번호: §e" + (index + 1) + "번").setScore(1);
                        objective.getScore("§7-------------------- ").setScore(0);

                    } else {
                        objective.getScore("§7(다른 월드에 있음)").setScore(3);

                        objective.getScore("§7--------------------").setScore(6);
                        objective.getScore("§e§l사망 위치 정보").setScore(5);
                        objective.getScore(translatedWorld).setScore(4);
                        objective.getScore("§fX: §7" + dLoc.getBlockX() + " Y: §7" + dLoc.getBlockY() + " Z: §7" + dLoc.getBlockZ()).setScore(2);
                        objective.getScore("§f상자 번호: §e" + (index + 1) + "번").setScore(1);
                        objective.getScore("§7-------------------- ").setScore(0);

                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        player.setScoreboard(board);
    }

    private void createDataFile() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
                getLogger().info("FullBright 데이터 폴더를 새로 생성했습니다.");
            }

            dataFile = new File(getDataFolder(), "deathData.yml");
            if (!dataFile.exists()) {
                boolean created = dataFile.createNewFile();
                if (created) {
                    getLogger().info("dataFile created");
                } else {
                    getLogger().severe("datafile created failed");
                }

            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDeathData() {
        if (dataConfig == null || dataFile == null)
            return;

        try {
            dataConfig.set("data", null);

            for (UUID uuid : deathLocations.keySet()) {
                List<Location> locs = deathLocations.get(uuid);
                List<List<ItemStack>> allItems = deathItems.get(uuid);

                if (locs == null || locs.isEmpty())
                    continue;

                String uuidStr = uuid.toString();

                for (int i = 0; i < locs.size(); i++) {
                    Location loc = locs.get(i);
                    if (loc == null || loc.getWorld() == null)
                        continue;

                    List<ItemStack> items = (allItems != null && i < allItems.size()) ? allItems.get(i) : new ArrayList<>();

                    String path = uuidStr + "." + i;

                    String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
                    dataConfig.set("data" + path + ".location", locStr);
                    dataConfig.set("data" + path + ".items", items);

                }
            }
            dataConfig.save(dataFile);
        } catch (Exception e) {
            getLogger().severe("사망 데이터를 deathData.yml에 저장하는 도중 오류가 발생했습니다!");
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadDeathData() {
        if (dataConfig == null || !dataFile.exists() || dataConfig == null)
            return;
        try {
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            if (!dataConfig.contains("data"))
                return;

            ConfigurationSection dataSection = dataConfig.getConfigurationSection("data");
            if (dataSection == null)
                return;

            Map<UUID, List<Location>> tempLocations = new HashMap<>();
            Map<UUID, List<List<ItemStack>>> tempItems = new HashMap<>();

            for (String uuidStr : dataSection.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                ConfigurationSection playerSection = dataSection.getConfigurationSection(uuidStr);
                if (playerSection == null)
                    continue;

                List<Location> locs = new ArrayList<>();
                List<List<ItemStack>> allItems = new ArrayList<>();

                for (String indexStr : playerSection.getKeys(false)) {
                    String path = uuidStr + "." + indexStr;

                    String locStr = (String) dataConfig.get("data" + path + ".location");
                    List<?> rawitems = dataConfig.getList("data" + path + ".items");
                    List<ItemStack> items = new ArrayList<>();
                    if (rawitems != null) {
                        for (Object obj : rawitems) {
                            if (obj instanceof ItemStack) {
                                items.add((ItemStack) obj);
                            }
                        }
                    }

                    if (locStr != null) {
                        String[] split = locStr.split(",");
                        if (split.length == 4) {
                            org.bukkit.World world = Bukkit.getWorld(split[0]);
                            if (world != null) {
                                int x = Integer.parseInt(split[1]);
                                int y = Integer.parseInt(split[2]);
                                int z = Integer.parseInt(split[3]);
                                Location loc = new Location(world, x, y, z);

                                locs.add(loc);
                                allItems.add(items);
                            }
                        }
                    }
                }

                if (!locs.isEmpty()) {
                    deathLocations.put(uuid, locs);
                    deathItems.put(uuid, allItems);
                }
            }

            deathLocations.clear();
            deathItems.clear();
            deathLocations.putAll(tempLocations);
            deathItems.putAll(tempItems);

            getLogger().info("deathData.yml로부터 사망 상자 데이터를 성공적으로 복구했습니다.");
        } catch (Exception e) {
            getLogger().severe("deathData.yml 데이터를 읽어오는 도중 문법 오류가 발견되었습니다!");
            e.printStackTrace();
        }

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

    private String formatBiomeName(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
        }

        return sb.toString().trim();
    }
}








