package dev.teamping.network;

import dev.teamping.TeamPing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** S2C. Снять маркер досрочно — вейпоинты сами не гаснут. */
public record RemovePingPayload(UUID pingId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemovePingPayload> TYPE =
            new CustomPacketPayload.Type<>(TeamPing.id("remove_ping"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemovePingPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUUID(payload.pingId),
                    buf -> new RemovePingPayload(buf.readUUID())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
