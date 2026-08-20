package dev.teamping.team;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Set;

/**
 * Единственное место, через которое мод знает о командах.
 * Ни один класс FTB Teams не должен утечь за пределы {@link FtbTeamSource}.
 */
public interface TeamProvider {
    boolean isSameTeam(ServerPlayer a, ServerPlayer b);

    /** Получатели пинга. Отправитель включён — он тоже должен видеть свой маркер. */
    Collection<ServerPlayer> teammates(ServerPlayer player);

    /** RGB без альфы. */
    int teamColor(ServerPlayer player);

    /**
     * Устойчивые идентификаторы команд игрока; пусто, если команд нет.
     *
     * <p>Их несколько, потому что команды бывают сразу из нескольких источников:
     * в сборках нередко есть и пати FTB, и ванильный {@code /team}. Ими помечается
     * вейпоинт при создании, поэтому идентификатор должен переживать перезаход
     * и не зависеть от состава команды. Пусто — значит вейпоинт остаётся личной
     * заметкой автора.
     */
    Set<String> teamIds(ServerPlayer player);

    /** Для лога при старте сервера. */
    String describe();
}
