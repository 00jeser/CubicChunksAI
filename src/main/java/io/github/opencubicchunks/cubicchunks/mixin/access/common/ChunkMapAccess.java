package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    // E4: ChunkMap.getChunks() нет в 26.1.2. Видимые держатели лежат в visibleChunkMap.
    @Accessor("visibleChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> cc_visibleChunkMap();
}
