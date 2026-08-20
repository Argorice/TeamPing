package dev.teamping.ping;

import dev.teamping.TeamPing;
import dev.teamping.config.ServerConfig;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.network.PingBroadcastPayload;
import dev.teamping.network.PlacePingPayload;
import dev.teamping.network.TeamPingNetwork;
import dev.teamping.team.TeamProvider;
import dev.teamping.team.TeamProviders;
import dev.teamping.util.SableSupport;
import dev.teamping.util.TeamPingTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Вся серверная логика пингов. Клиенту здесь не верят ни в чём,
 * кроме координат и намерения — и координаты тоже проверяют.
 *
 * <p>Обычные пинги живут секунды и уходят тем, кто рядом и в команде.
 * Вейпоинты живут в сейве мира и рассылаются по владению — этим занимается
 * {@link WaypointSync}.
 */
public final class ServerPingManager {
    private static final Map<UUID, Long> LAST_PING = new ConcurrentHashMap<>();

    private ServerPingManager() {
    }

    public static void reset() {
        LAST_PING.clear();
        WaypointSync.reset();
    }

    public static void onPlayerQuit(ServerPlayer player) {
        LAST_PING.remove(player.getUUID());
        WaypointSync.onPlayerQuit(player);
    }

    public static void handlePlace(ServerPlayer player, PlacePingPayload payload) {
        ServerConfig config = TeamPingConfig.server();
        long now = System.currentTimeMillis();

        if (!isFinite(payload.position())) {
            TeamPing.logOnce("drop-nan", "Dropped a ping with a non-finite position");
            return;
        }

        // 1. Рейт-лимит — молча дропаем.
        Long last = LAST_PING.get(player.getUUID());
        if (last != null && now - last < config.rateLimitMs) {
            TeamPing.logOnce("drop-rate", "Dropped a ping from {}: rate limit ({} ms)",
                    player.getGameProfile().getName(), config.rateLimitMs);
            return;
        }

        // 2. Что под прицелом. Обязательно до проверки дистанции: попадание в судно
        // Sable приходит в координатах его плота, за тридцать миллионов блоков,
        // и без перевода в мир любой такой пинг умер бы здесь же.
        Target target = resolveTarget(player, payload);
        Vec3 position = target.position();

        // 3. Дистанция — уже по мировой точке.
        double maxDistance = config.maxPingDistance;
        if (player.position().distanceToSqr(position) > maxDistance * maxDistance) {
            TeamPing.logOnce("drop-distance", "Dropped a ping from {}: {} blocks away, limit is {}",
                    player.getGameProfile().getName(),
                    (int) Math.sqrt(player.position().distanceToSqr(position)),
                    (int) maxDistance);
            return;
        }

        LAST_PING.put(player.getUUID(), now);

        // 4. Измерение — берём серверное, а не то, что сказал клиент.
        ResourceKey<Level> dimension = player.level().dimension();

        // 5. Тип и подпись. Вейпоинт остаётся вейпоинтом, даже если наведён на игрока:
        // это осознанное «запомнить точку», а не «отметить цель».
        PingType type = payload.pingType();
        Component label = null;
        if (type != PingType.WAYPOINT && target.type() != null) {
            type = target.type();
            label = target.label();
        }
        if (type == PingType.RESOURCE && label == null) {
            label = resourceLabel(player, payload.targetBlock().orElse(null));
            if (label == null) {
                type = PingType.NORMAL;
            }
        }

        TeamProvider provider = TeamProviders.get();
        int color = type.useTeamColor() ? provider.teamColor(player) : type.defaultColor();

        Ping ping = new Ping(
                UUID.randomUUID(),
                player.getUUID(),
                player.getGameProfile().getName(),
                dimension,
                position,
                type,
                label,
                color,
                now,
                type.lifetimeMs()
        );

        if (type == PingType.WAYPOINT) {
            placeWaypoint(player, ping, provider.teamIds(player), config.maxWaypointsPerPlayer);
            return;
        }

        // 6. Обычный пинг — только сокомандникам в том же измерении.
        broadcast(player, ping);
    }

