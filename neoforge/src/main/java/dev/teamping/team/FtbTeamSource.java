package dev.teamping.team;

import dev.teamping.TeamPing;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Команды из FTB Teams.
 *
 * <p>Через рефлексию сознательно. FTB ломает сигнатуры между версиями, а compileOnly
 * всё равно не спасает от {@code NoClassDefFoundError} на сервере без мода — спасает
 * только то, что класс не грузится. Здесь FTB не упоминается ни в одном импорте:
 * {@link #create()} либо возвращает рабочий источник, либо {@code null}.
 *
 * <p>Важная тонкость: FTB держит каждого игрока в команде <i>всегда</i>, хотя бы
 * в личной «пати из одного». Личная команда — это «команды нет», иначе ванильные
 * {@code /team} никогда не получили бы очереди на сервере с установленным FTB.
 * Отсюда фильтр по {@code isPartyTeam()}.
 *
 * <p>Форма API по исходникам FTB Teams 2101.x:
 * {@code FTBTeamsAPI.api().getManager().getTeamForPlayer(ServerPlayer)} →
 * {@code Optional<Team>}; {@code Team.getMembers()} → {@code Set<UUID>};
 * {@code Team.getTeamId()} — общий для всей пати, в отличие от {@code getId()};
 * цвет — {@code Team.getProperty(TeamProperties.COLOR)} → {@code Color4I.rgb()},
 * потому что {@code getColor()} у {@code Team} нет.
 */
public final class FtbTeamSource implements TeamSource {
    private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
    private static final String TEAM_CLASS = "dev.ftb.mods.ftbteams.api.Team";
    private static final String PROPERTIES_CLASS = "dev.ftb.mods.ftbteams.api.property.TeamProperties";
    private static final String PROPERTY_CLASS = "dev.ftb.mods.ftbteams.api.property.TeamProperty";
    private static final String COLOR_CLASS = "dev.ftb.mods.ftblibrary.icon.Color4I";

    private final Object api;
    private final Method getManager;
    private final Method getTeamForPlayer;
    private final Method getMembers;

    @Nullable
    private final Method getTeamId;
    @Nullable
    private final Method isPartyTeam;
    @Nullable
    private final Method getProperty;
    @Nullable
    private final Object colorProperty;
    @Nullable
    private final Method colorRgb;

    private boolean brokenLogged = false;

    private FtbTeamSource(Object api, Method getManager, Method getTeamForPlayer, Method getMembers,
                          @Nullable Method getTeamId, @Nullable Method isPartyTeam,
                          @Nullable Method getProperty, @Nullable Object colorProperty,
                          @Nullable Method colorRgb) {
        this.api = api;
        this.getManager = getManager;
        this.getTeamForPlayer = getTeamForPlayer;
        this.getMembers = getMembers;
        this.getTeamId = getTeamId;
        this.isPartyTeam = isPartyTeam;
        this.getProperty = getProperty;
        this.colorProperty = colorProperty;
        this.colorRgb = colorRgb;
    }

    /** Возвращает рабочий источник или {@code null}, если FTB Teams недоступен. */
    @Nullable
    public static TeamSource create() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);
            if (api == null) {
                return null;
            }

            Method getManager = apiMethod.getReturnType().getMethod("getManager");
            Method getTeamForPlayer = getManager.getReturnType()
                    .getMethod("getTeamForPlayer", ServerPlayer.class);

            Class<?> teamClass = Class.forName(TEAM_CLASS);
            Method getMembers = teamClass.getMethod("getMembers");
            Method getTeamId = firstMethod(teamClass, "getTeamId", "getId");
            Method isPartyTeam = firstMethod(teamClass, "isPartyTeam");

            Method getProperty = null;
            Object colorProperty = null;
            Method colorRgb = null;
            try {
                getProperty = teamClass.getMethod("getProperty", Class.forName(PROPERTY_CLASS));
                colorProperty = Class.forName(PROPERTIES_CLASS).getField("COLOR").get(null);
                colorRgb = Class.forName(COLOR_CLASS).getMethod("rgb");
            } catch (Throwable t) {
                TeamPing.LOGGER.warn("FTB Teams: could not read the team colour, using the vanilla one", t);
            }

            TeamPing.LOGGER.info("FTB Teams found, taking party rosters from it");
            return new FtbTeamSource(api, getManager, getTeamForPlayer, getMembers, getTeamId,
                    isPartyTeam, getProperty, colorProperty, colorRgb);
        } catch (Throwable t) {
            TeamPing.LOGGER.warn("FTB Teams is present but its API did not match, ignoring it", t);
            return null;
        }
    }

    @Override
    public Collection<ServerPlayer> members(ServerPlayer player) {
        try {
            Object team = partyOf(player);
            if (team == null) {
                return List.of();
            }
            Object members = getMembers.invoke(team);
            if (!(members instanceof Collection<?> collection)) {
                return List.of();
            }
            List<ServerPlayer> result = new ArrayList<>();
            for (Object member : collection) {
                if (member instanceof UUID uuid) {
                    ServerPlayer online = player.server.getPlayerList().getPlayer(uuid);
                    if (online != null) {
                        result.add(online);
                    }
                }
            }
            return result;
        } catch (Throwable t) {
            onBroken(t);
            return List.of();
        }
    }

    @Override
    public Set<String> ids(ServerPlayer player) {
        if (getTeamId == null) {
            return Set.of();
        }
        try {
            Object team = partyOf(player);
            if (team == null) {
                return Set.of();
            }
            Object id = getTeamId.invoke(team);
            return id == null ? Set.of() : Set.of("ftb:" + id);
        } catch (Throwable t) {
            onBroken(t);
            return Set.of();
        }
    }

    @Override
    public OptionalInt color(ServerPlayer player) {
        if (getProperty == null || colorProperty == null || colorRgb == null) {
            return OptionalInt.empty();
        }
        try {
            Object team = partyOf(player);
            if (team == null) {
                return OptionalInt.empty();
            }
            Object color = getProperty.invoke(team, colorProperty);
            if (color == null) {
                return OptionalInt.empty();
            }
            Object rgb = colorRgb.invoke(color);
            return rgb instanceof Integer value ? OptionalInt.of(value & 0xFFFFFF) : OptionalInt.empty();
        } catch (Throwable t) {
            onBroken(t);
            return OptionalInt.empty();
        }
    }

    /** Только настоящая пати; личная команда одного игрока — это {@code null}. */
    @Nullable
    private Object partyOf(ServerPlayer player) throws Exception {
        Object manager = getManager.invoke(api);
        Object result = getTeamForPlayer.invoke(manager, player);
        Object team = result instanceof Optional<?> optional ? optional.orElse(null) : result;

        if (team == null || isPartyTeam == null) {
            return team;
        }
        return Boolean.TRUE.equals(isPartyTeam.invoke(team)) ? team : null;
    }

    @Nullable
    private static Method firstMethod(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // пробуем следующее имя
            }
        }
        return null;
    }

    private void onBroken(Throwable t) {
        if (!brokenLogged) {
            brokenLogged = true;
            TeamPing.LOGGER.warn("A call into FTB Teams failed, ignoring it from now on", t);
        }
    }

    @Override
    public String describe() {
        return colorRgb == null ? "FTB Teams (no colour)" : "FTB Teams";
    }
}
