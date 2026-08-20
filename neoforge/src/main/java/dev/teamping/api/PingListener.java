package dev.teamping.api;

import dev.teamping.ping.Ping;

import java.util.UUID;

/**
 * Listener for the client-side ping store — TeamPing's public extension point.
 *
 * <p>Through it, another mod learns about every ping the player can see and can render
 * them however it likes: on its own map, in a HUD, in a radio interface. No team
 * filtering is needed — pings from other teams never reach the client, the server
 * drops them earlier.
 *
 * <p>All methods are called on the client main thread. Every method has an empty
 * default, so override only what you need.
 *
 * <p>Register with {@link TeamPingClientApi#registerListener(PingListener)}. The mod's
 * own Xaero map integration is wired the exact same way — there is no private path
 * around this interface.
 */
public interface PingListener {

    /** The player can now see a new ping. The same ping may arrive again as an update. */
    default void onPingAdded(Ping ping) {
    }

    /** A ping is gone: TTL expired, evicted by the cap, or removed by its owner. */
    default void onPingRemoved(UUID pingId) {
    }

    /** The store was wiped: dimension change or leaving the world. */
    default void onPingsCleared() {
    }
}
