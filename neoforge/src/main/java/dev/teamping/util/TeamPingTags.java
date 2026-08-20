package dev.teamping.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class TeamPingTags {
    /** Общий тег руд, одинаковый у Fabric и NeoForge в 1.21. */
    public static final TagKey<Block> ORES = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private TeamPingTags() {
    }
}
