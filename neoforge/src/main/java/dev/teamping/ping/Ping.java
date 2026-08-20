package dev.teamping.ping;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Единица данных мода. Собирается целиком на сервере — клиент не может
 * подделать ни владельца, ни цвет, ни подпись.
 *
 * @param createdAt системное время; на сервере — серверное, на клиенте
 *                  перезаписывается локальным при приёме (см. {@link #withLocalClock(long)}),
 *                  чтобы TTL не зависел от расхождения часов.
 */
public record Ping(
        UUID id,
        UUID ownerId,
        String ownerName,
        ResourceKey<Level> dimension,
        Vec3 position,
        PingType type,
        @Nullable Component label,
        int color,
        long createdAt,
        long lifetimeMs
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, Ping> STREAM_CODEC =
            StreamCodec.of(Ping::encode, Ping::decode);

    private static void encode(RegistryFriendlyByteBuf buf, Ping ping) {
        buf.writeUUID(ping.id);
        buf.writeUUID(ping.ownerId);
        buf.writeUtf(ping.ownerName, 64);
        buf.writeResourceKey(ping.dimension);
        buf.writeDouble(ping.position.x);
        buf.writeDouble(ping.position.y);
        buf.writeDouble(ping.position.z);
        buf.writeEnum(ping.type);
        buf.writeBoolean(ping.label != null);
        if (ping.label != null) {
            ComponentSerialization.STREAM_CODEC.encode(buf, ping.label);
        }
        buf.writeInt(ping.color);
        buf.writeLong(ping.createdAt);
        buf.writeLong(ping.lifetimeMs);
    }

    private static Ping decode(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        UUID ownerId = buf.readUUID();
        String ownerName = buf.readUtf(64);
        ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        PingType type = buf.readEnum(PingType.class);
        Component label = buf.readBoolean() ? ComponentSerialization.STREAM_CODEC.decode(buf) : null;
        int color = buf.readInt();
        long createdAt = buf.readLong();
        long lifetimeMs = buf.readLong();
        return new Ping(id, ownerId, ownerName, dimension, position, type, label, color, createdAt, lifetimeMs);
    }

    /** Пересаживает пинг на часы получателя — иначе рассинхрон времени ломает TTL. */
    public Ping withLocalClock(long now) {
        return new Ping(id, ownerId, ownerName, dimension, position, type, label, color, now, lifetimeMs);
    }

    public boolean permanent() {
        return lifetimeMs <= 0L;
    }

    public long ageMs(long now) {
        return Math.max(0L, now - createdAt);
    }

    public long remainingMs(long now) {
        if (permanent()) {
            return Long.MAX_VALUE;
        }
        return lifetimeMs - ageMs(now);
    }

    public boolean expired(long now) {
        return !permanent() && remainingMs(now) <= 0L;
    }
}
