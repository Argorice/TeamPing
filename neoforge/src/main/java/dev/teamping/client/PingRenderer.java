package dev.teamping.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.teamping.config.ClientConfig;
import dev.teamping.config.TeamPingConfig;
import dev.teamping.ping.Ping;
import dev.teamping.ping.PingType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Рендер маркеров в мире. Тело общее для обоих лоадеров, подпроекты
 * только зовут {@link #render} из своего события.
 *
 * <p>Два прохода: сначала «сквозь стены» полупрозрачным {@code textSeeThrough},
 * потом обычный {@code text} с тестом глубины поверх. В прямой видимости
 * иконка выходит сплошной, за стеной — приглушённой.
 */
public final class PingRenderer {
    private static final float THROUGH_WALL_ALPHA = 0.40F;
    private static final float BASE_TEXT_SCALE = 0.025F;

    private static final Matrix4f LAST_PROJECTION = new Matrix4f();
    private static boolean hasProjection = false;

    private PingRenderer() {
    }

    /** Матрица проекции этого кадра — нужна HUD-стрелкам. */
    @Nullable
    public static Matrix4f lastProjection() {
        return hasProjection ? LAST_PROJECTION : null;
    }

    public static void render(@Nullable PoseStack poseStack, Camera camera, @Nullable Matrix4f projectionMatrix) {
        if (projectionMatrix != null) {
            LAST_PROJECTION.set(projectionMatrix);
            hasProjection = true;
        }
        if (poseStack == null || camera == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        List<Ping> pings = PingStore.visible();
        if (pings.isEmpty()) {
            return;
        }

        ClientConfig config = TeamPingConfig.client();
        long now = System.currentTimeMillis();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        if (config.showThroughWalls) {
            for (Ping ping : pings) {
                draw(poseStack, buffers, camera, cameraPos, ping, now, config, true);
            }
            buffers.endBatch();
        }

        for (Ping ping : pings) {
            draw(poseStack, buffers, camera, cameraPos, ping, now, config, false);
        }
        buffers.endBatch();
    }

    private static void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Camera camera,
                             Vec3 cameraPos, Ping ping, long now, ClientConfig config, boolean throughWalls) {
        float alpha = fade(ping, now) * (throughWalls ? THROUGH_WALL_ALPHA : 1.0F);
        if (alpha <= 0.05F) {
            return;
        }

        Vec3 position = ping.position();
        double distance = cameraPos.distanceTo(position);

        // Не линейный масштаб: вблизи не гигантский, вдали не исчезает.
        float scale = (float) (Mth.clamp(distance * 0.025D, 0.5D, 4.0D) * config.pingScale);
        scale *= popIn(ping, now);
        if (ping.type() == PingType.DANGER) {
            scale *= 1.0F + 0.05F * (float) Math.sin((now % 1000L) / 1000.0D * Math.PI * 2.0D);
        }

        poseStack.pushPose();
        poseStack.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
        poseStack.mulPose(camera.rotation());

        drawIcon(poseStack, buffers, ping, scale, alpha, throughWalls);
        drawLabels(poseStack, buffers, ping, scale, alpha, distance, config, throughWalls);

        poseStack.popPose();
    }

    private static void drawIcon(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                 Ping ping, float scale, float alpha, boolean throughWalls) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        RenderType renderType = throughWalls
                ? RenderType.textSeeThrough(ping.type().texture())
                : RenderType.text(ping.type().texture());
        VertexConsumer consumer = buffers.getBuffer(renderType);

        Matrix4f pose = poseStack.last().pose();
        int color = ping.color();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (alpha * 255.0F);
        int light = LightTexture.FULL_BRIGHT;

        // Порядок вершин — как у ванильных билбордов-частиц (BR → TR → TL → BL).
        // Обратный порядок даёт заднюю грань: у RenderType.text отсечение включено,
        // и квад просто не нарисуется.
        consumer.addVertex(pose, 0.5F, -0.5F, 0.0F).setColor(r, g, b, a).setUv(1.0F, 1.0F).setLight(light);
        consumer.addVertex(pose, 0.5F, 0.5F, 0.0F).setColor(r, g, b, a).setUv(1.0F, 0.0F).setLight(light);
        consumer.addVertex(pose, -0.5F, 0.5F, 0.0F).setColor(r, g, b, a).setUv(0.0F, 0.0F).setLight(light);
        consumer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(r, g, b, a).setUv(0.0F, 1.0F).setLight(light);

        poseStack.popPose();
    }

    private static void drawLabels(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Ping ping,
                                   float scale, float alpha, double distance, ClientConfig config,
                                   boolean throughWalls) {
        Font font = Minecraft.getInstance().font;
        float textScale = BASE_TEXT_SCALE * scale * 1.15F;

        poseStack.pushPose();
        poseStack.translate(0.0F, -0.62F * scale, 0.0F);
        // Инверсия по X и Y: текст в мире рисуется «вверх ногами» без неё.
        poseStack.scale(-textScale, -textScale, textScale);

        Matrix4f pose = poseStack.last().pose();
        Font.DisplayMode mode = throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        int textAlpha = (int) (alpha * 255.0F) << 24;

        int line = 0;
        Component owner = Component.literal(ping.ownerName())
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ping.color())));
        line = drawLine(font, buffers, pose, mode, owner, line, textAlpha | 0xFFFFFF);

        if (ping.label() != null) {
            line = drawLine(font, buffers, pose, mode, ping.label(), line, textAlpha | (ping.color() & 0xFFFFFF));
        }

        if (config.showDistance) {
            Component text = Component.translatable("teamping.hud.distance", (int) Math.round(distance));
            drawLine(font, buffers, pose, mode, text, line, textAlpha | 0xBFBFBF);
        }

        poseStack.popPose();
    }

    private static int drawLine(Font font, MultiBufferSource buffers, Matrix4f pose, Font.DisplayMode mode,
                                Component text, int line, int color) {
        float x = -font.width(text) / 2.0F;
        float y = line * 10.0F;
        font.drawInBatch(text, x, y, color, false, pose, buffers, mode, 0, LightTexture.FULL_BRIGHT);
        return line + 1;
    }

    /** Та же прозрачность, что и у иконки — чтобы стрелки на HUD гасли синхронно. */
    public static float alphaOf(Ping ping, long now) {
        return fade(ping, now);
    }

    /** Плавное появление и затухание за последние 2 секунды жизни. */
    private static float fade(Ping ping, long now) {
        float in = Mth.clamp(ping.ageMs(now) / 200.0F, 0.0F, 1.0F);
        float out = 1.0F;
        if (!ping.permanent()) {
            long remaining = ping.remainingMs(now);
            if (remaining < 2000L) {
                out = Mth.clamp(remaining / 2000.0F, 0.0F, 1.0F);
            }
        }
        return in * out;
    }

    /** Лёгкий «поп» при появлении — маркер заметен боковым зрением. */
    private static float popIn(Ping ping, long now) {
        float t = Mth.clamp(ping.ageMs(now) / 220.0F, 0.0F, 1.0F);
        return 0.4F + 0.6F * easeOutBack(t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float p = t - 1.0F;
        return 1.0F + c3 * p * p * p + c1 * p * p;
    }
}
