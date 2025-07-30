package bloody.devmules.iaddithisSkilling;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class IaddithisSkilling extends JavaPlugin {
    private static IaddithisSkilling instance;

    private File dataFile;
    private FileConfiguration dataConfig;

    private File inventionFile;
    private FileConfiguration inventionConfig;

    private HighscoreWebServer webServer;

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

        // invention.yml
        inventionFile = new File(getDataFolder(), "invention.yml");
        if (!inventionFile.exists()) {
            inventionFile.getParentFile().mkdirs();
            saveResource("invention.yml", false); // Maakt default aan uit resources
        }
        inventionConfig = YamlConfiguration.loadConfiguration(inventionFile);

        // Register listeners (ALLES registeren dat je had!)
        getServer().getPluginManager().registerEvents(new SkillEvents(), this);
        getServer().getPluginManager().registerEvents(new SkillsGuiCommand(), this);
        getServer().getPluginManager().registerEvents(new SalvageCauldronListener(), this);

        // Register commands (volledig)
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
                webServer = new HighscoreWebServer(address, port);
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

        // Stop webserver netjes
        if (webServer != null) {
            webServer.stop();
            getLogger().info("Highscore webserver stopped.");
        }

        getLogger().info("IaddithisSkilling disabled.");
    }

    public static IaddithisSkilling getInstance() {
        return instance;
    }

    public FileConfiguration getData() {
        return dataConfig;
    }

    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml!");
            e.printStackTrace();
        }
    }

    public FileConfiguration getInventionConfig() {
        return inventionConfig;
    }

    public void saveInventionConfig() {
        try {
            inventionConfig.save(inventionFile);
        } catch (IOException e) {
            getLogger().severe("Could not save invention.yml!");
            e.printStackTrace();
        }
    }
}
