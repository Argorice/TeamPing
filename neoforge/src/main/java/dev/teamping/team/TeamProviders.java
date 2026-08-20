package dev.teamping.team;

import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import dev.teamping.TeamPing;
import dev.teamping.config.TeamPingConfig;

/**
 * Выбор провайдера один раз при старте сервера.
 * Фабрика возвращает интерфейс, а не класс FTB, и зовётся только внутри {@code if}.
 */
public final class TeamProviders {
    private static TeamProvider instance = new SoloProvider();

    private TeamProviders() {
    }

    public static void init() {
        String requested = TeamPingConfig.server().teamProvider;

        List<TeamSource> sources = new ArrayList<>();
        if (!"solo".equals(requested)) {
            if (!"scoreboard".equals(requested)) {
                TeamSource ftb = ftbOrNull();
                if (ftb != null) {
                    sources.add(ftb);
                }
            }
            if (!"ftb".equals(requested)) {
                sources.add(new ScoreboardTeamSource());
            }
        }

        instance = sources.isEmpty() ? new SoloProvider() : new CompositeTeamProvider(sources);
        TeamPing.LOGGER.info("Team provider: {} (teamProvider = \"{}\")",
                instance.describe(), requested);
    }

    @Nullable
    private static TeamSource ftbOrNull() {
        return ModList.get().isLoaded("ftbteams") ? FtbTeamSource.create() : null;
    }

    public static TeamProvider get() {
        return instance;
    }
}
