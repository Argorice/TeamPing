package dev.teamping.util;

import dev.teamping.TeamPing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Суда мода Sable, на котором работает Create Aeronautics.
 *
 * <p>Судно у Sable — это не сущность и не отдельное измерение, а кусок чанков
 * того же мира, вынесенный за тридцать миллионов блоков, плюс поза. Sable
 * перезаписывает ванильный {@code BlockGetter#clip}, поэтому обычный рейкаст
 * <b>уже попадает</b> по судам — но возвращает координаты в пространстве этого
 * куска, то есть те самые миллионы. Класть их в метку нельзя: сервер отбросит
 * пинг по проверке дистанции.
 *
 * <p>Отсюда две задачи здесь: понять, что попали в судно, и перевести точку
 * попадания обратно в мировые координаты.
 *
 * <p>Через рефлексию — как и всё остальное необязательное в этом моде.
 * Проверяем именно {@code ModList.isLoaded("sable")}, а не наличие класса:
 * библиотека {@code sable-companion} заезжает внутрь и других модов, так что
 * «класс нашёлся» ещё не значит «Sable установлен».
 *
 * <p>Методы ищем по точной сигнатуре, а не по имени и числу аргументов.
 * У {@code getContaining} восемь перегрузок на два аргумента, у
 * {@code projectOutOfSubLevel} три — и какая из них попадётся первой в
 * {@code getMethods()}, не определено ничем.
 */
public final class SableSupport {
    private static final String COMPANION_CLASS = "dev.ryanhcode.sable.companion.SableCompanion";

    private static boolean resolved = false;
    private static boolean available = false;

    @Nullable
    private static Object companion;
    @Nullable
    private static Method getContaining;
    @Nullable
    private static Method projectOutOfSubLevel;
    @Nullable
    private static Method getUniqueId;
    @Nullable
    private static Method getName;

    private SableSupport() {
    }

    /** Судно под прицелом: точка попадания уже в мировых координатах. */
    public record ShipHit(Vec3 worldPosition, UUID shipId, @Nullable String name) {
    }

    /**
     * Если {@code plotPos} принадлежит судну — возвращает его вместе с точкой
     * попадания, переведённой в мир. Иначе {@code null}: попали в обычный блок.
     */
    @Nullable
    public static ShipHit resolve(Level level, BlockPos plotPos, Vec3 plotPosition) {
        if (!resolve()) {
            return null;
        }
        try {
            Object subLevel = getContaining.invoke(companion, level, plotPos);
            if (subLevel == null) {
                return null;
            }

            Object projected = projectOutOfSubLevel.invoke(companion, level, plotPosition);
            if (!(projected instanceof Vec3 world)) {
                return null;
            }

            Object id = getUniqueId == null ? null : getUniqueId.invoke(subLevel);
            Object name = getName == null ? null : getName.invoke(subLevel);

            return new ShipHit(world,
                    id instanceof UUID uuid ? uuid : new UUID(0L, 0L),
                    name instanceof String text && !text.isBlank() ? text : null);
        } catch (Throwable t) {
            available = false;
            TeamPing.LOGGER.warn("Sable lookup failed, ship pings are off for this session", t);
            return null;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return available;
        }
        resolved = true;

        if (!ModList.get().isLoaded("sable")) {
            return false;
        }
        try {
            Class<?> companionClass = Class.forName(COMPANION_CLASS);
            companion = companionClass.getField("INSTANCE").get(null);
            if (companion == null) {
                return false;
            }

            // BlockPos — это Vec3i; перегрузка под него разбирает позицию до чанка сама.
            getContaining = method(companionClass, "getContaining", Level.class, Vec3i.class);
            // Position появился в 1.2.0, Vec3 был до него и помечен на удаление.
            // Обе возвращают Vec3, так что берём ту, что есть, начиная с новой.
            projectOutOfSubLevel = method(companionClass, "projectOutOfSubLevel", Level.class, Position.class);
            if (projectOutOfSubLevel == null) {
                projectOutOfSubLevel = method(companionClass, "projectOutOfSubLevel", Level.class, Vec3.class);
            }
            if (getContaining == null || projectOutOfSubLevel == null) {
                TeamPing.LOGGER.warn("Sable is installed but its companion API did not match, "
                        + "ship pings are off. Signatures seen: {}", signatures(companionClass));
                return false;
            }

            Class<?> access = getContaining.getReturnType();
            getUniqueId = method(access, "getUniqueId");
            getName = method(access, "getName");

            available = true;
            TeamPing.LOGGER.info("Sable found, aircraft and ships can be pinged");
            return true;
        } catch (Throwable t) {
            TeamPing.LOGGER.warn("Sable is installed but could not be reached, ship pings are off", t);
            return false;
        }
    }

    @Nullable
    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Чтобы при следующем расхождении API его было видно из лога, а не из головы. */
    private static String signatures(Class<?> owner) {
        StringBuilder result = new StringBuilder();
        for (Method candidate : owner.getMethods()) {
            String name = candidate.getName();
            if (!name.equals("getContaining") && !name.equals("projectOutOfSubLevel")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(name).append('(');
            Class<?>[] parameters = candidate.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(parameters[i].getSimpleName());
            }
            result.append(')');
        }
        return result.isEmpty() ? "none" : result.toString();
    }
}
