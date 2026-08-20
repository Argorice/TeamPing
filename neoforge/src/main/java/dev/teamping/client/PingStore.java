package dev.teamping.client;

import dev.teamping.TeamPing;
import dev.teamping.api.PingListener;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.ping.Ping;
import dev.teamping.ping.PingType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Клиентское хранилище пингов. Чистится по TTL раз в тик,
 * полностью — при смене измерения и выходе из мира.
 *
 * <p>Все изменения разъезжаются по {@link PingListener}: на этом же механизме
 * держится интеграция с картами и любой чужой мод. Слушателей зовём строго
 * вне блокировки — иначе чужой код смог бы подвесить хранилище.
 */
public final class PingStore {
    private static final int MAX_PINGS = 32;

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<UUID, Ping> PINGS = new LinkedHashMap<>();
    private static final CopyOnWriteArrayList<PingListener> LISTENERS = new CopyOnWriteArrayList<>();

    @Nullable
    private static ResourceKey<Level> lastDimension = null;

    private PingStore() {
    }

    // --- слушатели ---------------------------------------------------------

    public static void addListener(PingListener listener) {
        if (LISTENERS.addIfAbsent(listener)) {
            // Догоняем подписавшегося поздно, чтобы он не пропустил живые пинги.
            for (Ping ping : snapshot()) {
                safely(() -> listener.onPingAdded(ping));
            }
        }
    }

    public static void removeListener(PingListener listener) {
        LISTENERS.remove(listener);
    }

    private static void fireAdded(Ping ping) {
        for (PingListener listener : LISTENERS) {
            safely(() -> listener.onPingAdded(ping));
        }
    }

    private static void fireRemoved(UUID id) {
        for (PingListener listener : LISTENERS) {
            safely(() -> listener.onPingRemoved(id));
        }
    }

    private static void fireCleared() {
        for (PingListener listener : LISTENERS) {
            safely(listener::onPingsCleared);
        }
    }

    /** Чужой слушатель не должен уронить мод: логируем и живём дальше. */
    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            TeamPing.LOGGER.warn("A ping listener threw an exception", t);
        }
    }

    // --- хранилище ---------------------------------------------------------

    public static void add(Ping ping) {
        List<UUID> dropped = new ArrayList<>();
        synchronized (LOCK) {
            PINGS.remove(ping.id());
            PINGS.put(ping.id(), ping);
            while (countTemporary() > MAX_PINGS) {
                UUID victim = pickVictim();
                if (victim == null) {
                    break;
                }
                PINGS.remove(victim);
                dropped.add(victim);
            }
        }
        for (UUID id : dropped) {
            fireRemoved(id);
        }
        fireAdded(ping);
    }

    /**
     * Лимит считается только по временным пингам, и вытесняются тоже только они.
     *
     * <p>Раньше потолок был общим, и вейпоинты вылетали из него молча: восемь точек
     * на игрока — в команде из пяти это уже сорок, больше лимита. Сервер о такой
     * пропаже не знает и прислать точку заново не догадается до следующего входа,
     * так что игрок просто терял вейпоинты. Их и так ограничивает сервер, здесь
     * ограничивать нечего.
     */
    private static int countTemporary() {
        int count = 0;
        for (Ping ping : PINGS.values()) {
            if (ping.type() != PingType.WAYPOINT) {
                count++;
            }
        }
        return count;
    }

    /** Вытесняем самый старый временный пинг; вейпоинты не трогаем никогда. */
    @Nullable
    private static UUID pickVictim() {
        for (Map.Entry<UUID, Ping> entry : PINGS.entrySet()) {
            if (entry.getValue().type() != PingType.WAYPOINT) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void remove(UUID id) {
        boolean removed;
        synchronized (LOCK) {
            removed = PINGS.remove(id) != null;
        }
        if (removed) {
            fireRemoved(id);
        }
    }

    public static void clear() {
        boolean hadAnything;
        synchronized (LOCK) {
            hadAnything = !PINGS.isEmpty();
            PINGS.clear();
        }
        if (hadAnything) {
            fireCleared();
        }
    }

    public static List<Ping> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(PINGS.values());
        }
    }

    /** То, что реально нужно рисовать здесь и сейчас. */
    public static List<Ping> visible() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return List.of();
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        boolean hideOwn = TeamPingConfig.client().hideOwnPings;
        UUID self = minecraft.player.getUUID();

        List<Ping> result = new ArrayList<>();
        for (Ping ping : snapshot()) {
            if (!ping.dimension().equals(dimension)) {
                continue;
            }
            if (hideOwn && ping.ownerId().equals(self)) {
                continue;
            }
            result.add(ping);
        }
        return result;
    }

    /**
     * Все вейпоинты, которые игроку сейчас видны: свои и командные, свои первыми.
     * Чужие в этот список попадают потому, что вейпоинт принадлежит и команде тоже —
     * значит и в списке ему место, пусть и без кнопки «снять».
     */
    public static List<Ping> allWaypoints() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of();
        }
        UUID self = minecraft.player.getUUID();

        List<Ping> result = new ArrayList<>();
        for (Ping ping : snapshot()) {
            if (ping.type() == PingType.WAYPOINT) {
                result.add(ping);
            }
        }
        result.sort(Comparator.comparingInt((Ping ping) -> ping.ownerId().equals(self) ? 0 : 1)
                .thenComparing(Ping::ownerName));
        return result;
    }

    public static List<Ping> ownWaypoints() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of();
        }
        UUID self = minecraft.player.getUUID();
        List<Ping> result = new ArrayList<>();
        for (Ping ping : snapshot()) {
            if (ping.type() == PingType.WAYPOINT && ping.ownerId().equals(self)) {
                result.add(ping);
            }
        }
        return result;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            if (lastDimension != null) {
                lastDimension = null;
                clear();
            }
            return;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            // Вейпоинты живут на сервере и приходят по владению — их не трогаем.
            // Гаснут только обычные пинги: они привязаны к моменту и к месту.
            dropWhere(ping -> ping.type() != PingType.WAYPOINT);
            return;
        }

        long now = System.currentTimeMillis();
        dropWhere(ping -> ping.expired(now));
    }

    /** Выкидывает всё, что подходит под условие, и сообщает об этом слушателям. */
    private static void dropWhere(Predicate<Ping> condition) {
        List<UUID> removed = new ArrayList<>();
        synchronized (LOCK) {
            PINGS.values().removeIf(ping -> {
                if (condition.test(ping)) {
                    removed.add(ping.id());
                    return true;
                }
                return false;
            });
        }
        for (UUID id : removed) {
            fireRemoved(id);
        }
    }

    /** Выход из мира: следующий заход начнётся с чистого листа и свежей синхронизации. */
    public static void onDisconnect() {
        lastDimension = null;
        clear();
    }
}
