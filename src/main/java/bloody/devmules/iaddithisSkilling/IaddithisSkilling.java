package bloody.devmules.iaddithisSkilling;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class IaddithisSkilling extends JavaPlugin {
    private File dataFile;
    private FileConfiguration dataConfig;
    private static IaddithisSkilling instance;

    private HighscoreWebServer webServer; // <-- Toegevoegd

    @Override
    public void onEnable() {
        instance = this;

        // load default config.yml
        saveDefaultConfig();

        // initialize data.yml
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml!");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Register events (listeners)
        getServer().getPluginManager().registerEvents(new SkillEvents(), this);
        getServer().getPluginManager().registerEvents(new SkillsGuiCommand(), this);

        // Register commands
        getCommand("skills").setExecutor(new SkillsCommand());
        getCommand("highscore").setExecutor(new HighscoreCommand());
        getCommand("togglenotifications").setExecutor(new ToggleNotificationCommand());
        getCommand("iaddithisskilling").setExecutor(new ReloadCommand());
        getCommand("resetskill").setExecutor(new SkillResetCommand());
        getCommand("skillsmenu").setExecutor(new SkillsGuiCommand());
        getCommand("untangle").setExecutor(new UntangleCommand());

        // ---- Start Highscore webserver via config ----
        FileConfiguration cfg = getConfig();
        if (cfg.getBoolean("webserver.enabled", false)) {
            String address = cfg.getString("webserver.bind-address", "0.0.0.0");
            int port = cfg.getInt("webserver.port", 8888);
            try {
                webServer = new HighscoreWebServer(address, port); // <<-- zie aangepaste constructor!
                webServer.start();
                getLogger().info("Highscore webserver started at http://" + address + ":" + port + "/");
            } catch (IOException e) {
                getLogger().severe("Failed to start webserver: " + e.getMessage());
            }
        }

        getLogger().info("IaddithisSkilling enabled.");
    }

    @Override
    public void onDisable() {
        saveData();

        // ---- Stop de webserver netjes ----
        if (webServer != null) {
            webServer.stop();
            getLogger().info("Highscore webserver stopped.");
        }

        getLogger().info("IaddithisSkilling disabled.");
    }

    /** @return singleton instance */
    public static IaddithisSkilling getInstance() {
        return instance;
    }

    /** @return the in‑memory data.yml config */
    public FileConfiguration getData() {
        return dataConfig;
    }

    /** save data.yml to disk */
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml!");
            e.printStackTrace();
        }
    }
}
