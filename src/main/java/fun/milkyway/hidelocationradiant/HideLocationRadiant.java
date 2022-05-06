package fun.milkyway.hidelocationradiant;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.baronessdev.paid.auth.api.events.AuthPlayerLoginEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HideLocationRadiant extends JavaPlugin implements CommandExecutor, Listener {

    private ExecutorService executorService;

    private FileConfiguration configuration;
    private File configFile;
    private Location spawnLocation;

    private FileConfiguration locationsConfigFile;
    private File locationsFile;

    private Set<UUID> authorizedPlayers;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onEnable() {
        authorizedPlayers = new HashSet<>();

        executorService = Executors.newSingleThreadExecutor();
        File dataFolder = getDataFolder();

        if (!dataFolder.exists()) {
            getDataFolder().mkdir();
        }

        locationsFile = new File(dataFolder, "logoutlocations.yml");
        locationsConfigFile = YamlConfiguration.loadConfiguration(locationsFile);

        spawnLocation = null;

        configFile = new File(dataFolder, "config.yml");
        configuration = YamlConfiguration.loadConfiguration(configFile);
        spawnLocation = configuration.getLocation("spawnlocation");

        Objects.requireNonNull(getCommand("hlspawn")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        executorService.execute(() -> {
            try {
                locationsConfigFile.save(locationsFile);
                configuration.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        executorService.shutdown();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!authorizedPlayers.contains(player.getUniqueId())) {
            return;
        }
        Location location = event.getPlayer().getLocation().clone();
        executorService.submit(() -> {
            locationsConfigFile.set(player.getUniqueId().toString(), location);
            try {
                locationsConfigFile.save(locationsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        player.teleport(spawnLocation);
        authorizedPlayers.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAuthorize(AuthPlayerLoginEvent event) {
        Player player = event.getPlayer();
        CompletableFuture
                .supplyAsync(() -> locationsConfigFile.getLocation(player.getUniqueId().toString()), executorService)
                .thenAccept(location -> {
                    if (location != null) {
                        Bukkit.getScheduler().runTaskLater(this, () -> player.teleportAsync(location), 1);
                    }
                });
        authorizedPlayers.add(player.getUniqueId());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        spawnLocation = player.getLocation();
        configuration.set("spawnlocation", spawnLocation);
        try {
            configuration.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        sender.sendMessage("Позиция спавна установлена.");
        return true;
    }
}
