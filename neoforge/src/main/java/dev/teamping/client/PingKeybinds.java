package dev.teamping.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.teamping.network.PlacePingPayload;
import dev.teamping.network.RemoveWaypointPayload;
import dev.teamping.ping.Ping;
import dev.teamping.ping.PingType;
import dev.teamping.util.SableSupport;
import dev.teamping.util.TeamPingTags;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Одна клавиша плюс модификаторы, чтобы не занимать полсписка управления:
 *
 * <pre>
 * Y                 обычный пинг (на руде — автоматически «ресурс»)
 * Shift + Y         опасность
 * Ctrl  + Y         вейпоинт
 * Alt   + Y         убрать ближайший свой вейпоинт
 * Ctrl+Shift+Y      меню вейпоинтов
 * </pre>
 *
 * <p>Кому модификаторы не по душе — рядом лежат отдельные биндинги на каждое
 * действие, по умолчанию не назначенные. Назначил — работает напрямую,
 * не назначил — не мешается.
 *
 * <p>Подсказки в интерфейсе не хардкодят «Ctrl + Y», а спрашивают у самих
 * биндингов, что сейчас нажимать: игрок волен перевесить всё как угодно.
 */
public final class PingKeybinds {
    private static final String CATEGORY = "category.teamping";
    private static final double PICK_DISTANCE = 256.0D;
    private static final double SKY_DISTANCE = 64.0D;

    public static final KeyMapping PING = keyMapping("key.teamping.ping", GLFW.GLFW_KEY_Y);
    public static final KeyMapping DANGER = unbound("key.teamping.danger");
    public static final KeyMapping WAYPOINT = unbound("key.teamping.waypoint");
    public static final KeyMapping REMOVE_WAYPOINT = unbound("key.teamping.remove_waypoint");
    public static final KeyMapping WAYPOINT_MENU = unbound("key.teamping.waypoint_menu");

    public static final KeyMapping[] ALL = {PING, DANGER, WAYPOINT, REMOVE_WAYPOINT, WAYPOINT_MENU};

    private PingKeybinds() {
    }

    private static KeyMapping keyMapping(String name, int key) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    private static KeyMapping unbound(String name) {
        return keyMapping(name, InputConstants.UNKNOWN.getValue());
    }

    public static void tick(Minecraft minecraft) {
        while (WAYPOINT_MENU.consumeClick()) {
            openMenu(minecraft);
        }
        while (DANGER.consumeClick()) {
            place(minecraft, PingType.DANGER);
        }
        while (WAYPOINT.consumeClick()) {
            place(minecraft, PingType.WAYPOINT);
        }
        while (REMOVE_WAYPOINT.consumeClick()) {
            removeNearestOwnWaypoint(minecraft);
        }
        while (PING.consumeClick()) {
            handleMainKey(minecraft);
        }
    }

    private static void handleMainKey(Minecraft minecraft) {
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();

        if (ctrl && shift) {
            openMenu(minecraft);
        } else if (Screen.hasAltDown()) {
            removeNearestOwnWaypoint(minecraft);
        } else if (ctrl) {
            place(minecraft, PingType.WAYPOINT);
        } else if (shift) {
            place(minecraft, PingType.DANGER);
        } else {
            place(minecraft, null);
        }
    }

    /** @param forced тип пинга, либо {@code null} — тогда «обычный», а на руде «ресурс». */
    private static void place(Minecraft minecraft, @Nullable PingType forced) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        Vec3 eye = minecraft.player.getEyePosition();
        HitResult hit = minecraft.player.pick(PICK_DISTANCE, 1.0F, false);
        BlockPos blockPos = blockPosOf(hit);
        boolean ore = blockPos != null && isOre(minecraft.level, blockPos);

        // Sable перезаписывает ванильный clip, поэтому в hit может лежать блок судна —
        // но в координатах его плота, за тридцать миллионов блоков. Для сравнения
        // с сущностью нужна честная мировая точка.
        SableSupport.ShipHit ship = blockPos == null ? null
                : SableSupport.resolve(minecraft.level, blockPos, hit.getLocation());
        double blockDistanceSqr = hit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : eye.distanceToSqr(ship != null ? ship.worldPosition() : hit.getLocation());

