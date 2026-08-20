package dev.teamping.team;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Один способ узнать команду: FTB Teams, ванильный скорборд, что угодно ещё.
 *
 * <p>Источник обязан честно отвечать «команды нет» пустотой, а не выдумывать
 * персональную команду из одного игрока. На этом держится
 * {@link CompositeTeamProvider}: он складывает всё, что нашли источники,
 * и только если не нашёл никто — переходит к радиусу.
 */
public interface TeamSource {

    /** Участники команды игрока, включая его самого. Пусто, если команды нет. */
    Collection<ServerPlayer> members(ServerPlayer player);

    /** Идентификаторы команд игрока. Пусто, если команды нет. */
    Set<String> ids(ServerPlayer player);

    /** Цвет команды, если источник его знает. */
    OptionalInt color(ServerPlayer player);

    /** Короткое имя для строчки в логе. */
    String describe();
}
