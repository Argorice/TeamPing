package dev.teamping.team;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** Ванильные команды, те самые, что раздаются командой {@code /team}. */
public final class ScoreboardTeamSource implements TeamSource {

    @Override
    public Collection<ServerPlayer> members(ServerPlayer player) {
        Team team = player.getTeam();
        if (team == null) {
            return List.of();
        }
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (team.equals(other.getTeam())) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public Set<String> ids(ServerPlayer player) {
        Team team = player.getTeam();
        return team == null ? Set.of() : Set.of("scoreboard:" + team.getName());
    }

    @Override
    public OptionalInt color(ServerPlayer player) {
        Team team = player.getTeam();
        if (team == null) {
            return OptionalInt.empty();
        }
        ChatFormatting formatting = team.getColor();
        Integer color = formatting == null ? null : formatting.getColor();
        return color == null ? OptionalInt.empty() : OptionalInt.of(color & 0xFFFFFF);
    }

    @Override
    public String describe() {
        return "scoreboard";
    }
}
