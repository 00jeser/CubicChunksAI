package io.github.opencubicchunks.cubicchunks.world.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Выбор стиля измерения, у которого ещё нет ключа {@code cubicchunks:world_style}.
 * Уже записанный ключ сюда не приходит: его не перебивают ни кнопка, ни toml.
 */
public final class WorldStyleSelector {
    private WorldStyleSelector() {}

    /**
     * @param saved                 стиль из данных уровня; {@code null}, если ключа нет
     * @param generateNewWorldsAsCC {@link io.github.opencubicchunks.cubicchunks.config.CommonConfig#shouldGenerateNewWorldsAsCC()}
     * @param serverConfig          конфиг мира; {@code null}, если файла {@code serverconfig/cubicchunks.toml} нет
     */
    public static WorldStyle select(
            @Nullable WorldStyle saved, boolean generateNewWorldsAsCC, @Nullable ServerConfig serverConfig, ResourceKey<Level> dimension
    ) {
        if (saved != null) {
            return saved;
        }
        if (serverConfig == null) {
            return generateNewWorldsAsCC ? WorldStyle.CUBIC : WorldStyle.CHUNK;
        }
        if (serverConfig.hasOverride(dimension) || generateNewWorldsAsCC) {
            return serverConfig.getWorldStyle(dimension);
        }
        return WorldStyle.CHUNK;
    }
}
