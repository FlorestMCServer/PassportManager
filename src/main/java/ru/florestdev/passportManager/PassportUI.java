package ru.florestdev.passportManager;

import at.pcgamingfreaks.MarriageMaster.API.MarriageMasterPlugin;
import at.pcgamingfreaks.MarriageMaster.API.MarriagePlayer;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

public class PassportUI {

    // Вспомогательный метод для безопасного перехода между окнами на Folia
    private static void openNextStep(Player player, Runnable nextStep) {
        // Даем серверу 1 тик на очистку старого инвентаря
        player.getScheduler().run(PassportManager.getInstance(), (task) -> nextStep.run(), null);
    }

    public static void startSurvey(Player player, String country) {
        askRealName(player, country);
    }

    /* ================== PLAYER SURVEY ================== */

    private static void askRealName(Player player, String country) {
        new AnvilGUI.Builder()
                .title("Ваше реальное имя")
                .itemLeft(createGuiItem("Введите имя"))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    // Folia Fix: Переход на следующий этап через планировщик
                    openNextStep(player, () -> askBirthDate(player, country, state.getText()));
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .plugin(PassportManager.getInstance())
                .open(player);
    }

    private static void askBirthDate(Player player, String country, String realName) {
        new AnvilGUI.Builder()
                .title("Дата рождения (ДД.ММ.ГГГГ)")
                .itemLeft(createGuiItem("Введите дату"))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    openNextStep(player, () -> askAddress(player, country, realName, state.getText()));
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .plugin(PassportManager.getInstance())
                .open(player);
    }

    private static void askAddress(Player player, String country, String realName, String date) {
        new AnvilGUI.Builder()
                .title("Прописка (Город, Улица)")
                .itemLeft(createGuiItem("Введите адрес"))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    String status = getMaritalStatus(player);
                    PassportData data = new PassportData(
                            country,
                            realName,
                            player.getName(),
                            date,
                            state.getText(),
                            status
                    );

                    // Добавление запроса (там уже ConcurrentHashMap, так что безопасно)
                    PassportManager.getInstance().addRequest(player.getUniqueId(), data);
                    player.sendMessage("§aЗаявка отправлена лидерам " + country);
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .plugin(PassportManager.getInstance())
                .open(player);
    }

    /* ================== ADMIN SURVEY (Аналогичные фиксы) ================== */

    public static void startAdminSurvey(Player admin, Player target, String country) {
        new AnvilGUI.Builder()
                .title("Реальное имя")
                .itemLeft(createGuiItem("Введите имя"))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    openNextStep(admin, () -> openAdminBirthDate(admin, target, country, state.getText()));
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .plugin(PassportManager.getInstance())
                .open(admin);
    }

    private static void openAdminBirthDate(Player admin, Player target, String country, String name) {
        new AnvilGUI.Builder()
                .title("Дата рождения")
                .itemLeft(createGuiItem("Введите дату"))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    openNextStep(admin, () -> openAdminAddress(admin, target, country, name, state.getText()));
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .plugin(PassportManager.getInstance())
                .open(admin);
    }

    private static void openAdminAddress(Player admin, Player target, String country, String name, String date) {
        new AnvilGUI.Builder()
                .title("Прописка")
                .itemLeft(createGuiItem("Введите адрес"))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    String status = getMaritalStatus(target);
                    PassportData data = new PassportData(
                            country,
                            name,
                            target.getName(),
                            date,
                            state.getText(),
                            status
                    );

                    // Передаем управление менеджеру (он сам решит вопрос с потоками выдачи)
                    PassportManager.getInstance().forceGivePassport(target, data);
                    admin.sendMessage("§aПаспорт для " + target.getName() + " успешно выдан!");

                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .plugin(PassportManager.getInstance())
                .open(admin);
    }

    /* ================== UTILS & API ================== */

    private static String getMaritalStatus(OfflinePlayer player) {
        if (player == null) return "Неизвестно";
        MarriageMasterPlugin mm = (MarriageMasterPlugin) Bukkit.getPluginManager().getPlugin("MarriageMaster");
        if (mm == null) return "Неизвестно";

        MarriagePlayer mPlayer = mm.getPlayerData(player.getUniqueId());
        return (mPlayer != null && mPlayer.isMarried()) ? "В браке" : "Не в браке";
    }

    private static ItemStack createGuiItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}