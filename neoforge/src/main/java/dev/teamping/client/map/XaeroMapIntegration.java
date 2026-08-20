package dev.teamping.client.map;

import dev.teamping.TeamPing;
import dev.teamping.ping.Ping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Метки на миникарте и мировой карте Xaero.
 *
 * <p>Через рефлексию — сознательно. У Xaero нет публичного API для вейпоинтов,
 * исходники закрыты, а compileOnly на его внутренности заставил бы пинить точную
 * версию мода и ломал бы сборку при каждом его обновлении. Здесь же худший
 * сценарий — «метки на карте не появились», один warn в лог, всё остальное
 * работает как ни в чём не бывало.
 *
 * <p>Определяем наличие <b>по классу, а не по modId</b>: миникарта Xaero входит
 * ещё и в Better PVP (modId {@code xaerobetterpvp}), и проверка одного
 * {@code xaerominimap} мимо него промахнулась бы.
 *
 * <p>Форма API, проверенная на minimap 25.3.x – 26.4.x под 1.21.1:
 * <pre>
 * BuiltInHudModules.MINIMAP.getCurrentSession()
 *     .getWorldManager().getCurrentWorld().getCurrentWaypointSet()
 * new Waypoint(x, y, z, name, initials, WaypointColor, WaypointPurpose, temporary)
 * WaypointSet.add(Waypoint) / remove(Waypoint)
 * </pre>
 * Обрати внимание: сам {@code Waypoint} так и остался в старом пакете
 * {@code xaero.common.minimap.waypoints}, хотя всё вокруг переехало в
 * {@code xaero.hud.*} — это не опечатка.
 *
 * <p>Мировая карта своих вейпоинтов не хранит, она читает их у миникарты.
 * Поэтому одна вставка даёт метку сразу на обеих, но если стоит <i>только</i>
 * мировая карта — класть метки некуда, и интеграция не включится.
 */
public final class XaeroMapIntegration implements MapIntegration {
    private static final String CLASS_MODULES = "xaero.hud.minimap.BuiltInHudModules";
    private static final String CLASS_WAYPOINT = "xaero.common.minimap.waypoints.Waypoint";
    private static final String CLASS_COLOR = "xaero.hud.minimap.waypoint.WaypointColor";
    private static final String CLASS_PURPOSE = "xaero.hud.minimap.waypoint.WaypointPurpose";
    private static final String CLASS_SUPPORT_MODS = "xaero.map.mods.SupportMods";

    /** Палитра ванильных цветовых кодов: к ней приводим цвет команды. */
    private static final int[] VANILLA_PALETTE = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final Map<String, Method> METHOD_CACHE = new HashMap<>();

    private final Object minimapModule;
    private final Class<?> waypointClass;
    private final Constructor<?> waypointConstructor;
    private final Method colorFromIndex;
    private final Object purposeNormal;

    /** Мировая карта необязательна: без неё просто нечего обновлять. */
    @Nullable
    private final Class<?> supportModsClass;

    /**
     * Наши метки, чтобы потом было что снимать: Xaero не переопределяет equals,
     * поэтому держим прямую ссылку на объект. Вместе с ним запоминаем и набор,
     * в который клали: игрок волен переключить набор в любой момент, и снимать
     * метку надо из того же места, иначе она навсегда осядет в чужом списке.
     */
    private final LinkedHashMap<UUID, Placed> placed = new LinkedHashMap<>();

    private record Placed(Object waypointSet, Object waypoint) {
    }

    private boolean broken = false;

    private XaeroMapIntegration(Object minimapModule, Class<?> waypointClass,
                                Constructor<?> waypointConstructor, Method colorFromIndex,
                                Object purposeNormal, @Nullable Class<?> supportModsClass) {
        this.minimapModule = minimapModule;
        this.waypointClass = waypointClass;
        this.waypointConstructor = waypointConstructor;
        this.colorFromIndex = colorFromIndex;
        this.purposeNormal = purposeNormal;
        this.supportModsClass = supportModsClass;
    }

    /** Возвращает рабочую интеграцию или {@code null}, если карты Xaero нет. */
    @Nullable
    public static MapIntegration create() {
        try {
            Class<?> modules = Class.forName(CLASS_MODULES);
            Object minimapModule = modules.getField("MINIMAP").get(null);
            if (minimapModule == null) {
                return null;
            }

            Class<?> waypointClass = Class.forName(CLASS_WAYPOINT);
            Class<?> colorClass = Class.forName(CLASS_COLOR);
            Class<?> purposeClass = Class.forName(CLASS_PURPOSE);

            Constructor<?> constructor = waypointClass.getConstructor(
                    int.class, int.class, int.class,
                    String.class, String.class,
                    colorClass, purposeClass, boolean.class);
            Method colorFromIndex = colorClass.getMethod("fromIndex", int.class);
            Object purposeNormal = enumConstant(purposeClass, "NORMAL");
            if (purposeNormal == null) {
                return null;
            }

            Class<?> supportMods = null;
            try {
                supportMods = Class.forName(CLASS_SUPPORT_MODS);
            } catch (Throwable ignored) {
                // Мировая карта не установлена — не беда.
            }

            TeamPing.LOGGER.info("Xaero maps found, pings will also show up as map markers");
            return new XaeroMapIntegration(minimapModule, waypointClass, constructor,
                    colorFromIndex, purposeNormal, supportMods);
        } catch (Throwable t) {
            // Карты Xaero нет — нормальная ситуация, молчим. А вот если мод стоит,
            // но не собрался — это уже расхождение версий, и о нём надо сказать,
            // иначе игрок увидит только «карт не найдено» и будет искать не там.
            if (minimapInstalled()) {
                TeamPing.LOGGER.warn("Xaero's Minimap is installed but its internals did not match, "
                        + "map markers are off (world markers keep working)", t);
            }
            return null;
        }
    }

