package io.github.opencubicchunks.cubicchunks.test.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.github.opencubicchunks.cc_core.CubicChunksBase;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import io.github.opencubicchunks.cubicchunks.server.level.CubicTicking;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.testutils.CloseableReference;
import io.github.opencubicchunks.cubicchunks.testutils.Misc;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import io.github.opencubicchunks.cubicchunks.world.level.CubicWorldStyleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Стиль измерения: три значения, ключ уровня важнее toml, два измерения в одном процессе.
 */
public class TestWorldStyle extends BaseTest {
    private boolean previousGenerateNewWorldsAsCC;

    @BeforeEach
    public void rememberGenerateNewWorldsAsCC() {
        previousGenerateNewWorldsAsCC = CubicChunks.config().shouldGenerateNewWorldsAsCC();
    }

    @AfterEach
    public void restoreGenerateNewWorldsAsCC() {
        CubicChunks.config().setGenerateNewWorldsAsCC(previousGenerateNewWorldsAsCC);
    }

    @Test
    public void threeStylesMatchCubicFlag() throws Exception {
        Path cubicRoot = Files.createTempDirectory("cc_style_cubic");
        CubicChunks.config().setGenerateNewWorldsAsCC(true);
        try (LineAppender lines = attachLog(); CloseableReference<ServerLevel> level = Misc.setupServerLevel(cubicRoot, Level.OVERWORLD)) {
            assertStyle(level.value(), WorldStyle.CUBIC, true);
            assertTrue(lines.lines().stream().anyMatch(line -> line.contains("dimension=minecraft:overworld style=CUBIC cubic=true")));
        }

        Path hybridRoot = Files.createTempDirectory("cc_style_hybrid");
        writeServerConfig(hybridRoot, "CHUNK", List.of(), List.of(Level.OVERWORLD.identifier().toString()), List.of());
        try (CloseableReference<ServerLevel> level = Misc.setupServerLevel(hybridRoot, Level.OVERWORLD)) {
            assertStyle(level.value(), WorldStyle.HYBRID, true);
            assertTrue(((CubicLevel) level.value()).cc_worldStyle().generates2DChunks());
            assertFalse(WorldStyle.CUBIC.generates2DChunks());
        }

        Path chunkRoot = Files.createTempDirectory("cc_style_chunk");
        CubicChunks.config().setGenerateNewWorldsAsCC(false);
        try (LineAppender lines = attachLog(); CloseableReference<ServerLevel> level = Misc.setupServerLevel(chunkRoot, Level.OVERWORLD)) {
            assertStyle(level.value(), WorldStyle.CHUNK, false);
            assertTrue(WorldStyle.CHUNK.generates2DChunks());
            assertTrue(lines.lines().stream().anyMatch(line -> line.contains("dimension=minecraft:overworld style=CHUNK cubic=false")));
        }
    }

    @Test
    public void savedStyleSurvivesRestartWhenTomlSaysChunk() throws Exception {
        Path worldRoot = Files.createTempDirectory("cc_style_restart");
        CubicChunks.config().setGenerateNewWorldsAsCC(true);
        try (CloseableReference<ServerLevel> level = Misc.setupServerLevel(worldRoot, Level.OVERWORLD)) {
            assertEquals(WorldStyle.CUBIC, ((CubicLevel) level.value()).cc_worldStyle());
        }
        assertEquals("CUBIC", readStoredStyle(worldRoot, Level.OVERWORLD));

        writeServerConfig(worldRoot, "CHUNK", List.of(), List.of(), List.of());
        CubicChunks.config().setGenerateNewWorldsAsCC(false);
        try (CloseableReference<ServerLevel> level = Misc.setupServerLevel(worldRoot, Level.OVERWORLD)) {
            assertStyle(level.value(), WorldStyle.CUBIC, true);
        }
        assertEquals("CUBIC", readStoredStyle(worldRoot, Level.OVERWORLD));
    }

    @Test
    public void newWorldWithFlagOffIsChunk() throws Exception {
        CubicChunks.config().setGenerateNewWorldsAsCC(false);
        Path worldRoot = Files.createTempDirectory("cc_style_off");
        try (CloseableReference<ServerLevel> level = Misc.setupServerLevel(worldRoot, Level.OVERWORLD)) {
            assertStyle(level.value(), WorldStyle.CHUNK, false);
        }
        assertEquals("CHUNK", readStoredStyle(worldRoot, Level.OVERWORLD));
    }

