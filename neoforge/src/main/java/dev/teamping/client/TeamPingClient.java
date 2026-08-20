package dev.teamping.client;

import dev.teamping.TeamPing;
import dev.teamping.client.map.MapIntegrations;
import dev.teamping.config.TeamPingConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Клиентская точка входа. Помечена {@code dist = Dist.CLIENT} — на выделенном
 * сервере этот класс и всё, до чего он дотягивается, не грузится.
 */
@Mod(value = TeamPing.MOD_ID, dist = Dist.CLIENT)
public final class TeamPingClient {

    public TeamPingClient(IEventBus modBus, ModContainer container) {
        TeamPingConfig.loadClient();

        modBus.addListener(TeamPingClient::onRegisterKeyMappings);
        modBus.addListener(TeamPingClient::onClientSetup);

        NeoForge.EVENT_BUS.addListener(TeamPingClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(TeamPingClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(TeamPingClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(TeamPingClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(TeamPingClient::onLoggingOut);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (net.minecraft.client.KeyMapping mapping : PingKeybinds.ALL) {
            event.register(mapping);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        PingStore.tick(minecraft);
        PingKeybinds.tick(minecraft);
        NearbySharingClient.tick();
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        PingRenderer.render(event.getPoseStack(), event.getCamera(), event.getProjectionMatrix());
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        PingHud.render(event.getGuiGraphics());
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientNetworking.reset();
        NearbySharingClient.onLoggingIn();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Карты ищем после загрузки всех модов, иначе их классов может ещё не быть.
        MapIntegrations.init();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PingStore.onDisconnect();
    }
}
