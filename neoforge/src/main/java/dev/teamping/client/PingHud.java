package dev.teamping.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.teamping.TeamPing;
import dev.teamping.config.ClientConfig;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.ping.Ping;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Стрелки по краю экрана для пингов вне поля зрения.
 *
 * <p>Позиция считается через матрицу проекции этого кадра (её ловит
 * {@link PingRenderer}), направление для точек «за спиной» — прямо
 * из координат в пространстве камеры, иначе проекция уводит стрелку не туда.
 * Больше четырёх стрелок по умолчанию не рисуем — край экрана превращается в кашу.
 */
public final class PingHud {
    private static final ResourceLocation ARROW = TeamPing.id("textures/ping/arrow.png");
    private static final int ARROW_TEXTURE_SIZE = 32;
    private static final int ARROW_SIZE = 18;
    private static final float MARGIN = 40.0F;

    private PingHud() {
    }

    private record Arrow(float x, float y, float angle, int color, float alpha, double distance) {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig config = TeamPingConfig.client();

        if (!config.showOffscreenArrows || mc.level == null || mc.player == null
                || mc.options.hideGui || mc.screen != null) {
            return;
        }

        Matrix4f projection = PingRenderer.lastProjection();
        if (projection == null) {
            return;
        }

        List<Ping> pings = PingStore.visible();
        if (pings.isEmpty()) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float centerX = width / 2.0F;
        float centerY = height / 2.0F;
        long now = System.currentTimeMillis();

        List<Arrow> arrows = new ArrayList<>();
        for (Ping ping : pings) {
            float alpha = PingRenderer.alphaOf(ping, now);
            if (alpha <= 0.05F) {
                continue;
            }

            Vec3 position = ping.position();
            Vector3f view = new Vector3f(
                    (float) (position.x - cameraPos.x),
                    (float) (position.y - cameraPos.y),
                    (float) (position.z - cameraPos.z));
            inverse.transform(view);

            boolean inFront = view.z < 0.0F;
            float screenX = 0.0F;
            float screenY = 0.0F;
            boolean onScreen = false;

            if (inFront) {
                Vector4f clip = new Vector4f(view.x, view.y, view.z, 1.0F);
                projection.transform(clip);
                if (clip.w != 0.0F) {
                    float ndcX = clip.x / clip.w;
                    float ndcY = clip.y / clip.w;
                    screenX = (ndcX * 0.5F + 0.5F) * width;
                    screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * height;
                    onScreen = ndcX >= -1.0F && ndcX <= 1.0F && ndcY >= -1.0F && ndcY <= 1.0F;
                }
            }
            if (onScreen) {
                continue;
            }

            float dirX;
            float dirY;
            if (inFront) {
                dirX = screenX - centerX;
                dirY = screenY - centerY;
            } else {
                dirX = view.x;
                dirY = -view.y;
            }

            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (length < 1.0E-4F) {
                dirX = 0.0F;
                dirY = -1.0F;
                length = 1.0F;
            }
            float nx = dirX / length;
            float ny = dirY / length;

            float limitX = Math.abs(nx) < 1.0E-4F ? Float.MAX_VALUE : (centerX - MARGIN) / Math.abs(nx);
            float limitY = Math.abs(ny) < 1.0E-4F ? Float.MAX_VALUE : (centerY - MARGIN) / Math.abs(ny);
            float reach = Math.min(limitX, limitY);

            float angle = (float) Math.atan2(nx, -ny);
            arrows.add(new Arrow(
                    centerX + nx * reach,
                    centerY + ny * reach,
                    angle,
                    ping.color(),
                    alpha,
                    cameraPos.distanceTo(position)));
        }

        if (arrows.isEmpty()) {
            return;
        }

        arrows.sort(Comparator.comparingDouble(Arrow::distance));
        int limit = Math.min(config.maxOffscreenArrows, arrows.size());

        for (int i = 0; i < limit; i++) {
            drawArrow(graphics, mc, arrows.get(i));
        }
    }

    private static void drawArrow(GuiGraphics graphics, Minecraft mc, Arrow arrow) {
        float r = ((arrow.color >> 16) & 0xFF) / 255.0F;
        float g = ((arrow.color >> 8) & 0xFF) / 255.0F;
        float b = (arrow.color & 0xFF) / 255.0F;

        graphics.pose().pushPose();
        graphics.pose().translate(arrow.x, arrow.y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotation(arrow.angle));
        graphics.pose().translate(-ARROW_SIZE / 2.0F, -ARROW_SIZE / 2.0F, 0.0F);

        // «Бесцветный» путь blit не включает блендинг сам — без этого альфа
        // из setColor и прозрачность PNG зависят от состояния GL снаружи.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(r, g, b, arrow.alpha);
        graphics.blit(ARROW, 0, 0, ARROW_SIZE, ARROW_SIZE, 0.0F, 0.0F,
                ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        graphics.pose().popPose();

        Component distance = Component.translatable("teamping.hud.distance", (int) Math.round(arrow.distance));
        int alpha = (int) (arrow.alpha * 255.0F) << 24;
        graphics.drawCenteredString(mc.font, distance,
                (int) arrow.x, (int) arrow.y + ARROW_SIZE, alpha | (arrow.color & 0xFFFFFF));
    }
}
