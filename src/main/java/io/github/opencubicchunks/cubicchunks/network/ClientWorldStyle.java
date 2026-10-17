package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;

/**
 * Стиль, который сервер прислал до конструктора {@code ClientLevel}. Очередь одноразовая: следующее измерение не наследует предыдущее.
 */
public final class ClientWorldStyle {
    private static volatile WorldStyle queued;

    private ClientWorldStyle() {}

    public static void queue(WorldStyle style) {
        queued = style;
    }

    public static WorldStyle take() {
        WorldStyle style = queued;
        queued = null;
        return style == null ? WorldStyle.CHUNK : style;
    }
}
