package dev.teamping.network;

import dev.teamping.TeamPing;
import dev.teamping.ping.ServerPingManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация пакетов на нативном NeoForge — без прослойки Architectury.
 *
 * <p>Регистратор помечен {@code optional()}: ванильный клиент и клиент без мода
 * спокойно заходят на сервер с модом, просто не получают пингов.
 */
public final class TeamPingNetwork {
    /** Версия протокола. Менять при несовместимых правках пакетов. */
    private static final String PROTOCOL_VERSION = "1";

    private TeamPingNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(
                PlacePingPayload.TYPE,
                PlacePingPayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        context.enqueueWork(() -> ServerPingManager.handlePlace(player, payload));
                    }
                }
        );

        registrar.playToServer(
                RemoveWaypointPayload.TYPE,
                RemoveWaypointPayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        context.enqueueWork(() -> ServerPingManager.handleRemoveWaypoint(player, payload.pingId()));
                    }
                }
        );

        registrar.playToServer(
                NearbySharingPayload.TYPE,
                NearbySharingPayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        context.enqueueWork(() ->
                                dev.teamping.team.NearbySharing.set(player, payload.enabled()));
                    }
                }
        );

        // Тела лямбд — именно лямбды, а не ссылки на методы: тогда клиентский
        // класс грузится в момент первого вызова, то есть только на клиенте.
        registrar.playToClient(
                PingBroadcastPayload.TYPE,
                PingBroadcastPayload.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> dev.teamping.client.ClientPingHandler.onPingReceived(payload.ping()))
        );

        registrar.playToClient(
                RemovePingPayload.TYPE,
                RemovePingPayload.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> dev.teamping.client.ClientPingHandler.onPingRemoved(payload.pingId()))
        );
    }

    /**
     * Отправка с проверкой канала. Без неё NeoForge бросит исключение,
     * если у получателя мода нет.
     */
    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (!player.connection.hasChannel(payload.type())) {
            TeamPing.logOnce("no-channel", "{} has no TeamPing channel, skipping {}",
                    player.getGameProfile().getName(), payload.type().id());
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }
}
