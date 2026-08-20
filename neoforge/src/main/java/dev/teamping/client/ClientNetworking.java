package dev.teamping.client;

import dev.teamping.TeamPing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Отправка на сервер с проверкой канала.
 *
 * <p>NeoForge валидирует пакеты симметрично: попытка отправить незарегистрированный
 * у собеседника payload бросает {@code UnsupportedOperationException}. Регистратор
 * помечен {@code optional()}, значит клиент с модом спокойно заходит на сервер без него —
 * и первое же нажатие клавиши уронило бы игру прямо из тика. Поэтому здесь проверка,
 * а игроку один раз за подключение объясняем, почему пинги не работают.
 */
public final class ClientNetworking {
    private static boolean warned = false;

    private ClientNetworking() {
    }

    /** Вызывать при входе в мир — иначе предупреждение покажется только однажды за сессию. */
    public static void reset() {
        warned = false;
    }

    public static void send(CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();

        if (connection == null || !connection.hasChannel(payload.type())) {
            TeamPing.logOnce("send-no-channel",
                    "Cannot send {}: the server has no TeamPing channel", payload.type().id());
            warnOnce(minecraft);
            return;
        }

        TeamPing.logOnce("send-ok", "Sent {} to the server", payload.type().id());
        PacketDistributor.sendToServer(payload);
    }

    /**
     * То же, но молча: нечего показывать «мод не установлен на сервере» из-за
     * фоновой синхронизации настройки, о которой игрок не просил.
     */
    public static void sendQuietly(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(payload.type())) {
            return;
        }
        PacketDistributor.sendToServer(payload);
    }

    private static void warnOnce(Minecraft minecraft) {
        if (warned || minecraft.gui == null) {
            return;
        }
        warned = true;
        minecraft.gui.setOverlayMessage(Component.translatable("teamping.notice.no_server_mod"), false);
    }
}
