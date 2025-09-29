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

    private File salvageFile;
    private FileConfiguration salvageConfig;

    private HighscoreWebServer webServer;

    @Override
    public void onEnable() {
        instance = this;

        // 1) Load config.yml
        saveDefaultConfig();

        // 2) Load data.yml
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

        // 3) Load salvage.yml
        salvageFile = new File(getDataFolder(), "salvage.yml");
        if (!salvageFile.exists()) {
            salvageFile.getParentFile().mkdirs();
            saveResource("salvage.yml", false);
        }
        salvageConfig = YamlConfiguration.loadConfiguration(salvageFile);

        // 4) SkillManager XP-table init
        try {
            SkillManager.init(getConfig());
        } catch (Throwable t) {
            getLogger().warning("SkillManager.init skipped or failed: " + t.getMessage());
        }

        // 4b) EXP Boost service init (double-XP e.d.)
        try {
            ExpBoostService.init();
        } catch (Throwable t) {
            getLogger().warning("ExpBoostService.init failed: " + t.getMessage());
        }

        // 5) Slayer service (slayer.yml) + GUIs
        SlayerService.init();
        getServer().getPluginManager().registerEvents(new SlayerShopGUI(), this);
        getServer().getPluginManager().registerEvents(new SlayerShopEditorGUI(), this); // editor listener

        // 5b) Slayer kills (MythicMobs only)
        if (getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            getServer().getPluginManager().registerEvents(new SlayerKillListenerMM(), this);
        } else {
            getLogger().info("MythicMobs not found: Slayer tasks will not track progress.");
        }

        // 6) Other listeners (SkillEvents bevat Mythic-bridge via reflection)
        getServer().getPluginManager().registerEvents(new SkillEvents(), this);
        getServer().getPluginManager().registerEvents(new SkillsGuiCommand(), this);
        getServer().getPluginManager().registerEvents(new SalvageCauldronListener(), this);

        // 7) Commands
        if (getCommand("skills") != null)               getCommand("skills").setExecutor(new SkillsCommand());
        if (getCommand("highscore") != null)            getCommand("highscore").setExecutor(new HighscoreCommand());
        if (getCommand("togglenotifications") != null)  getCommand("togglenotifications").setExecutor(new ToggleNotificationCommand());
        if (getCommand("iaddithisskilling") != null)    getCommand("iaddithisskilling").setExecutor(new ReloadCommand());
        if (getCommand("resetskill") != null)           getCommand("resetskill").setExecutor(new SkillResetCommand());
        if (getCommand("skillsmenu") != null)           getCommand("skillsmenu").setExecutor(new SkillsGuiCommand());
        if (getCommand("untangle") != null)             getCommand("untangle").setExecutor(new UntangleCommand());

        // Skill XP command (MythicMobs nodes → directe XP + popup)
        if (getCommand("skillxp") != null)              getCommand("skillxp").setExecutor(new SkillXpCommand());

        // ★ Nieuw: EXP boost command
        if (getCommand("expboost") != null)             getCommand("expboost").setExecutor(new ExpBoostCommand());

        // Slayer shop & points
        if (getCommand("slayershop") != null)           getCommand("slayershop").setExecutor(new SlayerShopCommand());
        if (getCommand("slayerpoints") != null)         getCommand("slayerpoints").setExecutor(new SlayerPointsCommand());

        // Slayer task manager
        if (getCommand("slayer") != null) {
            SlayerCommand slayerCmd = new SlayerCommand();
            getCommand("slayer").setExecutor(slayerCmd);
            getCommand("slayer").setTabCompleter(slayerCmd);
        }

        // Slayer shop editor command
        if (getCommand("slayershopedit") != null)       getCommand("slayershopedit").setExecutor(new SlayerShopEditorCommand());

        // 8) Highscore webserver
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

        getLogger().info("IaddithisSkilling enabled successfully.");
    }

    @Override
    public void onDisable() {
        saveData();

        try {
            ExpBoostService.shutdown();
        } catch (Throwable ignored) {}

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

    public FileConfiguration getSalvageConfig() {
        return salvageConfig;
    }

    public void saveSalvageConfig() {
        try {
            salvageConfig.save(salvageFile);
        } catch (IOException e) {
            getLogger().severe("Could not save salvage.yml!");
            e.printStackTrace();
        }
    }
}
