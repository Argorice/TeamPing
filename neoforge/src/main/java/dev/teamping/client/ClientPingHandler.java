package dev.teamping.client;

import dev.teamping.TeamPing;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.ping.Ping;
import dev.teamping.ping.PingType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.UUID;

/**
 * Приём пингов на клиенте.
 *
 * <p>Сознательно ничего не пишем в чат: маркер, звук и короткая строка
 * над хотбаром — этого достаточно, а чат остаётся чатом.
 */
public final class ClientPingHandler {
    private ClientPingHandler() {
    }

    public static void onPingReceived(Ping ping) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        TeamPing.logOnce("receive-" + ping.type(), "Received a {} ping from {}",
                ping.type(), ping.ownerName());

        // Часы сервера и клиента могут разъезжаться — TTL считаем по своим.
        Ping local = ping.withLocalClock(System.currentTimeMillis());
        boolean own = local.ownerId().equals(minecraft.player.getUUID());

        PingStore.add(local);

        if (own && TeamPingConfig.client().hideOwnPings) {
            return;
        }

        PingSounds.play(local.type());
        showNotice(minecraft, local, own);
    }

    public static void onPingRemoved(UUID pingId) {
        PingStore.remove(pingId);
    }

    private static void showNotice(Minecraft minecraft, Ping ping, boolean own) {
        if (!TeamPingConfig.client().showActionbarNotice || minecraft.gui == null) {
            return;
        }

        String kind = ping.type().name().toLowerCase(Locale.ROOT);
        String key = (own ? "teamping.notice.self." : "teamping.notice.other.") + kind;

        Component body = ping.label() == null
                ? Component.translatable(key, ping.ownerName())
                : Component.translatable(key + ".labeled", ping.ownerName(), ping.label());

        minecraft.gui.setOverlayMessage(
                body.copy().setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ping.color()))),
                false
        );
    }
}
