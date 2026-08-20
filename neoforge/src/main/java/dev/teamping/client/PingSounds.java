package dev.teamping.client;

import dev.teamping.config.TeamPingConfig;
import dev.teamping.ping.PingType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Звук играется локально, без привязки к позиции: далёкий пинг —
 * как раз тот, который важно услышать.
 */
public final class PingSounds {
    private PingSounds() {
    }

    public static void play(PingType type) {
        double volume = TeamPingConfig.client().soundVolume;
        if (volume <= 0.0D) {
            return;
        }
        SoundEvent sound = switch (type) {
            case NORMAL -> SoundEvents.NOTE_BLOCK_BELL.value();
            case DANGER -> SoundEvents.NOTE_BLOCK_BASS.value();
            case RESOURCE -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case WAYPOINT -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case ALLY -> SoundEvents.NOTE_BLOCK_PLING.value();
            case ENEMY -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
            case VESSEL -> SoundEvents.NOTE_BLOCK_BIT.value();
        };
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, type.pitch(), (float) volume));
    }
}
