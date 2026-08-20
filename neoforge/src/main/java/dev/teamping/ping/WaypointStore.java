package dev.teamping.ping;

import dev.teamping.TeamPing;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вейпоинты живут в сейве мира, рядом с остальными его данными.
 *
 * <p>Раньше их хранил клиент в своём файле, и из-за этого метка переживала выход
 * из команды: сервер про неё попросту не знал и снять не мог. Теперь единственный
 * источник истины — здесь.
 */
public final class WaypointStore extends SavedData {
    private static final String FILE_ID = "teamping_waypoints";

    /** Порядок вставки важен: по нему вытесняется самый старый при переполнении. */
    private final Map<UUID, StoredWaypoint> waypoints = new LinkedHashMap<>();

    public WaypointStore() {
    }

    public static WaypointStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    private static SavedData.Factory<WaypointStore> factory() {
        return new SavedData.Factory<>(WaypointStore::new, WaypointStore::load);
    }

    private static WaypointStore load(CompoundTag tag, HolderLookup.Provider registries) {
        WaypointStore store = new WaypointStore();
        ListTag list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StoredWaypoint waypoint = StoredWaypoint.load(list.getCompound(i));
            if (waypoint != null) {
                store.waypoints.put(waypoint.ping().id(), waypoint);
            } else {
                TeamPing.LOGGER.warn("Skipping a malformed waypoint entry in the world save");
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StoredWaypoint waypoint : waypoints.values()) {
            list.add(waypoint.save());
        }
        tag.put("Waypoints", list);
        return tag;
    }

    public List<StoredWaypoint> all() {
        return new ArrayList<>(waypoints.values());
    }

    @Nullable
    public StoredWaypoint byId(UUID id) {
        return waypoints.get(id);
    }

    /**
     * Кладёт вейпоинт и возвращает те, что пришлось вытеснить: лимит считается
     * по автору, поэтому один активный игрок не съест точки всей команды.
     */
    public List<UUID> add(StoredWaypoint waypoint, int maxPerOwner) {
        List<UUID> evicted = new ArrayList<>();
        UUID owner = waypoint.ping().ownerId();

        while (countFor(owner) >= maxPerOwner) {
            UUID oldest = oldestFor(owner);
            if (oldest == null) {
                break;
            }
            waypoints.remove(oldest);
            evicted.add(oldest);
        }

        waypoints.put(waypoint.ping().id(), waypoint);
        setDirty();
        return evicted;
    }

    public boolean remove(UUID id) {
        if (waypoints.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    private int countFor(UUID owner) {
        int count = 0;
        for (StoredWaypoint waypoint : waypoints.values()) {
            if (waypoint.ping().ownerId().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private UUID oldestFor(UUID owner) {
        for (Map.Entry<UUID, StoredWaypoint> entry : waypoints.entrySet()) {
            if (entry.getValue().ping().ownerId().equals(owner)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
