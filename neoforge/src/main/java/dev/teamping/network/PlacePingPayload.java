package dev.teamping.network;

import dev.teamping.TeamPing;
import dev.teamping.ping.PingType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * C2S. Клиент присылает только «куда я смотрю» и «что я имел в виду».
 * Всё остальное сервер добывает сам.
 *
 * <p>Компонент называется {@code pingType}, а не {@code type}: у записи имя
 * компонента задаёт имя аксессора, а {@code type()} уже занят самим
 * {@link CustomPacketPayload}.
 */
public record PlacePingPayload(Vec3 position, PingType pingType, Optional<BlockPos> targetBlock,
                               int targetEntity) implements CustomPacketPayload {

    /** Под прицелом не было сущности. */
    public static final int NO_ENTITY = -1;

    public static final CustomPacketPayload.Type<PlacePingPayload> TYPE =
            new CustomPacketPayload.Type<>(TeamPing.id("place_ping"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlacePingPayload> CODEC =
            StreamCodec.of(PlacePingPayload::encode, PlacePingPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, PlacePingPayload payload) {
        buf.writeDouble(payload.position.x);
        buf.writeDouble(payload.position.y);
        buf.writeDouble(payload.position.z);
        buf.writeEnum(payload.pingType);
        buf.writeBoolean(payload.targetBlock.isPresent());
        payload.targetBlock.ifPresent(buf::writeBlockPos);
        buf.writeInt(payload.targetEntity);
    }

    private static PlacePingPayload decode(RegistryFriendlyByteBuf buf) {
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        PingType type = buf.readEnum(PingType.class);
        Optional<BlockPos> targetBlock = buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
        return new PlacePingPayload(position, type, targetBlock, buf.readInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
