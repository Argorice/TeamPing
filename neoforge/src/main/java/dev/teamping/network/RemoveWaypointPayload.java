package dev.teamping.network;

import dev.teamping.TeamPing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** C2S. Просьба снять свой вейпоинт. Владельца сервер проверяет сам. */
public record RemoveWaypointPayload(UUID pingId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveWaypointPayload> TYPE =
            new CustomPacketPayload.Type<>(TeamPing.id("remove_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveWaypointPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUUID(payload.pingId),
                    buf -> new RemoveWaypointPayload(buf.readUUID())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
