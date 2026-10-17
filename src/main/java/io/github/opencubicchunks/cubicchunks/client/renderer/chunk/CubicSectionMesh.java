package io.github.opencubicchunks.cubicchunks.client.renderer.chunk;

import java.util.function.LongPredicate;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicLevelRenderer;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Меш секции читает блоки куба из клиентского кэша. Промах — пустая секция, без {@code cc_read} и без {@code getChunk} колонки.
 * Свет в {@code LevelChunkSection} на 26.1 не хранится: компилятор берёт яркость из движка света. Отдельной заливки здесь нет,
 * {@code MixinLevelLightEngine} этот класс не трогает (E10).
 */
public final class CubicSectionMesh {
    private CubicSectionMesh() {}

    /**
     * Соседи, без которых ваниль не ставит секцию в компиляцию: восемь горизонтальных и секции сверху и снизу.
     * Угловые по диагонали Y не требуются — крайний куб окна иначе никогда не собирает меш.
     */
    public static boolean hasAllNeighbors(LongPredicate sectionExists, long sectionNode) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!sectionExists.test(SectionPos.offset(sectionNode, dx, 0, dz))) {
                    return false;
                }
            }
        }
        return sectionExists.test(SectionPos.offset(sectionNode, 0, 1, 0)) && sectionExists.test(SectionPos.offset(sectionNode, 0, -1, 0));
    }

    /**
     * Куб секции, если он уже лежит в кэше. {@code forceLoad} всегда ложный: пустой результат — не загрузка.
     */
    public static @Nullable LevelCube cachedCube(CubicLevel level, int sectionX, int sectionY, int sectionZ) {
        CubeAccess cube = level.cc_getCube(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY), Coords.sectionToCube(sectionZ),
                ChunkStatus.FULL, false);
        if (cube instanceof LevelCube levelCube) {
            return levelCube;
        }
        return null;
    }

    public static LevelCube cubeForMesh(@Nullable LevelCube cached, LevelCube emptyStandIn) {
        return cached != null ? cached : emptyStandIn;
    }

    /** Пометить секции куба и соседей грязными и сказать occlusion, что куб готов. Вызывается из {@code queueLightUpdate}. */
    public static void onCubeReady(ClientLevel level, CubePos cubePos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            LevelRenderer renderer = minecraft.levelRenderer;
            if (renderer instanceof CubicLevelRenderer cubicRenderer) {
                cubicRenderer.cc_onCubeReadyToRender(cubePos);
            }
        }
        int diameter = CubicConstants.DIAMETER_IN_SECTIONS;
        int originX = Coords.cubeToSection(cubePos.getX(), 0);
        int originY = Coords.cubeToSection(cubePos.getY(), 0);
        int originZ = Coords.cubeToSection(cubePos.getZ(), 0);
        level.setSectionRangeDirty(originX - 1, originY - 1, originZ - 1, originX + diameter, originY + diameter, originZ + diameter);
    }
}
