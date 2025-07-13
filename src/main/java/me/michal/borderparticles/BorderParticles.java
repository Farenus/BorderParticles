package me.michal.borderparticles;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class BorderParticles extends JavaPlugin {

    private String worldName;
    private int spawnX;
    private int spawnZ;
    private int radius;
    private long refreshIntervalTicks;
    private int particleViewDistance;
    private Particle.DustOptions dustOptions;

    // Przechowujemy referencję do naszego zadania, aby móc je anulować podczas przeładowania
    private BukkitTask borderTask;

    @Override
    public void onEnable() {
        // Rejestracja komendy
        this.getCommand("borderparticles").setExecutor(new BorderParticlesCommand(this));

        // Wczytanie konfiguracji i uruchomienie zadania
        loadConfigAndStartTask();
        getLogger().info("BorderParticles enabled!");
    }

    @Override
    public void onDisable() {
        // Zatrzymujemy zadanie, gdy plugin jest wyłączany, aby uniknąć błędów
        if (borderTask != null && !borderTask.isCancelled()) {
            borderTask.cancel();
        }
        getLogger().info("BorderParticles disabled!");
    }

    /**
     * Centralna metoda do ładowania konfiguracji i restartowania zadania.
     * Używana w onEnable() i komendzie reload.
     */
    public void loadConfigAndStartTask() {
        // Anuluj stare zadanie, jeśli istnieje
        if (borderTask != null && !borderTask.isCancelled()) {
            borderTask.cancel();
        }

        // Zapisuje domyślny config.yml, jeśli nie istnieje
        saveDefaultConfig();
        // Wymusza ponowne wczytanie wartości z pliku
        reloadConfig();

        FileConfiguration config = getConfig();
        worldName = config.getString("world", "world");
        spawnX = config.getInt("spawn.x", 0);
        spawnZ = config.getInt("spawn.z", 0);
        radius = config.getInt("radius", 500);
        // Pobieramy interwał w sekundach i od razu konwertujemy na ticki serwera (20 ticków = 1 sekunda)
        refreshIntervalTicks = config.getLong("refreshIntervalSeconds", 5) * 20L;
        // Dodajemy nową opcję - dystans, w jakim gracz widzi cząsteczki
        particleViewDistance = config.getInt("particleViewDistance", 100);
        
        // Tworzymy opcje cząsteczek raz, aby nie tworzyć nowego obiektu w pętli
        this.dustOptions = new Particle.DustOptions(Color.LIME, 1.0F);

        // Uruchamiamy nowe zadanie z nową konfiguracją
        startBorderTask();
        getLogger().info("Configuration reloaded and border task restarted.");
    }

    private void startBorderTask() {
        // Pobranie świata powinno odbyć się raz, na początku zadania
        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().severe("World '" + worldName + "' not found! The particle border will not be displayed.");
            return;
        }

        // Używamy finalnej zmiennej dla dystansu do kwadratu, aby uniknąć obliczeń w pętli
        final int viewDistanceSquared = particleViewDistance * particleViewDistance;

        // Uruchamiamy zadanie i przechowujemy jego referencję
        borderTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int minX = spawnX - radius;
            int maxX = spawnX + radius;
            int minZ = spawnZ - radius;
            int maxZ = spawnZ + radius;

            // Iterujemy po wszystkich graczach
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Sprawdzamy, czy gracz jest w odpowiednim świecie
                if (!player.getWorld().equals(world)) {
                    continue;
                }

                Location playerLoc = player.getLocation();
                
                // --- Pętla dla ścian równoległych do osi X (północna i południowa) ---
                for (int x = minX; x <= maxX; x += 2) {
                    // Sprawdzamy ścianę południową (większe Z)
                    spawnParticlesIfClose(player, playerLoc, world, x, maxZ, viewDistanceSquared);
                    // Sprawdzamy ścianę północną (mniejsze Z)
                    spawnParticlesIfClose(player, playerLoc, world, x, minZ, viewDistanceSquared);
                }

                // --- Pętla dla ścian równoległych do osi Z (wschodnia i zachodnia) ---
                for (int z = minZ; z <= maxZ; z += 2) {
                    // Sprawdzamy ścianę wschodnią (większe X)
                    spawnParticlesIfClose(player, playerLoc, world, maxX, z, viewDistanceSquared);
                    // Sprawdzamy ścianę zachodnią (mniejsze X)
                    spawnParticlesIfClose(player, playerLoc, world, minX, z, viewDistanceSquared);
                }
            }
        }, 0L, refreshIntervalTicks);
    }
    
    /**
     * Pomocnicza metoda do tworzenia cząsteczek w danym punkcie, jeśli jest on wystarczająco blisko gracza.
     * @param player Gracz, dla którego tworzymy cząsteczki
     * @param playerLoc Lokalizacja gracza (przekazana dla wydajności)
     * @param world Świat, w którym ma się pojawić cząsteczka
     * @param x Współrzędna X cząsteczki
     * @param z Współrzędna Z cząsteczki
     * @param viewDistanceSquared Kwadrat dystansu widzenia cząsteczek
     */
    private void spawnParticlesIfClose(Player player, Location playerLoc, World world, int x, int z, int viewDistanceSquared) {
        // Tworzymy tymczasową lokalizację dla punktu na granicy na wysokości gracza
        // Używamy double'ów, aby distanceSquared było dokładniejsze
        double particleX = x + 0.5;
        double particleZ = z + 0.5;

        // Prosty trick na sprawdzenie dystansu bez użycia wolnej funkcji pierwiastka kwadratowego
        // Sprawdzamy tylko w 2D (X, Z), bo wysokość Y nie ma znaczenia dla odległości na mapie
        double dx = playerLoc.getX() - particleX;
        double dz = playerLoc.getZ() - particleZ;

        if ((dx * dx + dz * dz) < viewDistanceSquared) {
            // Jeśli punkt jest w zasięgu, tworzymy pionową linię cząsteczek wokół gracza
            // aby stworzyć efekt "ściany"
            int playerY = playerLoc.getBlockY();
            for (int yOffset = -1; yOffset <= 2; yOffset++) {
                // POPRAWKA: Zmieniono Particle.REDSTONE na Particle.DUST
                player.spawnParticle(Particle.DUST, particleX, playerY + yOffset, particleZ, 0, dustOptions);
            }
        }
    }
    
    /**
     * Klasa obsługująca komendy dla pluginu.
     */
    public static class BorderParticlesCommand implements CommandExecutor {

        private final BorderParticles plugin;

        // Przekazujemy instancję głównej klasy pluginu, aby mieć dostęp do jej metod
        public BorderParticlesCommand(BorderParticles plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                // Sprawdzamy uprawnienia
                if (!sender.hasPermission("borderparticles.reload")) {
                    sender.sendMessage(Color.RED + "You don't have permission to use this command.");
                    return true;
                }
                
                // Wywołujemy metodę przeładowującą z głównej klasy
                plugin.loadConfigAndStartTask();
                sender.sendMessage(Color.GREEN + "BorderParticles configuration has been reloaded.");
                return true;
            }

            sender.sendMessage(Color.YELLOW + "Usage: /borderparticles reload");
            return true;
        }
    }
}
