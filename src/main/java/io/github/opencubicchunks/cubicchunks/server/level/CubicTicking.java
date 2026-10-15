package io.github.opencubicchunks.cubicchunks.server.level;

/**
 * Граница кубового тика. Стиль {@code CHUNK} не вызывает {@code cc_prepareTickingChunk},
 * даже если у держателя позиция куба.
 */
public final class CubicTicking {
    private CubicTicking() {}

    public static boolean preparesTickingCubes(boolean levelIsCubic, boolean positionIsCube) {
        return levelIsCubic && positionIsCube;
    }
}
