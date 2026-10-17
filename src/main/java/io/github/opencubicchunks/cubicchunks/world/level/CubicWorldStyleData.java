package io.github.opencubicchunks.cubicchunks.world.level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Ключ стиля измерения. Файл лежит по ванильному пути SavedData:
 * {@code <dimension>/data/cubicchunks/world_style.dat}, идентификатор {@code cubicchunks:world_style}.
 * Внутри NBT строка {@code CUBIC}, {@code HYBRID} или {@code CHUNK}.
 */
public final class CubicWorldStyleData extends SavedData {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("cubicchunks", "world_style");
    public static final String KEY_NAME = "cubicchunks:world_style";

    public static final Codec<CubicWorldStyleData> CODEC = RecordCodecBuilder
            .create(instance -> instance.group(Codec.STRING.flatXmap(CubicWorldStyleData::decodeStyle, style -> DataResult.success(style.name()))
                    .fieldOf("style").forGetter(CubicWorldStyleData::worldStyle)).apply(instance, CubicWorldStyleData::new));

    public static final SavedDataType<CubicWorldStyleData> TYPE = new SavedDataType<>(ID, CubicWorldStyleData::noDefault, CODEC);

    private final WorldStyle worldStyle;

    public CubicWorldStyleData(WorldStyle worldStyle) {
        this.worldStyle = worldStyle;
    }

    public WorldStyle worldStyle() {
        return worldStyle;
    }

    /**
     * @return стиль из уже записанного ключа, либо {@code null}, если файла нет.
     *         Конверт NBT — тот же, что пишет {@code SavedDataStorage}: соединение {@code data} и текущий {@code DataVersion}.
     *         Кодек не ссылается на реестры, поэтому чтение не требует {@code HolderLookup}.
     */
    public static @Nullable WorldStyle read(Path dimensionPath) {
        Path file = dataFile(dimensionPath);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            Tag data = root.get("data");
            if (data == null) {
                throw new IllegalArgumentException("Invalid value for " + KEY_NAME);
            }
            return CODEC.parse(NbtOps.INSTANCE, data)
                    .getOrThrow(message -> new IllegalArgumentException("Invalid value for " + KEY_NAME + ": " + message)).worldStyle;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid value for " + KEY_NAME, ex);
        }
    }

    public static void write(Path dimensionPath, WorldStyle style) {
        Path file = dataFile(dimensionPath);
        try {
            Files.createDirectories(file.getParent());
            Tag data = CODEC.encodeStart(NbtOps.INSTANCE, new CubicWorldStyleData(style)).getOrThrow();
            CompoundTag root = new CompoundTag();
            root.put("data", data);
            NbtUtils.addCurrentDataVersion(root);
            NbtIo.writeCompressed(root, file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write " + KEY_NAME, ex);
        }
    }

    private static Path dataFile(Path dimensionPath) {
        return ID.withSuffix(".dat").resolveAgainst(dimensionPath.resolve("data"));
    }

    private static CubicWorldStyleData noDefault() {
        throw new IllegalStateException(KEY_NAME + " has no default value");
    }

    private static DataResult<WorldStyle> decodeStyle(String name) {
        try {
            return DataResult.success(WorldStyle.valueOf(name));
        } catch (IllegalArgumentException ex) {
            return DataResult.error(() -> "Invalid value \"" + name + "\" for " + KEY_NAME);
        }
    }
}
