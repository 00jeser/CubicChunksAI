package io.github.notstirred.dasm.mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.notstirred.dasm.annotation.AnnotationUtil;
import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.exception.NoSuchTypeExists;
import io.github.notstirred.dasm.util.ClassNodeProvider;
import io.github.notstirred.dasm.util.TypeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

/**
 * E4: DASM 3.2.0 looks up MixinServiceModLauncher. NeoForge 26 stores the transformer on
 * FMLMixinService.mixinTransformer. This class replaces that lookup and is written over the
 * class inside dasm-neoforge before it is nested into the mod jar. The dependency version stays 3.2.0.
 */
public abstract class BaseConfigPlugin implements IMixinConfigPlugin {
    protected static final Logger logger = LogManager.getLogger("dasm");
    protected DasmExtension extension;
    protected ClassNodeProvider classNodeProvider = this.classNodeProvider();
    private int invocations = 0;

    @Override public void onLoad(String mixinPackage) {
        this.extension = DasmBootstrap.init(this.mappingsProvider(), this.classNodeProvider);
    }

    protected abstract MappingsProvider mappingsProvider();

    protected abstract ClassNodeProvider classNodeProvider();

    protected abstract List<DasmConfig> dasmConfigs();

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ++this.invocations;
        if (this.invocations <= 2) {
            return true;
        }
        if (this.invocations == 3) {
            DummyTargetsFunc dummyTargets = reflectIntoDummyTargets();
            List<DasmConfig> configs = this.dasmConfigs();
            HashSet<String> dasmTypes = new HashSet<>();
            for (DasmConfig dasmConfig : configs) {
                for (String dasmClass : dasmConfig.dasmClasses) {
                    this.extension.shouldApplyMixin(dasmClass);
                    dasmTypes.add(dasmClass);
                }
            }
            try {
                for (String dasmClass : dasmTypes) {
                    ClassNode classNode = this.classNodeProvider.classNode(Type.getObjectType(TypeUtil.classNameToInternalName(dasmClass)));
                    if (AnnotationUtil.isAnnotationPresent(classNode.invisibleAnnotations, Mixin.class)) {
                        continue;
                    }
                    dummyTargets.add(dasmClass, (classNode.access & 0x200) != 0);
                }
            } catch (NoSuchTypeExists e) {
                throw new RuntimeException(e);
            }
            return false;
        }
        return true;
    }

    private static DummyTargetsFunc reflectIntoDummyTargets() {
        try {
            IMixinService mixinService = MixinService.getService();
            Field transformerField = mixinService.getClass().getDeclaredField("mixinTransformer");
            transformerField.setAccessible(true);
            Object transformer = transformerField.get(mixinService);
            Field processorField = null;
            for (Class<?> type = transformer.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    processorField = type.getDeclaredField("processor");
                    break;
                } catch (NoSuchFieldException ignored) {
                    // Field lives on MixinTransformer, which may be a superclass of the runtime type.
                }
            }
            if (processorField == null) {
                throw new NoSuchFieldException("processor");
            }
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);
            Class<?> mixinProcessorClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinProcessor");
            Field pendingConfigsField = mixinProcessorClass.getDeclaredField("pendingConfigs");
            pendingConfigsField.setAccessible(true);
            @SuppressWarnings("unchecked") List<IMixinConfig> pendingConfigs = (List<IMixinConfig>) pendingConfigsField.get(processor);
            IMixinConfig config = pendingConfigs.stream().filter(c -> c.getName().equals("dasm-mod.mixins.json")).findFirst()
                    .orElseThrow(() -> new RuntimeException("Dasm failed to initialize, missing dasm mixin config"));
            Class<?> mixinConfigClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
            Field pendingMixinsField = mixinConfigClass.getDeclaredField("pendingMixins");
            pendingMixinsField.setAccessible(true);
            @SuppressWarnings("unchecked") List<IMixinInfo> pendingMixins = (List<IMixinInfo>) pendingMixinsField.get(config);
            IMixinInfo pendingMixin = pendingMixins.get(0);
            Class<?> mixinInfoClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
            Field declaredTargetsField = mixinInfoClass.getDeclaredField("declaredTargets");
            declaredTargetsField.setAccessible(true);
            @SuppressWarnings("unchecked") List<Object> classTargets = (List<Object>) declaredTargetsField.get(pendingMixin);
            Class<?> declaredTargetClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$DeclaredTarget");
            Constructor<?> declaredTargetsConstructor = declaredTargetClass.getDeclaredConstructor(String.class, boolean.class);
            declaredTargetsConstructor.setAccessible(true);
            IMixinInfo pendingMixinInterface = pendingMixins.get(1);
            @SuppressWarnings("unchecked") List<Object> interfaceTargets = (List<Object>) declaredTargetsField.get(pendingMixinInterface);
            classTargets.clear();
            interfaceTargets.clear();
            return (target, isInterface) -> {
                try {
                    Object declaredTarget = declaredTargetsConstructor.newInstance(target, false);
                    if (isInterface) {
                        interfaceTargets.add(declaredTarget);
                    } else {
                        classTargets.add(declaredTarget);
                    }
                } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialise DASM", e);
        }
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override public List<String> getMixins() {
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @FunctionalInterface
    interface DummyTargetsFunc {
        void add(String target, boolean isInterface);
    }
}