    /** Что оказалось под прицелом: точка в мире плюс, если повезло, тип и подпись. */
    private record Target(Vec3 position, @Nullable PingType type, @Nullable Component label) {
    }

    /**
     * Разбирает, во что попал игрок: в судно, в сущность или просто в блок.
     *
     * <p>Судно проверяется первым: у Sable попадание в него приходит в координатах
     * плота, за тридцать миллионов блоков, и такую точку нельзя ни показать,
     * ни проверить на дальность, пока не переведёшь обратно в мир.
     */
    private static Target resolveTarget(ServerPlayer player, PlacePingPayload payload) {
        Vec3 position = payload.position();
        SableSupport.ShipHit ship = payload.targetBlock()
                .map(block -> SableSupport.resolve(player.level(), block, position))
                .orElse(null);
        if (ship != null) {
            Component name = ship.name() == null
                    ? Component.translatable("teamping.vessel.unnamed")
                    : Component.literal(ship.name());
            return new Target(ship.worldPosition(), PingType.VESSEL, name);
        }

        Entity entity = payload.targetEntity() == PlacePingPayload.NO_ENTITY
                ? null
                : player.serverLevel().getEntity(payload.targetEntity());
        if (entity != null && entity != player && entity.isAlive()) {
            Vec3 centre = entity.getBoundingBox().getCenter();
            if (entity instanceof ServerPlayer other) {
                // Свой или чужой — знание сервера, клиент об этом не спрашивают.
                boolean ally = TeamProviders.get().isSameTeam(player, other);
                return new Target(centre, ally ? PingType.ALLY : PingType.ENEMY,
                        Component.literal(other.getGameProfile().getName()));
            }
            return new Target(centre, PingType.NORMAL, entity.getName());
        }

        return new Target(position, null, null);
    }

    private static void placeWaypoint(ServerPlayer player, Ping ping, Set<String> teamIds,
                                      int maxPerOwner) {
        StoredWaypoint waypoint = new StoredWaypoint(ping, teamIds);
        WaypointStore store = WaypointStore.get(player.server);

        for (UUID evicted : store.add(waypoint, maxPerOwner)) {
            WaypointSync.revoke(player.server, evicted);
        }
        WaypointSync.announce(player.server, waypoint);

        TeamPing.logOnce("waypoint-placed", "{} placed a waypoint (teams {})",
                ping.ownerName(), teamIds.isEmpty() ? "none" : teamIds);
    }

    /** Снять вейпоинт может только автор: он в списке у себя, у остальных его там нет. */
    public static void handleRemoveWaypoint(ServerPlayer player, UUID waypointId) {
        WaypointStore store = WaypointStore.get(player.server);
        StoredWaypoint waypoint = store.byId(waypointId);
        if (waypoint == null || !waypoint.ping().ownerId().equals(player.getUUID())) {
            return;
        }
        if (store.remove(waypointId)) {
            WaypointSync.revoke(player.server, waypointId);
        }
    }

    private static void broadcast(ServerPlayer sender, Ping ping) {
        PingBroadcastPayload payload = new PingBroadcastPayload(ping);
        Collection<ServerPlayer> targets = receivers(sender, ping.dimension());
        // Ключ включает число получателей: первая рассылка часто уходит одному
        // (команда ещё не собрана), и одной строчки «to 1» на всю сессию мало,
        // чтобы понять, заработали команды позже или нет.
        TeamPing.logOnce("broadcast-" + targets.size(),
                "Broadcasting a {} ping from {} to {} player(s)",
                ping.type(), ping.ownerName(), targets.size());
        for (ServerPlayer target : targets) {
            TeamPingNetwork.sendTo(target, payload);
        }
    }

    private static Collection<ServerPlayer> receivers(ServerPlayer sender, ResourceKey<Level> dimension) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer target : TeamProviders.get().teammates(sender)) {
            if (target.level().dimension().equals(dimension)) {
                result.add(target);
            }
        }
        return result;
    }

    @Nullable
    private static Component resourceLabel(ServerPlayer player, @Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (!pos.closerToCenterThan(player.position(), TeamPingConfig.server().maxPingDistance + 4.0D)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.is(TeamPingTags.ORES)) {
            return null;
        }
        return state.getBlock().getName();
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }
}
