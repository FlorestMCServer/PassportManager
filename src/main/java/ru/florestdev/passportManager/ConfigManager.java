package ru.florestdev.passportManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private final PassportManager plugin;
    private FileConfiguration countriesCfg;
    private final File passportsFile;

    // 1. Используем ConcurrentHashMap — он позволяет потокам не драться за данные
    private Map<UUID, List<PassportData>> database = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager(PassportManager plugin) {
        this.plugin = plugin;
        this.passportsFile = new File(plugin.getDataFolder(), "passports.json");
        reloadCountries();
        loadPassports();
    }

    public void reloadCountries() {
        File f = new File(plugin.getDataFolder(), "countries.yml");
        if (!f.exists()) plugin.saveResource("countries.yml", false);
        countriesCfg = YamlConfiguration.loadConfiguration(f);
    }

    private void loadPassports() {
        if (!passportsFile.exists()) return;
        try (Reader reader = new FileReader(passportsFile)) {
            Map<UUID, List<PassportData>> loaded = gson.fromJson(reader, new TypeToken<Map<UUID, List<PassportData>>>(){}.getType());
            if (loaded != null) database = new ConcurrentHashMap<>(loaded);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // 2. Сохраняем асинхронно через GlobalRegionScheduler
    public void savePassports() {
        // Делаем копию данных для сохранения, чтобы не блокировать базу
        Map<UUID, List<PassportData>> snapshot = new HashMap<>(database);

        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try (Writer writer = new FileWriter(passportsFile)) {
                gson.toJson(snapshot, writer);
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    public List<String> getLeaders(String country) {
        return countriesCfg.getStringList("countries." + country + ".leaders");
    }

    public boolean countryExists(String country) {
        return countriesCfg.contains("countries." + country);
    }

    // Возвращаем ConcurrentMap
    public Map<UUID, List<PassportData>> getDatabase() { return database; }
}