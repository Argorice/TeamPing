package dev.teamping.client;

import dev.teamping.config.TeamPingConfig;
import dev.teamping.network.NearbySharingPayload;

/**
 * Держит серверную сторону в курсе галочки «без команды — показывать всем рядом».
 *
 * <p>Настройка живёт в клиентском конфиге, то есть переживает перезаход, а сервер
 * помнит её только пока игрок в игре. Поэтому шлём её при каждом входе — но не сразу:
 * канал в момент входа может быть ещё не согласован, и пакет ушёл бы в никуда.
 * Секунда ожидания в тиках дешевле, чем разбираться потом, почему настройка не доехала.
 */
public final class NearbySharingClient {
    private static final int DELAY_TICKS = 20;

    private static int countdown = -1;

    private NearbySharingClient() {
    }

    public static boolean enabled() {
        return TeamPingConfig.client().shareWithNearby;
    }

    /** Переключение из меню: пишем в конфиг и сразу сообщаем серверу. */
    public static void toggle() {
        boolean value = !enabled();
        TeamPingConfig.client().shareWithNearby = value;
        TeamPingConfig.saveClient();
        ClientNetworking.sendQuietly(new NearbySharingPayload(value));
    }

    public static void onLoggingIn() {
        countdown = DELAY_TICKS;
    }

    public static void tick() {
        if (countdown < 0) {
            return;
        }
        if (countdown-- == 0) {
            ClientNetworking.sendQuietly(new NearbySharingPayload(enabled()));
        }
    }
}
