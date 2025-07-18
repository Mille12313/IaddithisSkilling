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
        getCommand("skillsmenu").setExecutor(new SkillsGuiCommand()); // GUI COMMAND!

        getLogger().info("IaddithisSkilling enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
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