        Entity entity = pickEntity(minecraft, eye, blockDistanceSqr);
        if (entity != null) {
            // Тип сущности решает сервер: свой или чужой — это его знание, не наше.
            ClientNetworking.send(new PlacePingPayload(entity.getBoundingBox().getCenter(),
                    forced != null ? forced : PingType.NORMAL, Optional.empty(), entity.getId()));
            return;
        }

        Vec3 target;
        Optional<BlockPos> targetBlock = Optional.empty();
        if (blockPos != null) {
            targetBlock = Optional.of(blockPos);
            target = ore ? Vec3.atCenterOf(blockPos) : hit.getLocation();
        } else {
            // Ничего не задели — точка по взгляду, чтобы можно было пингать небо и воду.
            target = eye.add(minecraft.player.getLookAngle().scale(SKY_DISTANCE));
        }

        PingType type = forced != null ? forced : (ore ? PingType.RESOURCE : PingType.NORMAL);
        ClientNetworking.send(new PlacePingPayload(target, type, targetBlock,
                PlacePingPayload.NO_ENTITY));
    }

    /**
     * Ближайшая сущность на луче, если она ближе блока. Свой рейкаст, а не
     * {@code ProjectileUtil}: так меньше зависимость от сигнатур и понятнее,
     * что именно попадает в выборку.
     */
    @Nullable
    private static Entity pickEntity(Minecraft minecraft, Vec3 eye, double blockDistanceSqr) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Vec3 end = eye.add(minecraft.player.getLookAngle().scale(PICK_DISTANCE));
        AABB search = new AABB(eye, end).inflate(2.0D);

        Entity best = null;
        double bestSqr = Math.min(blockDistanceSqr, PICK_DISTANCE * PICK_DISTANCE);

        for (Entity candidate : minecraft.level.getEntities(minecraft.player, search,
                entity -> entity.isPickable() && !entity.isSpectator())) {
            AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            Optional<Vec3> clip = box.clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(clip.get());
            if (distance < bestSqr) {
                bestSqr = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos blockPosOf(HitResult hit) {
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    private static boolean isOre(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(TeamPingTags.ORES);
    }

    private static void removeNearestOwnWaypoint(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        Ping nearest = null;
        double best = Double.MAX_VALUE;
        for (Ping ping : PingStore.ownWaypoints()) {
            double distance = ping.position().distanceToSqr(minecraft.player.position());
            if (distance < best) {
                best = distance;
                nearest = ping;
            }
        }
        if (nearest != null) {
            ClientNetworking.send(new RemoveWaypointPayload(nearest.id()));
        }
    }

    private static void openMenu(Minecraft minecraft) {
        if (minecraft.screen == null) {
            minecraft.setScreen(new WaypointScreen());
        }
    }

    // --- подсказки -------------------------------------------------------
    //
    // Ничего не хардкодим: спрашиваем у самих биндингов, что назначено прямо сейчас.
    // Перевесил клавиши — подсказки в меню поедут следом.

    public static Component pingHint() {
        return bound(PING) ? PING.getTranslatedKeyMessage() : unboundText();
    }

    public static Component dangerHint() {
        return hint(DANGER, "teamping.key.with_shift");
    }

    public static Component waypointHint() {
        return hint(WAYPOINT, "teamping.key.with_ctrl");
    }

    public static Component removeHint() {
        return hint(REMOVE_WAYPOINT, "teamping.key.with_alt");
    }

    /** Отдельная клавиша, если назначена; иначе модификатор плюс основная. */
    private static Component hint(KeyMapping standalone, String comboKey) {
        if (bound(standalone)) {
            return standalone.getTranslatedKeyMessage();
        }
        if (bound(PING)) {
            return Component.translatable(comboKey, PING.getTranslatedKeyMessage());
        }
        return unboundText();
    }

    private static boolean bound(KeyMapping mapping) {
        return !mapping.isUnbound();
    }

    private static Component unboundText() {
        return Component.translatable("teamping.key.unbound");
    }
}
