package ru.florestdev.passportManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PassportCommand implements CommandExecutor {
    private final PassportManager plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PassportCommand(PassportManager plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        // Команда: /passport request <country>
        if (args.length == 2 && args[0].equalsIgnoreCase("request")) {
            String country = args[1];
            if (!plugin.getConfigManager().countryExists(country)) {
                p.sendMessage("§cТакой страны не существует в конфиге.");
                return true;
            }
            // Выполняем в планировщике игрока p
            p.getScheduler().run(plugin, (task) -> PassportUI.startSurvey(p, country), null);
            return true;
        }

        // Команда: /passport accept <name>
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                p.sendMessage("§cИгрок оффлайн.");
                return true;
            }

            // На Folia важно: действия с ДВУМЯ игроками лучше координировать.
            // Но обычно достаточно запустить это в потоке того, кто вызывает действие.
            p.getScheduler().run(plugin, (task) -> plugin.acceptPassport(p, target), null);
            return true;
        }

        // Команда: /passport give <target> <country> (Admin Only)
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (!p.hasPermission("passport.admin")) {
                p.sendMessage("§cУ вас нет прав!");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            String country = args[2];

            if (target == null) {
                p.sendMessage("§cИгрок не найден.");
                return true;
            }

            if (!plugin.getConfigManager().countryExists(country)) {
                p.sendMessage("§cСтрана не найдена.");
                return true;
            }

            // Открываем анкету АДМИНУ в его потоке
            p.getScheduler().run(plugin, (task) -> {
                PassportUI.startAdminSurvey(p, target, country);
                p.sendMessage("§eЗаполнение данных для паспорта игрока " + target.getName());
            }, null);
            return true;
        }

        // Команда: /passport get <target> (Admin Only)
        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            if (!p.hasPermission("passport.admin")) {
                p.sendMessage("§cУ вас нет прав!");
                return true;
            }

            // OfflinePlayer безопасен для потоков, так как это просто ссылка на UUID/имя
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                p.sendMessage("§cЭтот игрок никогда не заходил на сервер.");
                return true;
            }

            p.getScheduler().run(plugin, (task) -> plugin.giveAllPassports(p, target), null);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            String country = args[1];

            if (!plugin.getConfigManager().countryExists(country)) {
                sender.sendMessage(ChatColor.RED + "Страны не существует.");
                return true;
            }

            List<String> leaders = plugin.getConfigManager().getLeaders(country);

            if (!leaders.contains(sender.getName())) {
                sender.sendMessage(
                        ChatColor.RED + "Вы не лидер страны %s".formatted(country)
                );
                return true;
            }

            File passportsFile = new File(plugin.getDataFolder(), "passports.json");

            try (Reader reader = new FileReader(passportsFile)) {

                Map<UUID, List<PassportData>> loaded =
                        gson.fromJson(
                                reader,
                                new TypeToken<Map<UUID, List<PassportData>>>() {}.getType()
                        );

                List<PassportData> normal = new ArrayList<>();

                for (List<PassportData> passports : loaded.values()) {
                    for (PassportData passportData : passports) {

                        if (passportData.getCountry().equalsIgnoreCase(country)) {
                            normal.add(passportData);
                        }

                    }
                }

                if (normal.isEmpty()) {
                    sender.sendMessage(
                            ChatColor.RED +
                                    "Страна %s не имеет никаких резидентов.".formatted(country)
                    );
                    return true;
                }

                StringBuilder message = new StringBuilder();
                message.append("Ваши граждане:\n\n");

                int number = 1;

                for (PassportData passportData : normal) {
                    message.append(number++)
                            .append(". ")
                            .append(passportData.getNickname())
                            .append("\n");
                }

                sender.sendMessage(message.toString());
                return true;

            } catch (IOException e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "Не удалось загрузить паспорта.");
                return true;
            }
        }

        // Команда: /passport get (Для самого себя)
        if (args.length == 1 && args[0].equalsIgnoreCase("get")) {
            p.getScheduler().run(plugin, (task) -> plugin.giveAllPassports(p, p), null);
            return true;
        }

        return false;
    }
}