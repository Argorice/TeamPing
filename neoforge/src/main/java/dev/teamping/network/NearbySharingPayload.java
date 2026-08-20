package dev.teamping.network;

import dev.teamping.TeamPing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S. «Когда команды нет, показывать мои метки всем, кто рядом» — да или нет.
 *
 * <p>Настройка отправителя, а не получателя: кто увидит мою метку, решаю я, и
 * чужая галочка на это не влияет. Шлётся при входе в мир и при каждом переключении.
 */
public record NearbySharingPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NearbySharingPayload> TYPE =
            new CustomPacketPayload.Type<>(TeamPing.id("nearby_sharing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NearbySharingPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.enabled),
                    buf -> new NearbySharingPayload(buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
