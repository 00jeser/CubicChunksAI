package io.github.opencubicchunks.cubicchunks.test.world.level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.CubicBuildHeight;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

public class TestCubicBuildHeight extends BaseTest {
    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    public void vanillaOutsideHeightIsInsideCubePacking() {
        assertTrue(CubicBuildHeight.containsBlockY(-10001));
        assertTrue(CubicBuildHeight.contains(new BlockPos(0, -10001, 0)));
        assertTrue(CubicBuildHeight.containsBlockY(-64));
        assertTrue(CubicBuildHeight.containsBlockY(320));
    }

    @Test
    public void blockBeyondCubePackingIsOutside() {
        assertFalse(CubicBuildHeight.containsBlockY(Integer.MAX_VALUE));
        assertFalse(CubicBuildHeight.containsBlockY(Integer.MIN_VALUE));
    }
}
