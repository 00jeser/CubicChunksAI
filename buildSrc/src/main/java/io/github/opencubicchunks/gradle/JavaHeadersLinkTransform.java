package io.github.opencubicchunks.gradle;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.regex.Pattern;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

/**
 * Runs javaheaders 1.2.8 with ASM 9.9.1 ahead of the ASM copy Gradle puts on the worker classpath.
 * Gradle 9.2.1 ships ASM 9.8, which rejects Java 25 class files (major 69). The plugin cannot
 * rename methods, so headers already use the vanilla names; this transform only swaps MC* types.
 */
public abstract class JavaHeadersLinkTransform implements TransformAction<JavaHeadersLinkTransform.Parameters> {
    private static final Set<String> CHILD_FIRST_PREFIXES = Set.of("org.objectweb.asm.", "io.github.opencubicchunks.javaheaders.",
            "io.github.opencubicchunks.dasm.", "com.google.gson.", "com.google.common.", "org.apache.commons.lang3.");

    public interface Parameters extends TransformParameters {
        @InputFile
        RegularFileProperty getConfig();

        @Input
        Property<String> getAcceptedJars();

        @Classpath
        ConfigurableFileCollection getToolClasspath();
    }

    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();
        String name = input.getName();
        if (!Pattern.compile(getParameters().getAcceptedJars().get()).matcher(name).matches()) {
            // outputs.file(input) tells Gradle the output IS the input. That file must already
            // exist, so a jar produced by an earlier task (the patched dasm jar) fails the first
            // build. Copy into a real transform output instead.
            File output = outputs.file(name);
            try {
                Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new GradleException("javaheaders pass-through failed for " + name, e);
            }
            return;
        }
        String baseName = name.substring(0, name.lastIndexOf('.'));
        File output = outputs.file(baseName + "-headerReplacement.jar");
        try (ChildFirst loader = new ChildFirst(urls(getParameters().getToolClasspath()))) {
            Class<?> configClass = Class.forName("io.github.opencubicchunks.javaheaders.Config", true, loader);
            Object config = readConfig(loader, configClass, getParameters().getConfig().get().getAsFile());
            Class<?> transformer = Class.forName("io.github.opencubicchunks.javaheaders.JavaHeadersTransformer", true, loader);
            Method transformCoreLibrary = transformer.getMethod("transformCoreLibrary", configClass, File.class, File.class);
            transformCoreLibrary.invoke(null, config, input, output);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new GradleException("javaheaders 1.2.8 failed on " + name, cause);
        } catch (Exception e) {
            throw new GradleException("javaheaders 1.2.8 failed on " + name, e);
        }
    }

    private static Object readConfig(ClassLoader loader, Class<?> configClass, File configFile) throws Exception {
        Class<?> gsonClass = Class.forName("com.google.gson.Gson", true, loader);
        Object gson = gsonClass.getConstructor().newInstance();
        String json = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        return gsonClass.getMethod("fromJson", String.class, Class.class).invoke(gson, json, configClass);
    }

    private static URL[] urls(ConfigurableFileCollection files) throws MalformedURLException {
        Set<File> jars = files.getFiles();
        URL[] urls = new URL[jars.size()];
        int i = 0;
        for (File jar : jars) {
            urls[i++] = jar.toURI().toURL();
        }
        return urls;
    }

    private static final class ChildFirst extends URLClassLoader {
        private ChildFirst(URL[] urls) {
            super(urls, JavaHeadersLinkTransform.class.getClassLoader());
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (CHILD_FIRST_PREFIXES.stream().noneMatch(name::startsWith)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notInTools) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
