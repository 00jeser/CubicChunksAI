package io.github.opencubicchunks.cubicchunks.client.gui.screens;

/**
 * Прогресс загрузки, у которого позиции — кубы, а не колонки. {@code ChunkLoadStatusView.get(int, int)} в этом случае не колонка.
 */
public interface CloPosProgress {
    boolean cc_columnsAvailable();
}
