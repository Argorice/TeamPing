package dev.teamping.network;

import dev.teamping.TeamPing;
import dev.teamping.ping.Ping;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C. Готовый пинг, собранный на сервере. */
public record PingBroadcastPayload(Ping ping) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PingBroadcastPayload> TYPE =
            new CustomPacketPayload.Type<>(TeamPing.id("ping_broadcast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PingBroadcastPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> Ping.STREAM_CODEC.encode(buf, payload.ping),
                    buf -> new PingBroadcastPayload(Ping.STREAM_CODEC.decode(buf))
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
