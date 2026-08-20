package dev.teamping.client;

import dev.teamping.network.RemoveWaypointPayload;
import dev.teamping.ping.Ping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Список вейпоинтов: сначала свои, с кнопкой «снять», следом командные — их видно,
 * но убрать может только автор. Внизу легенда управления: комбинации забываются,
 * а лезть за ними в настройки посреди игры никто не станет.
 */
public class WaypointScreen extends Screen {
    private static final int LIST_TOP = 40;
    private static final int ROW_HEIGHT = 22;
    /** Место под кнопку «Готово» и две строки легенды. */
    private static final int BOTTOM_RESERVE = 62;

    private int hidden = 0;

    public WaypointScreen() {
        super(Component.translatable("teamping.screen.waypoints"));
    }

    @Override
    protected void init() {
        List<Ping> waypoints = PingStore.allWaypoints();
        UUID self = self();

        int listBottom = this.height - BOTTOM_RESERVE;
        int rows = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        int shown = Math.min(rows, waypoints.size());
        this.hidden = waypoints.size() - shown;

        for (int i = 0; i < shown; i++) {
            Ping waypoint = waypoints.get(i);
            addRenderableWidget(rowFor(waypoint, waypoint.ownerId().equals(self),
                    LIST_TOP + i * ROW_HEIGHT));
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 56, 200, 20)
                .build());
    }

    private Button rowFor(Ping waypoint, boolean own, int y) {
        Component label = own
                ? Component.translatable("teamping.screen.remove", describe(waypoint))
                : Component.translatable("teamping.screen.teammate",
                        describe(waypoint), waypoint.ownerName());

        Button button = Button.builder(label, pressed -> {
                    ClientNetworking.send(new RemoveWaypointPayload(waypoint.id()));
                    PingStore.remove(waypoint.id());
                    rebuildWidgets();
                })
                .bounds(this.width / 2 - 130, y, 260, 20)
                .build();

        // Чужой вейпоинт показываем, но снять его нельзя — он не твой.
        button.active = own;
        return button;
    }

    private static UUID self() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? new UUID(0L, 0L) : minecraft.player.getUUID();
    }

    private static Component describe(Ping waypoint) {
        return Component.translatable(
                "teamping.screen.entry",
                (int) waypoint.position().x,
                (int) waypoint.position().y,
                (int) waypoint.position().z);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centre = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centre, 16, 0xFFFFFFFF);

        if (PingStore.allWaypoints().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("teamping.screen.empty"), centre, 52, 0xFFAAAAAA);
            graphics.drawCenteredString(this.font,
                    Component.translatable("teamping.screen.empty.hint"), centre, 66, 0xFF888888);
        } else if (this.hidden > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("teamping.screen.more", this.hidden),
                    centre, this.height - 70, 0xFF888888);
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("teamping.screen.legend.1",
                        PingKeybinds.pingHint(), PingKeybinds.dangerHint()),
                centre, this.height - 30, 0xFF909090);
        graphics.drawCenteredString(this.font,
                Component.translatable("teamping.screen.legend.2",
                        PingKeybinds.waypointHint(), PingKeybinds.removeHint()),
                centre, this.height - 19, 0xFF909090);
    }
}
