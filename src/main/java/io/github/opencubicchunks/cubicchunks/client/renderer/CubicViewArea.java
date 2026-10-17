package io.github.opencubicchunks.cubicchunks.client.renderer;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;

/**
 * Размер и центр клиентской области секций. Радиус по Y — число секций из {@code CommonConfig}, снятое в момент
 * перестройки области, а не чтение toml на кадр.
 */
public final class CubicViewArea {
    private CubicViewArea() {}

    /** Горизонтальная сторона сетки, та же формула, что у ванильного {@code ViewArea.setViewDistance}. */
    public static int horizontalGridSize(int renderDistanceChunks) {
        return renderDistanceChunks * 2 + 1;
    }

    /** Вертикальная сторона сетки в кубическом мире. {@code verticalViewDistance} — в секциях, как дистанция прорисовки. */
    public static int verticalGridSize(int verticalViewDistance) {
        return verticalViewDistance * 2 + 1;
    }

    /**
     * Высота сетки. Уровень {@code CHUNK} оставляет ванильное {@code Level.getSectionsCount()}. Кубический мир берёт вертикальный радиус.
     */
    public static int sectionGridSizeY(boolean cubic, int vanillaSectionCount, int verticalViewDistance) {
        if (!cubic) {
            return vanillaSectionCount;
        }
        return verticalGridSize(verticalViewDistance);
    }

    public static int sectionCount(int renderDistanceChunks, int verticalViewDistance) {
        int horizontal = horizontalGridSize(renderDistanceChunks);
        return horizontal * verticalGridSize(verticalViewDistance) * horizontal;
    }

    public static boolean containsSection(
            int cameraSectionX, int cameraSectionY, int cameraSectionZ, int horizontalRadius, int verticalRadius, int sectionX, int sectionY,
            int sectionZ
    ) {
        int dx = Math.abs(sectionX - cameraSectionX);
        int dy = Math.abs(sectionY - cameraSectionY);
        int dz = Math.abs(sectionZ - cameraSectionZ);
        return dx <= horizontalRadius && dz <= horizontalRadius && dy <= verticalRadius;
    }

    /** Куб, которому принадлежит секция камеры. Тот же куб уходит в {@code cc_updateViewCenter}. */
    public static CubePos centerCube(int sectionX, int sectionY, int sectionZ) {
        return CubePos.of(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY), Coords.sectionToCube(sectionZ));
    }

    public static boolean sameCenter(CubePos cacheCenter, int sectionX, int sectionY, int sectionZ) {
        return cacheCenter.equals(centerCube(sectionX, sectionY, sectionZ));
    }

    /** Интерфейс миксина {@code ViewArea}: вертикальный радиус, закэшированный при {@code setViewDistance}. */
    public interface Radius {
        int cc_verticalRadiusSections();
    }
}
