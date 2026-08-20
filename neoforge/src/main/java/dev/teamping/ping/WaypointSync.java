package dev.teamping.ping;

import dev.teamping.TeamPing;
import dev.teamping.network.PingBroadcastPayload;
import dev.teamping.network.RemovePingPayload;
import dev.teamping.network.TeamPingNetwork;
import dev.teamping.team.TeamProviders;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Держит клиентов в курсе, какие вейпоинты им сейчас положено видеть.
 *
 * <p>Смена команды нигде не событие: FTB о ней не сообщает наружу, а ванильный
 * скорборд тем более. Поэтому раз в две секунды сверяем, у кого команда поменялась,
 * и досылаем разницу. Две секунды — компромисс: игрок не успевает заметить задержку,
 * а сервер делает одно сравнение строк на игрока.
 */
public final class WaypointSync {
    private static final int TICKS_BETWEEN_CHECKS = 40;

    private static final Map<UUID, Set<String>> LAST_TEAMS = new ConcurrentHashMap<>();
    private static int ticks = 0;

    private WaypointSync() {
    }

    public static void reset() {
        LAST_TEAMS.clear();
        ticks = 0;
    }

    public static void onPlayerJoin(ServerPlayer player) {
        resync(player);
    }

    public static void onPlayerQuit(ServerPlayer player) {
        LAST_TEAMS.remove(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (++ticks < TICKS_BETWEEN_CHECKS) {
            return;
        }
        ticks = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<String> teams = TeamProviders.get().teamIds(player);
            if (!teams.equals(LAST_TEAMS.get(player.getUUID()))) {
                resync(player);
            }
        }
    }

    /**
     * Пересобирает картину у одного игрока: что положено видеть — досылаем,
     * что больше не положено — снимаем. Слать снятие того, чего у клиента и не было,
     * безвредно: он просто не найдёт такой метки.
     */
    public static void resync(ServerPlayer player) {
        Set<String> teams = TeamProviders.get().teamIds(player);
        LAST_TEAMS.put(player.getUUID(), teams);

        int sent = 0;
        int revoked = 0;
        StringBuilder why = new StringBuilder();
        WaypointStore store = WaypointStore.get(player.server);
        for (StoredWaypoint waypoint : store.all()) {
            String reason = waypoint.visibilityReason(player.getUUID(), teams);
            if (reason != null) {
                TeamPingNetwork.sendTo(player, new PingBroadcastPayload(waypoint.ping()));
                sent++;
                if (!why.isEmpty()) {
                    why.append(", ");
                }
                why.append(waypoint.ping().ownerName()).append(' ').append(reason);
            } else {
                TeamPingNetwork.sendTo(player, new RemovePingPayload(waypoint.ping().id()));
                revoked++;
            }
        }

        // Синхронизация случается только на входе и на смене команды, так что
        // писать её целиком не шумно, а разбираться по ней потом гораздо легче.
        // Причина видимости здесь важнее числа: метка, оставшаяся после выхода
        // из пати FTB, обычно держится за ванильную команду, и это видно только так.
        TeamPing.LOGGER.info("Waypoint sync for {}: teams {}, {} visible [{}], {} hidden",
                player.getGameProfile().getName(),
                teams.isEmpty() ? "none" : teams, sent, why, revoked);
    }

    /** Разослать только что созданный вейпоинт всем, кому он виден. */
    public static void announce(MinecraftServer server, StoredWaypoint waypoint) {
        PingBroadcastPayload payload = new PingBroadcastPayload(waypoint.ping());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (waypoint.visibleTo(player.getUUID(), TeamProviders.get().teamIds(player))) {
                TeamPingNetwork.sendTo(player, payload);
            }
        }
    }

    /**
     * Снять вейпоинт у всех. Шлём всем подряд, а не только тем, кто его видел:
     * список зрителей нигде не хранится, а лишнее снятие для клиента — не операция.
     */
    public static void revoke(MinecraftServer server, UUID waypointId) {
        RemovePingPayload payload = new RemovePingPayload(waypointId);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeamPingNetwork.sendTo(player, payload);
        }
    }
}
