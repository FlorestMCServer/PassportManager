package ru.florestdev.passportManager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class PassportCommand implements CommandExecutor {
    private final PassportManager plugin;

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

        // Команда: /passport get (Для самого себя)
        if (args.length == 1 && args[0].equalsIgnoreCase("get")) {
            p.getScheduler().run(plugin, (task) -> plugin.giveAllPassports(p, p), null);
            return true;
        }

        return false;
    }
}