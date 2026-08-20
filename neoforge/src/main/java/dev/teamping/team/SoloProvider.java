package dev.teamping.team;

import dev.teamping.config.TeamPingConfig;
import dev.teamping.util.ColorUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Команд нет вообще: получатели — все, кто рядом, цвет — по хешу ника.
 * Это не «заглушка», а рабочий режим для ванильного кооператива без скорбордов.
 */
public final class SoloProvider implements TeamProvider {

    @Override
    public boolean isSameTeam(ServerPlayer a, ServerPlayer b) {
        if (a.level() != b.level()) {
            return false;
        }
        double radius = TeamPingConfig.server().soloModeRadius;
        return a.distanceToSqr(b) <= radius * radius;
    }

    @Override
    public List<ServerPlayer> teammates(ServerPlayer player) {
        double radius = TeamPingConfig.server().soloModeRadius;
        double radiusSqr = radius * radius;
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other.level() != player.level()) {
                continue;
            }
            if (other == player || other.distanceToSqr(player) <= radiusSqr) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public int teamColor(ServerPlayer player) {
        return ColorUtil.fromName(player.getGameProfile().getName());
    }

    @Override
    public java.util.Set<String> teamIds(ServerPlayer player) {
        // Команд нет вообще — метить вейпоинт нечем.
        return java.util.Set.of();
    }

    @Override
    public String describe() {
        return "solo (без команд, радиус " + (int) TeamPingConfig.server().soloModeRadius + ")";
    }
}