    @Test
    public void invalidStyleNamesTheConfigKey() throws Exception {
        Path worldRoot = Files.createTempDirectory("cc_style_bad");
        writeServerConfig(worldRoot, "NOT_A_STYLE", List.of(), List.of(), List.of());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ServerConfig.load(worldRoot));
        assertTrue(error.getMessage().contains("worldStyles.defaultWorldStyle"));
    }

    @Test
    public void hybridKeyIsNotCollapsedToCubic() throws Exception {
        Path worldRoot = Files.createTempDirectory("cc_style_hybrid_key");
        CubicChunks.config().setGenerateNewWorldsAsCC(true);
        writeServerConfig(worldRoot, "CUBIC", List.of(), List.of(Level.OVERWORLD.identifier().toString()), List.of());
        try (CloseableReference<ServerLevel> level = Misc.setupServerLevel(worldRoot, Level.OVERWORLD)) {
            assertEquals(WorldStyle.HYBRID, ((CubicLevel) level.value()).cc_worldStyle());
        }
        assertEquals("HYBRID", readStoredStyle(worldRoot, Level.OVERWORLD));

        writeServerConfig(worldRoot, "CUBIC", List.of(), List.of(), List.of());
        try (CloseableReference<ServerLevel> level = Misc.setupServerLevel(worldRoot, Level.OVERWORLD)) {
            assertEquals(WorldStyle.HYBRID, ((CubicLevel) level.value()).cc_worldStyle());
        }
    }

    @Test
    public void overworldAndVanillaDimensionDifferInOneProcess() throws Exception {
        Path worldRoot = Files.createTempDirectory("cc_style_two");
        CubicChunks.config().setGenerateNewWorldsAsCC(true);
        writeServerConfig(worldRoot, "CUBIC", List.of(), List.of(), List.of(Level.END.identifier().toString()));
        try (LineAppender lines = attachLog();
                CloseableReference<List<ServerLevel>> levels = Misc.setupServerLevels(worldRoot, List.of(Level.OVERWORLD, Level.END))) {
            assertStyle(levels.value().get(0), WorldStyle.CUBIC, true);
            assertStyle(levels.value().get(1), WorldStyle.CHUNK, false);
            assertTrue(lines.lines().stream().anyMatch(line -> line.contains("dimension=minecraft:overworld style=CUBIC cubic=true")));
            assertTrue(lines.lines().stream().anyMatch(line -> line.contains("dimension=" + Level.END.identifier() + " style=CHUNK cubic=false")));
        }
        assertFalse(CubicTicking.preparesTickingCubes(false, true));
        assertFalse(CubicTicking.preparesTickingCubes(false, false));
        assertTrue(CubicTicking.preparesTickingCubes(true, true));
        assertFalse(CubicTicking.preparesTickingCubes(true, false));
    }

    private static void assertStyle(ServerLevel level, WorldStyle style, boolean cubic) {
        assertEquals(style, ((CubicLevel) level).cc_worldStyle());
        assertEquals(cubic, ((CanBeCubic) level).cc_isCubic());
        assertEquals(cubic, style != WorldStyle.CHUNK);
    }

    private static void writeServerConfig(Path worldRoot, String defaultStyle, List<String> cubic, List<String> hybrid, List<String> vanilla)
            throws Exception {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("worldStyles.defaultWorldStyle", defaultStyle);
        config.set("worldStyles.cubicDimensions", cubic);
        config.set("worldStyles.hybridDimensions", hybrid);
        config.set("worldStyles.vanillaDimensions", vanilla);
        Path file = worldRoot.resolve("serverconfig").resolve("cubicchunks.toml");
        Files.createDirectories(file.getParent());
        new TomlWriter().write(config, file.toFile(), WritingMode.REPLACE);
    }

    private static String readStoredStyle(Path worldRoot, ResourceKey<Level> dimension) throws Exception {
        Identifier id = dimension.identifier();
        Path file = worldRoot.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("data")
                .resolve(CubicWorldStyleData.ID.getNamespace()).resolve(CubicWorldStyleData.ID.getPath() + ".dat");
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            return tag.getCompoundOrEmpty("data").getStringOr("style", "");
        }
    }

    private static LineAppender attachLog() {
        LineAppender appender = new LineAppender();
        appender.start();
        ((org.apache.logging.log4j.core.Logger) CubicChunksBase.LOGGER).addAppender(appender);
        return appender;
    }

    private static final class LineAppender extends AbstractAppender implements AutoCloseable {
        private final List<String> lines = new ArrayList<>();

        private LineAppender() {
            super("cc-world-style", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override public void append(LogEvent event) {
            lines.add(event.getMessage().getFormattedMessage());
        }

        private List<String> lines() {
            return lines;
        }

        @Override public void close() {
            ((org.apache.logging.log4j.core.Logger) CubicChunksBase.LOGGER).removeAppender(this);
            super.stop();
        }
    }
}
