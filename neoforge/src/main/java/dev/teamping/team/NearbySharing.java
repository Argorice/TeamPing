package dev.teamping.team;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кто разрешил показывать свои метки всем, кто рядом, когда команды нет.
 *
 * <p>По умолчанию — никто. Без команды метка видна только автору, ровно как вейпоинт:
 * одно правило на весь мод, и на публичном сервере твои метки не разлетаются по округе
 * сами собой. Кому нужно наоборот — включает галочку в меню меток.
 *
 * <p>Хранится в памяти сервера и живёт до выхода игрока. Постоянное место у настройки
 * всё равно есть — клиентский конфиг, откуда она и приезжает при каждом входе.
 */
public final class NearbySharing {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private NearbySharing() {
    }

    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    public static boolean isOn(ServerPlayer player) {
        return ENABLED.contains(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        ENABLED.remove(player.getUUID());
    }

    public static void reset() {
        ENABLED.clear();
    }
}
