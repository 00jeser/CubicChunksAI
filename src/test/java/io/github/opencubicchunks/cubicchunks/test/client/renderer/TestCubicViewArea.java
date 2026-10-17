package io.github.opencubicchunks.cubicchunks.test.client.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicViewArea;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;

public class TestCubicViewArea extends BaseTest {
    private static final int VANILLA_SECTION_COUNT = 24;
    private static final int HORIZONTAL_RENDER_DISTANCE = 12;

    @Test
    public void verticalViewDistanceTwoHasFewerSectionsThanEight() {
        try {
            CubicChunks.config().setVerticalViewDistance(2);
            int narrow = CubicViewArea.sectionCount(HORIZONTAL_RENDER_DISTANCE, CubicChunks.config().getVerticalViewDistance());
            CubicChunks.config().setVerticalViewDistance(8);
            int wide = CubicViewArea.sectionCount(HORIZONTAL_RENDER_DISTANCE, CubicChunks.config().getVerticalViewDistance());
            assertThat(narrow).isLessThan(wide);
            assertThat(CubicViewArea.verticalGridSize(2)).isEqualTo(5);
            assertThat(CubicViewArea.verticalGridSize(8)).isEqualTo(17);
        } finally {
            CubicChunks.config().setVerticalViewDistance(8);
        }
    }

    @Test
    public void chunkLevelKeepsVanillaSectionHeight() {
        assertThat(CubicViewArea.sectionGridSizeY(false, VANILLA_SECTION_COUNT, 8)).isEqualTo(VANILLA_SECTION_COUNT);
        assertThat(CubicViewArea.sectionGridSizeY(true, VANILLA_SECTION_COUNT, 8)).isEqualTo(CubicViewArea.verticalGridSize(8));
        assertThat(CubicViewArea.horizontalGridSize(HORIZONTAL_RENDER_DISTANCE)).isEqualTo(HORIZONTAL_RENDER_DISTANCE * 2 + 1);
    }

    @Test
    public void verticalRadiusTwoHidesSectionsThatEightShows() {
        assertThat(CubicViewArea.containsSection(0, 0, 0, HORIZONTAL_RENDER_DISTANCE, 2, 0, 3, 0)).isFalse();
        assertThat(CubicViewArea.containsSection(0, 0, 0, HORIZONTAL_RENDER_DISTANCE, 8, 0, 3, 0)).isTrue();
        assertThat(CubicViewArea.containsSection(0, 0, 0, 2, 8, 3, 0, 0)).isFalse();
    }

    @Test
    public void viewCenterCubeMatchesCacheCenter() {
        int blockX = 40;
        int blockY = -30;
        int blockZ = 18;
        CubePos cacheCenter = CubePos.of(Coords.blockToCube(blockX), Coords.blockToCube(blockY), Coords.blockToCube(blockZ));
        int sectionX = Coords.blockToSection(blockX);
        int sectionY = Coords.blockToSection(blockY);
        int sectionZ = Coords.blockToSection(blockZ);
        assertThat(CubicViewArea.sameCenter(cacheCenter, sectionX, sectionY, sectionZ)).isTrue();
        assertThat(CubicViewArea.centerCube(sectionX, sectionY, sectionZ)).isEqualTo(cacheCenter);

        int nextCubeBlockY = blockY + Coords.sectionToMinBlock(CubicConstants.DIAMETER_IN_SECTIONS);
        CubePos moved = CubePos.of(Coords.blockToCube(blockX), Coords.blockToCube(nextCubeBlockY), Coords.blockToCube(blockZ));
        assertThat(moved).isNotEqualTo(cacheCenter);
        assertThat(CubicViewArea.sameCenter(cacheCenter, sectionX, Coords.blockToSection(nextCubeBlockY), sectionZ)).isFalse();
        assertThat(SectionPos.of(sectionX, sectionY, sectionZ).y()).isEqualTo(sectionY);
    }
}
