package io.github.opencubicchunks.cubicchunks.test.client.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.opencubicchunks.cubicchunks.client.renderer.chunk.CubicSectionMesh;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;

public class TestCubicSectionMesh extends BaseTest {
    @Test
    public void neighborAboveIsRequired() {
        long section = SectionPos.asLong(0, 0, 0);
        long above = SectionPos.offset(section, 0, 1, 0);
        assertThat(CubicSectionMesh.hasAllNeighbors(node -> node != above, section)).isFalse();
        assertThat(CubicSectionMesh.hasAllNeighbors(node -> true, section)).isTrue();
        long unrelated = SectionPos.asLong(4, 4, 4);
        assertThat(CubicSectionMesh.hasAllNeighbors(node -> node != unrelated, section)).isTrue();
        long verticalCorner = SectionPos.offset(section, 1, 1, 1);
        assertThat(CubicSectionMesh.hasAllNeighbors(node -> node != verticalCorner, section)).isTrue();
    }

    @Test
    public void cacheMissDoesNotForceLoad() {
        CubicLevel level = mock(CubicLevel.class);
        when(level.cc_getCube(anyInt(), anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false))).thenReturn(null);

        assertThat(CubicSectionMesh.cachedCube(level, 3, -2, 1)).isNull();
        verify(level).cc_getCube(anyInt(), anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false));
        verify(level, never()).cc_getCube(anyInt(), anyInt(), anyInt(), any(), eq(true));
    }

    @Test
    public void cachedCubeIsTheMeshSource() {
        CubicLevel level = mock(CubicLevel.class);
        LevelCube cube = mock(LevelCube.class);
        when(level.cc_getCube(anyInt(), anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false))).thenReturn(cube);
        LevelCube empty = mock(LevelCube.class);

        assertThat(CubicSectionMesh.cachedCube(level, 1, 2, 3)).isSameAs(cube);
        assertThat(CubicSectionMesh.cubeForMesh(cube, empty)).isSameAs(cube);
        assertThat(CubicSectionMesh.cubeForMesh(null, empty)).isSameAs(empty);
    }
}
