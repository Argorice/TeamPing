package dev.teamping.client.map;

import dev.teamping.TeamPing;
import dev.teamping.api.PingListener;
import dev.teamping.api.TeamPingClientApi;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.ping.Ping;
import dev.teamping.ping.PingType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Раскладывает пинги по установленным картам.
 *
 * <p>Подписывается через тот же публичный {@link TeamPingClientApi}, что доступен
 * любому чужому моду, — приватной калитки в обход API нет, и если она однажды
 * сломается, это заметит не только Xaero.
 */
public final class MapIntegrations implements PingListener {
    private static final List<MapIntegration> ACTIVE = new ArrayList<>();

    private MapIntegrations() {
    }

    public static void init() {
        register(XaeroMapIntegration.create());

        if (ACTIVE.isEmpty()) {
            TeamPing.LOGGER.info("No supported map mod found, pings will only show in the world");
            return;
        }
        TeamPingClientApi.registerListener(new MapIntegrations());
    }

    private static void register(MapIntegration integration) {
        if (integration != null) {
            ACTIVE.add(integration);
        }
    }

    private static boolean enabledFor(PingType type) {
        return switch (TeamPingConfig.client().mapMarkers) {
            case "none" -> false;
            case "waypoints" -> type == PingType.WAYPOINT;
            default -> true;
        };
    }

    @Override
    public void onPingAdded(Ping ping) {
        if (!enabledFor(ping.type())) {
            return;
        }
        for (MapIntegration integration : ACTIVE) {
            integration.add(ping);
        }
    }

    @Override
    public void onPingRemoved(UUID pingId) {
        for (MapIntegration integration : ACTIVE) {
            integration.remove(pingId);
        }
    }

    @Override
    public void onPingsCleared() {
        for (MapIntegration integration : ACTIVE) {
            integration.clear();
        }
    }
}
