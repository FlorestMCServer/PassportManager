package ru.florestdev.passportManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PassportManager extends JavaPlugin {
    private static PassportManager instance;
    private ConfigManager configManager;

    // 1. Используем ConcurrentHashMap для потокобезопасности заявок
    private final Map<UUID, PassportData> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        getCommand("passport").setExecutor(new PassportCommand(this));
    }

    public void addRequest(UUID uuid, PassportData data) {
        pendingRequests.put(uuid, data);
        List<String> leadersNames = configManager.getLeaders(data.getCountry());

        for (String name : leadersNames) {
            Player leader = Bukkit.getPlayer(name);
            if (leader != null) {
                // 2. Отправка сообщения лидеру в его потоке
                leader.getScheduler().run(this, (task) -> {
                    leader.sendMessage("§6[Паспорт] §fИгрок " + data.getNickname() + " запросил паспорт страны " + data.getCountry());
                    leader.sendMessage("§fИспользуйте §e/passport accept " + data.getNickname());
                }, null);
            }
        }
    }

    public void acceptPassport(Player leader, Player target) {
        // Удаляем заявку атомарно
        PassportData data = pendingRequests.remove(target.getUniqueId());
        if (data == null) {
            leader.sendMessage("§cЗаявок от этого игрока нет.");
            return;
        }

        // 3. Сохранение данных (ConfigManager уже адаптирован под ConcurrentHashMap)
        configManager.getDatabase().computeIfAbsent(target.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>())).add(data);
        configManager.savePassports();

        ItemStack book = createPassportBook(target.getName(), data);

        // 4. ГЛАВНОЕ: Выдача предметов в потоках соответствующих игроков
        // Сначала выдаем лидеру (в текущем потоке, так как метод вызван из PassportCommand в потоке лидера)
        leader.getInventory().addItem(book.clone());
        leader.sendMessage("§aВы выдали паспорт игроку " + target.getName());

        // Теперь выдаем таргету в ЕГО потоке (он может быть на другом конце карты)
        target.getScheduler().run(this, (task) -> {
            target.getInventory().addItem(book);
            target.sendMessage("§aВаш паспорт одобрен!");
        }, null);
    }

    private ItemStack createPassportBook(String ownerName, PassportData data) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta != null) {
            meta.setTitle("Паспорт №" + data.getSerial());
            meta.setAuthor(data.getCountry());

            String page = "§lПАСПОРТ§r\n\n" +
                    "§0Серия: §8" + data.getSerial() + "\n" +
                    "§0Имя: §8" + data.getRealName() + "\n" +
                    "§0Ник: §8" + ownerName + "\n" +
                    "§0Страна: §8" + data.getCountry() + "\n" +
                    "§0Дата рожд: §8" + data.getBirthDate() + "\n" +
                    "§0Прописка: §8" + data.getAddress() + "\n" +
                    "§0Сем. полож: §8" + data.getMaritalStatus();

            meta.setPages(Collections.singletonList(page));
            book.setItemMeta(meta);
        }
        return book;
    }

    public void forceGivePassport(Player target, PassportData data) {
        configManager.getDatabase()
                .computeIfAbsent(target.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(data);
        configManager.savePassports();

        ItemStack book = createPassportBook(target.getName(), data);

        // Выполняем выдачу в потоке таргета
        target.getScheduler().run(this, (task) -> {
            target.getInventory().addItem(book);
            target.sendMessage("§aВам выдан паспорт страны " + data.getCountry());
        }, null);
    }

    public void giveAllPassports(Player admin, OfflinePlayer target) {
        List<PassportData> userPassports = configManager.getDatabase().get(target.getUniqueId());

        if (userPassports == null || userPassports.isEmpty()) {
            admin.sendMessage("§cУ игрока " + target.getName() + " нет паспортов.");
            return;
        }

        // Так как admin — это Player, и метод вызван в его потоке из CommandHandler,
        // мы можем спокойно добавлять предметы в его инвентарь прямо здесь.
        for (PassportData data : userPassports) {
            ItemStack book = createPassportBook(target.getName(), data);
            admin.getInventory().addItem(book);
        }

        admin.sendMessage("§aВы получили копии паспортов (" + userPassports.size() + " шт.) игрока " + target.getName());
    }

    public static PassportManager getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
}