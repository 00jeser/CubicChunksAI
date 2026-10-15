package io.github.opencubicchunks.cubicchunks.config;

import static io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class ServerConfig extends BaseConfig {
    private static final String FILE_PATH = "serverconfig/cubicchunks.toml";

    private static final String KEY_WORLDSTYLES = "worldStyles";
    private static final String KEY_DEFAULT_WORLD_STYLE = KEY_WORLDSTYLES + ".defaultWorldStyle";
    private static final String KEY_CUBIC_DIMENSIONS = KEY_WORLDSTYLES + ".cubicDimensions";
    private static final String KEY_HYBRID_DIMENSIONS = KEY_WORLDSTYLES + ".hybridDimensions";
    private static final String KEY_CHUNK_DIMENSIONS = KEY_WORLDSTYLES + ".vanillaDimensions";

    private final WorldStyle defaultWorldStyle;
    // TODO should this be a Map<ResourceLocation, ...> instead?
    private final Map<String, WorldStyle> overrides = new HashMap<>();

    private ServerConfig(CommentedConfig config) {
        defaultWorldStyle = requireStyle(config, KEY_DEFAULT_WORLD_STYLE);
        putOverrides(config, KEY_CUBIC_DIMENSIONS, WorldStyle.CUBIC);
        putOverrides(config, KEY_HYBRID_DIMENSIONS, WorldStyle.HYBRID);
        putOverrides(config, KEY_CHUNK_DIMENSIONS, WorldStyle.CHUNK);
    }

    private void putOverrides(CommentedConfig config, String key, WorldStyle style) {
        Object raw = config.get(key);
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Invalid value for " + key);
        }
        for (Object entry : list) {
            if (!(entry instanceof String dimensionId)) {
                throw new IllegalArgumentException("Invalid value for " + key);
            }
            overrides.put(dimensionId, style);
        }
    }

    private static WorldStyle requireStyle(CommentedConfig config, String key) {
        Object raw = config.get(key);
        if (raw instanceof WorldStyle style) {
            return style;
        }
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("Missing or invalid value for " + key);
        }
        return worldStyleFromString(key, value);
    }

    private static WorldStyle worldStyleFromString(String key, String value) {
        String styleName = value.equals("VANILLA") ? "CHUNK" : value;
        try {
            return WorldStyle.valueOf(styleName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid value \"" + value + "\" for " + key, ex);
        }
    }

    public WorldStyle getWorldStyle(ResourceKey<Level> dimension) {
        return overrides.getOrDefault(dimension.identifier().toString(), defaultWorldStyle);
    }

    public boolean hasOverride(ResourceKey<Level> dimension) {
        return overrides.containsKey(dimension.identifier().toString());
    }

    /**
     * @return конфиг мира, либо {@code null}, если {@code serverconfig/cubicchunks.toml} ещё не создан
     */
    public static @Nullable ServerConfig load(Path worldFolder) {
        File configPath = getConfigPath(worldFolder);
        if (!configPath.isFile()) {
            return null;
        }
        CommentedConfig config = createDefaultConfig();
        read(configPath, config);
        return new ServerConfig(config);
    }

    private static CommentedConfig createDefaultConfig() {
        // TODO some way of setting different defaults for specific modids? e.g. for things like RFTools and Mystcraft - maybe things like
        // "mystcraft:*"
        Config.setInsertionOrderPreserved(true);
        var config = CommentedConfig.inMemory();
        config.set(KEY_DEFAULT_WORLD_STYLE, WorldStyle.CUBIC);
        config.setComment(KEY_DEFAULT_WORLD_STYLE,
                """
                         The default world style used for dimensions that are not explicitly defined in one of the three lists below.
                         Possible values:
                             "CUBIC" - the dimension uses cubic chunks.
                             "HYBRID" - the dimension has cubic chunks enabled, but uses vanilla world generation. This may improve mod compatibility in some cases.
                             "VANILLA" - the dimension does NOT use cubic chunks; it behaves the same as in vanilla, with limited height, etc.\
                        """);
        config.set(KEY_CUBIC_DIMENSIONS, List.of());
        config.set(KEY_HYBRID_DIMENSIONS, List.of());
        config.set(KEY_CHUNK_DIMENSIONS, List.of(Level.END.identifier().toString()));
        config.setComment(KEY_CUBIC_DIMENSIONS, """
                 Explicitly sets the world style for each dimension. Overrides the default in defaultWorldStyle.
                 Note that this only affects dimensions that have not yet been generated.
                 If you want to change the world style of an existing dimension, you will need to delete it manually to make it regenerate.

                 By default, the only dimension listed here is the End, which is set to Vanilla so that you don't fall forever if you fall off.\
                """);
        return config;
    }

    private static File getConfigPath(Path worldFolder) {
        return new File(worldFolder.toFile(), FILE_PATH);
    }

}
