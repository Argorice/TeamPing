package dev.teamping.ping;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Вейпоинт, каким его хранит сервер: сам пинг плюс команда, в которой он создан.
 *
 * <p>Владение двойное, и это принципиально. Метка принадлежит и автору, и той команде,
 * где её поставили. Из этого следует всё поведение:
 *
 * <ul>
 *   <li>новый участник команды видит метки, поставленные до его прихода;</li>
 *   <li>автор ушёл из команды — метка остаётся и у него, и у команды;</li>
 *   <li>ушёл кто-то другой — метка у него пропадает, он её не создавал.</li>
 * </ul>
 *
 * @param teamIds команды автора на момент создания. Их может быть несколько: в сборках
 *                нередко есть и пати FTB, и ванильный {@code /team}, и метка принадлежит
 *                всем сразу. Пусто — метка чисто личная.
 */
public record StoredWaypoint(Ping ping, Set<String> teamIds) {

    public boolean visibleTo(UUID playerId, Set<String> playerTeamIds) {
        return visibilityReason(playerId, playerTeamIds) != null;
    }

    /**
     * Почему игрок это видит: {@code "own"} или общая команда. {@code null} — не видит.
     *
     * <p>Отдельным методом ради лога. Когда метка держится за одну из двух команд
     * (пати FTB и ванильную сразу), «почему она ещё здесь» из одних цифр не понять,
     * а из строки «via scoreboard:Kito» — сразу.
     */
    @Nullable
    public String visibilityReason(UUID playerId, Set<String> playerTeamIds) {
        if (ping.ownerId().equals(playerId)) {
            return "own";
        }
        for (String id : teamIds) {
            if (playerTeamIds.contains(id)) {
                return id;
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", ping.id());
        tag.putUUID("Owner", ping.ownerId());
        tag.putString("OwnerName", ping.ownerName());
        ListTag teams = new ListTag();
        for (String id : teamIds) {
            teams.add(StringTag.valueOf(id));
        }
        tag.put("Teams", teams);
        tag.putString("Dimension", ping.dimension().location().toString());
        tag.putDouble("X", ping.position().x);
        tag.putDouble("Y", ping.position().y);
        tag.putDouble("Z", ping.position().z);
        tag.putInt("Color", ping.color());
        tag.putLong("Created", ping.createdAt());
        if (ping.label() != null) {
            tag.putString("Label", ping.label().getString());
        }
        return tag;
    }

    private static Set<String> readTeams(CompoundTag tag) {
        Set<String> teams = new LinkedHashSet<>();
        ListTag list = tag.getList("Teams", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            teams.add(list.getString(i));
        }
        // Формат до появления нескольких команд: одна строка в поле Team.
        String legacy = tag.getString("Team");
        if (teams.isEmpty() && !legacy.isEmpty()) {
            teams.add(legacy);
        }
        return teams;
    }

    @Nullable
    public static StoredWaypoint load(CompoundTag tag) {
        try {
            ResourceLocation dimension = ResourceLocation.parse(tag.getString("Dimension"));
            Component label = tag.contains("Label") ? Component.literal(tag.getString("Label")) : null;
            Ping ping = new Ping(
                    tag.getUUID("Id"),
                    tag.getUUID("Owner"),
                    tag.getString("OwnerName"),
                    ResourceKey.create(Registries.DIMENSION, dimension),
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    PingType.WAYPOINT,
                    label,
                    tag.getInt("Color"),
                    tag.getLong("Created"),
                    0L);
            return new StoredWaypoint(ping, readTeams(tag));
        } catch (Exception e) {
            return null;
        }
    }
}
