package dev.teamping.api;

import dev.teamping.network.PlacePingPayload;
import dev.teamping.ping.PingType;
import dev.teamping.ping.ServerPingManager;
import dev.teamping.team.TeamProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Server side of TeamPing's public API.
 *
 * <p>Useful when your mod wants to place pings itself: a turret spotted a target,
 * a scanner found ore, a drone reached a waypoint. The ping goes through exactly the
 * same validation as a player pressing the key, rate limit and distance included.
 */
public final class TeamPingServerApi {
    private TeamPingServerApi() {
    }

    /**
     * Place a ping on behalf of a player. Owner, colour and delivery are handled by the
     * server. There is nothing to return — an invalid ping is dropped silently, like any
     * other.
     */
    public static void placePing(ServerPlayer sender, Vec3 position, PingType type,
                                 @Nullable BlockPos targetBlock) {
        ServerPingManager.handlePlace(sender,
                new PlacePingPayload(position, type, Optional.ofNullable(targetBlock),
                        PlacePingPayload.NO_ENTITY));
    }

    /** Who will see this player's pings: their teammates, including themselves. */
    public static Collection<ServerPlayer> teammates(ServerPlayer player) {
        return TeamProviders.get().teammates(player);
    }

    public static boolean sameTeam(ServerPlayer a, ServerPlayer b) {
        return TeamProviders.get().isSameTeam(a, b);
    }

    /** Team colour as RGB without alpha. Sourced from FTB Teams, the scoreboard, or a name hash. */
    public static int teamColor(ServerPlayer player) {
        return TeamProviders.get().teamColor(player);
    }

    /** How the mod resolved teams on this server — for a log line or for debugging. */
    public static String teamProviderName() {
        return TeamProviders.get().describe();
    }
}
