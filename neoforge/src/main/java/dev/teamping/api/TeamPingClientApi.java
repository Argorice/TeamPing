package dev.teamping.api;

import dev.teamping.client.ClientNetworking;
import dev.teamping.client.PingStore;
import dev.teamping.network.PlacePingPayload;
import dev.teamping.ping.Ping;
import dev.teamping.ping.PingType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Client side of TeamPing's public API.
 *
 * <p><b>Client only.</b> Never touch this class on a dedicated server — it pulls in
 * client-side classes.
 *
 * <p>Example: draw other players' pings on your own map.
 * <pre>{@code
 * TeamPingClientApi.registerListener(new PingListener() {
 *     @Override public void onPingAdded(Ping ping) { myMap.addMarker(ping); }
 *     @Override public void onPingRemoved(UUID id) { myMap.removeMarker(id); }
 *     @Override public void onPingsCleared()       { myMap.clearMarkers(); }
 * });
 * }</pre>
 */
public final class TeamPingClientApi {
    private TeamPingClientApi() {
    }

    /**
     * Subscribe to pings. The listener immediately receives {@code onPingAdded} for every
     * ping that is already live, so subscribing late can never leave it out of sync.
     */
    public static void registerListener(PingListener listener) {
        PingStore.addListener(listener);
    }

    public static void unregisterListener(PingListener listener) {
        PingStore.removeListener(listener);
    }

    /** Snapshot of every ping the player can see right now, across all dimensions. */
    public static List<Ping> activePings() {
        return PingStore.snapshot();
    }

    /** Only those in the current dimension and not hidden by the player's settings. */
    public static List<Ping> visiblePings() {
        return PingStore.visible();
    }

    /** Waypoints placed by this player. */
    public static List<Ping> ownWaypoints() {
        return PingStore.ownWaypoints();
    }

    /**
     * Ask the server to place a ping. The server checks distance, rate limit and dimension
     * and decides who gets to see it — so this call can neither be used to spam nor to
     * reach another team.
     *
     * <p>If the server doesn't have the mod, the call does nothing.
     */
    public static void placePing(Vec3 position, PingType type, @Nullable BlockPos targetBlock) {
        ClientNetworking.send(new PlacePingPayload(position, type, Optional.ofNullable(targetBlock),
                PlacePingPayload.NO_ENTITY));
    }
}
