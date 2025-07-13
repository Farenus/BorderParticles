package me.michal.borderparticles;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class BorderParticles extends JavaPlugin {

    private int spawnX;
    private int spawnZ;
    private int radius;
    private String worldName;
    private int refreshInterval;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        startBorderTask();
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        worldName = config.getString("world", "world");
        spawnX = config.getInt("spawnX", -73);
        spawnZ = config.getInt("spawnZ", -10);
        radius = config.getInt("radius", 500);
        refreshInterval = config.getInt("refreshIntervalSeconds", 60);
    }

    private void startBorderTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(worldName);
                if (world == null) return;

                int minX = spawnX - radius;
                int maxX = spawnX + radius;
                int minZ = spawnZ - radius;
                int maxZ = spawnZ + radius;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getWorld().getName().equals(worldName)) continue;

                    int y = player.getLocation().getBlockY();
                    double[] offsets = {-0.25, 0.0, 0.25};

                    for (int x = minX; x <= maxX; x += 2) {
                        for (double offsetZ : offsets) {
                            for (double offsetX : offsets) {
                                player.spawnParticle(org.bukkit.Particle.DUST, x + 0.5 + offsetX, y, minZ + 0.5 + offsetZ, 0,
                                    0, 0, 0, 1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1));
                                player.spawnParticle(org.bukkit.Particle.DUST, x + 0.5 + offsetX, y, maxZ + 0.5 + offsetZ, 0,
                                    0, 0, 0, 1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1));
                            }
                        }
                    }
                    for (int z = minZ; z <= maxZ; z += 2) {
                        for (double offsetZ : offsets) {
                            for (double offsetX : offsets) {
                                player.spawnParticle(org.bukkit.Particle.DUST, minX + 0.5 + offsetX, y, z + 0.5 + offsetZ, 0,
                                    0, 0, 0, 1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1));
                                player.spawnParticle(org.bukkit.Particle.DUST, maxX + 0.5 + offsetX, y, z + 0.5 + offsetZ, 0,
                                    0, 0, 0, 1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, refreshInterval * 20L);
    }
}
