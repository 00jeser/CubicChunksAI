package io.github.opencubicchunks.cubicchunks.client.renderer;

import io.github.opencubicchunks.cc_core.api.CubePos;

/** Граф окклюзии, который умеет отметить соседей куба. DASM-копия {@code onChunkReadyToRender} на 26.1 не применяется. */
public interface CubeOcclusionNotifier {
    void cc_onCubeReadyToRender(CubePos cubePos);
}
