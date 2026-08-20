package dev.teamping.team;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Складывает все источники команд, а не выбирает один.
 *
 * <p>В сборках сплошь и рядом раздают команды дважды: пати в FTB Teams и заодно
 * ванильный {@code /team}. Выбирать между ними неправильно — игрок ждёт, что
 * сокомандники увидят метку в обоих случаях. Поэтому состав команды здесь это
 * объединение, а «одна команда» означает пересечение хотя бы по одному источнику.
 *
 * <p>Если ни один источник команды не нашёл, работает {@link SoloProvider}:
 * все в радиусе, цвет по хешу ника.
 */
public final class CompositeTeamProvider implements TeamProvider {
    private final List<TeamSource> sources;
    private final SoloProvider solo = new SoloProvider();

    public CompositeTeamProvider(List<TeamSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public Collection<ServerPlayer> teammates(ServerPlayer player) {
        Set<ServerPlayer> result = new LinkedHashSet<>();
        for (TeamSource source : sources) {
            result.addAll(source.members(player));
        }
        if (result.isEmpty()) {
            return solo.teammates(player);
        }
        // Свою метку игрок видит всегда, даже если источник забыл его перечислить.
        result.add(player);
        return new ArrayList<>(result);
    }

    @Override
    public boolean isSameTeam(ServerPlayer a, ServerPlayer b) {
        Set<String> mine = teamIds(a);
        if (mine.isEmpty()) {
            return solo.isSameTeam(a, b);
        }
        for (String id : teamIds(b)) {
            if (mine.contains(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> teamIds(ServerPlayer player) {
        Set<String> result = new LinkedHashSet<>();
        for (TeamSource source : sources) {
            result.addAll(source.ids(player));
        }
        return result;
    }

    @Override
    public int teamColor(ServerPlayer player) {
        // Цвет берём у первого источника, который его знает: смешивать нечего,
        // а порядок источников задан осмысленно.
        for (TeamSource source : sources) {
            OptionalInt color = source.color(player);
            if (color.isPresent()) {
                return color.getAsInt();
            }
        }
        return solo.teamColor(player);
    }

    @Override
    public String describe() {
        if (sources.isEmpty()) {
            return solo.describe();
        }
        StringBuilder builder = new StringBuilder();
        for (TeamSource source : sources) {
            if (!builder.isEmpty()) {
                builder.append(" + ");
            }
            builder.append(source.describe());
        }
        return builder + " (falls back to " + solo.describe() + ")";
    }
}
