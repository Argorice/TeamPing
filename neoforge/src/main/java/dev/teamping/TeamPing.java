package dev.teamping;

import com.mojang.logging.LogUtils;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.network.TeamPingNetwork;
import dev.teamping.ping.ServerPingManager;
import dev.teamping.ping.WaypointSync;
import dev.teamping.team.TeamProviders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Точка входа. Всё, что нужно и клиенту, и серверу.
 * Клиентская часть — в {@code dev.teamping.client.TeamPingClient}, она помечена
 * {@code dist = Dist.CLIENT} и на выделенном сервере не грузится вообще.
 */
@Mod(TeamPing.MOD_ID)
public final class TeamPing {
    public static final String MOD_ID = "teamping";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> LOGGED_ONCE = ConcurrentHashMap.newKeySet();

    public TeamPing(IEventBus modBus, ModContainer container) {
        TeamPingConfig.loadServer();

        modBus.addListener(TeamPingNetwork::register);

        NeoForge.EVENT_BUS.addListener(TeamPing::onServerStarting);
        NeoForge.EVENT_BUS.addListener(TeamPing::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(TeamPing::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(TeamPing::onServerTick);

        LOGGER.info("TeamPing loaded");
    }

    /**
     * Logs a line the first time it happens and never again.
     *
     * <p>Used to trace the path of a ping through the mod without turning the log into a
     * waterfall: one line when a ping is first sent, one when the first one comes back,
     * one for each reason the server has to drop one. If a player reports that nothing
     * happens, their log says exactly where the chain stops.
     */
    public static void logOnce(String key, String message, Object... args) {
        if (LOGGED_ONCE.add(key)) {
            LOGGER.info(message, args);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        TeamProviders.init();
        ServerPingManager.reset();
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaypointSync.onPlayerJoin(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        WaypointSync.tick(event.getServer());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerPingManager.onPlayerQuit(player);
        }
    }
}