    /**
     * Именно по классу, а не по id мода: тот же миникарточный код едет внутри
     * Better PVP под другим идентификатором.
     */
    private static boolean minimapInstalled() {
        try {
            Class.forName(CLASS_MODULES);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String name() {
        return "Xaero";
    }

    @Override
    public void add(Ping ping) {
        if (broken || !inCurrentDimension(ping)) {
            return;
        }
        try {
            Object set = currentWaypointSet();
            if (set == null) {
                return;
            }
            removeInternal(ping.id());

            Object waypoint = waypointConstructor.newInstance(
                    (int) Math.floor(ping.position().x),
                    (int) Math.floor(ping.position().y),
                    (int) Math.floor(ping.position().z),
                    labelFor(ping),
                    ping.type().symbol(),
                    colorFromIndex.invoke(null, nearestPaletteIndex(ping.color())),
                    purposeNormal,
                    // temporary: если игра всё-таки закроется с висящей меткой,
                    // Xaero не потащит её в следующий заход.
                    true);

            invoke(set, "add", waypointClass, waypoint);
            placed.put(ping.id(), new Placed(set, waypoint));
            refreshWorldMap();
        } catch (Throwable t) {
            onBroken(t);
        }
    }

    @Override
    public void remove(UUID pingId) {
        if (broken) {
            return;
        }
        try {
            if (removeInternal(pingId)) {
                refreshWorldMap();
            }
        } catch (Throwable t) {
            onBroken(t);
        }
    }

    @Override
    public void clear() {
        if (placed.isEmpty()) {
            return;
        }
        if (broken) {
            placed.clear();
            return;
        }
        try {
            for (Placed entry : placed.values()) {
                invoke(entry.waypointSet(), "remove", waypointClass, entry.waypoint());
            }
            refreshWorldMap();
        } catch (Throwable t) {
            onBroken(t);
        } finally {
            placed.clear();
        }
    }

    // --- внутреннее --------------------------------------------------------

    private boolean removeInternal(UUID pingId) throws Exception {
        Placed entry = placed.remove(pingId);
        if (entry == null) {
            return false;
        }
        invoke(entry.waypointSet(), "remove", waypointClass, entry.waypoint());
        return true;
    }

    @Nullable
    private Object currentWaypointSet() throws Exception {
        Object session = invoke(minimapModule, "getCurrentSession");
        if (session == null) {
            return null;
        }
        Object worldManager = invoke(session, "getWorldManager");
        if (worldManager == null) {
            return null;
        }
        Object world = invoke(worldManager, "getCurrentWorld");
        if (world == null) {
            return null;
        }
        return invoke(world, "getCurrentWaypointSet");
    }

    /** Мировая карта кеширует список вейпоинтов — без пинка она не заметит правку. */
    private void refreshWorldMap() {
        if (supportModsClass == null) {
            return;
        }
        try {
            Object minimapSupport = supportModsClass.getField("xaeroMinimap").get(null);
            if (minimapSupport != null) {
                invoke(minimapSupport, "requestWaypointsRefresh");
            }
        } catch (Throwable ignored) {
            // Не критично: метка появится, просто мировая карта обновится позже.
        }
    }

    private static boolean inCurrentDimension(Ping ping) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension().equals(ping.dimension());
    }

    private static String labelFor(Ping ping) {
        String text = ping.label() != null ? ping.label().getString() : ping.ownerName();
        if (ping.label() != null) {
            text = text + " — " + ping.ownerName();
        }
        return text.length() > 32 ? text.substring(0, 32) : text;
    }

    /** У Xaero шестнадцать цветов, поэтому цвет команды приводим к ближайшему. */
    private static int nearestPaletteIndex(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < VANILLA_PALETTE.length; i++) {
            int candidate = VANILLA_PALETTE[i];
            long dr = red - ((candidate >> 16) & 0xFF);
            long dg = green - ((candidate >> 8) & 0xFF);
            long db = blue - (candidate & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    @Nullable
    private static Object enumConstant(Class<?> enumClass, String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> value && value.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    private static Object invoke(Object target, String name, Class<?> parameterType, Object argument)
            throws Exception {
        return method(target.getClass(), name, parameterType).invoke(target, argument);
    }

    @Nullable
    private static Object invoke(Object target, String name) throws Exception {
        return method(target.getClass(), name).invoke(target);
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> parameterType : parameterTypes) {
            key.append('/').append(parameterType.getName());
        }
        String cacheKey = key.toString();

        Method cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Method resolved = owner.getMethod(name, parameterTypes);
        METHOD_CACHE.put(cacheKey, resolved);
        return resolved;
    }

    private void onBroken(Throwable t) {
        broken = true;
        placed.clear();
        TeamPing.LOGGER.warn("Failed to place a marker on the Xaero map, disabling the integration "
                + "for this session; everything else keeps working", t);
    }
}
