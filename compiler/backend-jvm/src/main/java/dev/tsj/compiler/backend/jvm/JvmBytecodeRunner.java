package dev.tsj.compiler.backend.jvm;

import java.io.PrintStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads and executes compiled JVM artifact classes.
 */
public final class JvmBytecodeRunner {
    public enum ClassloaderIsolationMode {
        SHARED("shared"),
        APP_ISOLATED("app-isolated");

        private final String cliValue;

        ClassloaderIsolationMode(final String cliValue) {
            this.cliValue = cliValue;
        }

        public String cliValue() {
            return cliValue;
        }
    }

    public Class<?> loadMainClass(final JvmCompiledArtifact artifact) {
        return loadMainClass(artifact, List.of(), ClassloaderIsolationMode.SHARED);
    }

    public Class<?> loadMainClass(final JvmCompiledArtifact artifact, final List<Path> interopClasspathEntries) {
        return loadMainClass(artifact, interopClasspathEntries, ClassloaderIsolationMode.SHARED);
    }

    public Class<?> loadMainClass(
            final JvmCompiledArtifact artifact,
            final List<Path> interopClasspathEntries,
            final ClassloaderIsolationMode isolationMode
    ) {
        Objects.requireNonNull(artifact, "artifact");
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextLoader = thread.getContextClassLoader();
        try (RunnerClassLoader classLoader = createClassLoader(artifact, interopClasspathEntries, isolationMode)) {
            thread.setContextClassLoader(classLoader.mainLoader());
            return Class.forName(artifact.className(), true, classLoader.mainLoader());
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-003",
                    "Generated class not found: " + artifact.className(),
                    null,
                    null,
                    classNotFoundException
            );
        } catch (final JvmCompilationException jvmCompilationException) {
            throw jvmCompilationException;
        } catch (final Exception exception) {
            throw new JvmCompilationException(
                    "TSJ-RUN-004",
                    "Failed to load generated class: " + exception.getMessage(),
                    null,
                    null,
                    exception
            );
        } finally {
            thread.setContextClassLoader(originalContextLoader);
        }
    }

    public void run(final JvmCompiledArtifact artifact, final PrintStream stdout) {
        run(artifact, stdout, System.err);
    }

    public void run(final JvmCompiledArtifact artifact, final PrintStream stdout, final PrintStream stderr) {
        run(artifact, List.of(), ClassloaderIsolationMode.SHARED, stdout, stderr);
    }

    public void run(
            final JvmCompiledArtifact artifact,
            final List<Path> interopClasspathEntries,
            final PrintStream stdout,
            final PrintStream stderr
    ) {
        run(artifact, interopClasspathEntries, ClassloaderIsolationMode.SHARED, stdout, stderr);
    }

    public void run(
            final JvmCompiledArtifact artifact,
            final List<Path> interopClasspathEntries,
            final ClassloaderIsolationMode isolationMode,
            final PrintStream stdout,
            final PrintStream stderr
    ) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextLoader = thread.getContextClassLoader();
        try (RunnerClassLoader classLoader = createClassLoader(artifact, interopClasspathEntries, isolationMode)) {
            thread.setContextClassLoader(classLoader.mainLoader());
            final Class<?> mainClass = Class.forName(artifact.className(), true, classLoader.mainLoader());
            final Method mainMethod = mainClass.getMethod("main", String[].class);
            final PrintStream originalOut = System.out;
            final PrintStream originalErr = System.err;
            synchronized (System.class) {
                try {
                    System.setOut(stdout);
                    System.setErr(stderr);
                    mainMethod.invoke(null, (Object) new String[0]);
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
            }
            stdout.flush();
            stderr.flush();
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-003",
                    "Generated class not found: " + artifact.className(),
                    null,
                    null,
                    classNotFoundException
            );
        } catch (final NoSuchMethodException noSuchMethodException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-005",
                    "Generated class missing main method: " + artifact.className(),
                    null,
                    null,
                    noSuchMethodException
            );
        } catch (final InvocationTargetException invocationTargetException) {
            final Throwable targetException = invocationTargetException.getTargetException();
            final JvmCompilationException isolationFailure = maybeIsolationFailure(targetException, isolationMode);
            if (isolationFailure != null) {
                throw isolationFailure;
            }
            throw new JvmCompilationException(
                    "TSJ-RUN-006",
                    "Generated program failed: " + targetException.getMessage(),
                    null,
                    null,
                    targetException
            );
        } catch (final JvmCompilationException jvmCompilationException) {
            throw jvmCompilationException;
        } catch (final Exception exception) {
            throw new JvmCompilationException(
                    "TSJ-RUN-004",
                    "Failed to execute generated class: " + exception.getMessage(),
                    null,
                    null,
                    exception
            );
        } finally {
            thread.setContextClassLoader(originalContextLoader);
        }
    }

    private static RunnerClassLoader createClassLoader(
            final JvmCompiledArtifact artifact,
            final List<Path> interopClasspathEntries,
            final ClassloaderIsolationMode isolationMode
    ) {
        Objects.requireNonNull(isolationMode, "isolationMode");
        if (isolationMode == ClassloaderIsolationMode.SHARED) {
            return createSharedClassLoader(artifact, interopClasspathEntries);
        }
        return createAppIsolatedClassLoader(artifact, interopClasspathEntries);
    }

    private static RunnerClassLoader createSharedClassLoader(
            final JvmCompiledArtifact artifact,
            final List<Path> interopClasspathEntries
    ) {
        final List<URL> urls = new ArrayList<>();
        urls.add(toUrl(artifact.outputDirectory()));
        for (Path classpathEntry : interopClasspathEntries) {
            final Path normalized = classpathEntry.toAbsolutePath().normalize();
            if (!Files.exists(normalized)) {
                throw new JvmCompilationException(
                        "TSJ-RUN-008",
                        "Classpath entry not found: " + normalized
                );
            }
            urls.add(toUrl(normalized));
        }
        final URLClassLoader classLoader = new URLClassLoader(
                urls.toArray(URL[]::new),
                Thread.currentThread().getContextClassLoader()
        );
        return new RunnerClassLoader(classLoader, List.of(classLoader));
    }

    private static RunnerClassLoader createAppIsolatedClassLoader(
            final JvmCompiledArtifact artifact,
            final List<Path> interopClasspathEntries
    ) {
        detectIsolationConflicts(
                artifact.outputDirectory(),
                interopClasspathEntries,
                ClassloaderIsolationMode.APP_ISOLATED
        );
        final List<URL> dependencyUrls = new ArrayList<>();
        for (Path classpathEntry : interopClasspathEntries) {
            final Path normalized = classpathEntry.toAbsolutePath().normalize();
            if (!Files.exists(normalized)) {
                throw new JvmCompilationException(
                        "TSJ-RUN-008",
                        "Classpath entry not found: " + normalized
                );
            }
            dependencyUrls.add(toUrl(normalized));
        }
        final URLClassLoader dependencyLoader = new URLClassLoader(
                dependencyUrls.toArray(URL[]::new),
                Thread.currentThread().getContextClassLoader()
        );
        final ChildFirstUrlClassLoader appLoader = new ChildFirstUrlClassLoader(
                new URL[]{toUrl(artifact.outputDirectory())},
                dependencyLoader
        );
        return new RunnerClassLoader(appLoader, List.of(appLoader, dependencyLoader));
    }

    private static void detectIsolationConflicts(
            final Path outputDirectory,
            final List<Path> interopClasspathEntries,
            final ClassloaderIsolationMode isolationMode
    ) {
        final Set<String> appClasses = collectClassNames(outputDirectory);
        if (appClasses.isEmpty()) {
            return;
        }
        for (Path classpathEntry : interopClasspathEntries) {
            final Path normalized = classpathEntry.toAbsolutePath().normalize();
            for (String dependencyClass : collectClassNames(normalized)) {
                if (!appClasses.contains(dependencyClass)) {
                    continue;
                }
                throw new JvmCompilationException(
                        "TSJ-RUN-009",
                        "Classloader isolation conflict under mode `"
                                + isolationMode.cliValue()
                                + "`: class `"
                                + dependencyClass
                                + "` exists in both app output and dependency `"
                                + normalized
                                + "`."
                );
            }
        }
    }

    private static Set<String> collectClassNames(final Path classpathEntry) {
        final Set<String> classNames = new HashSet<>();
        final Path normalized = classpathEntry.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(normalized)) {
                paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .forEach(path -> classNames.add(toClassName(normalized.relativize(path))));
            } catch (final IOException ignored) {
                return classNames;
            }
            return classNames;
        }
        if (!Files.isRegularFile(normalized)) {
            return classNames;
        }
        try (JarFile jarFile = new JarFile(normalized.toFile())) {
            final java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                final String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                classNames.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
            }
        } catch (final IOException ignored) {
            return classNames;
        }
        return classNames;
    }

    private static String toClassName(final Path relativeClassFile) {
        final String normalized = relativeClassFile
                .toString()
                .replace(java.io.File.separatorChar, '.');
        return normalized.substring(0, normalized.length() - ".class".length());
    }

    private static JvmCompilationException maybeIsolationFailure(
            final Throwable targetException,
            final ClassloaderIsolationMode isolationMode
    ) {
        if (isolationMode != ClassloaderIsolationMode.APP_ISOLATED) {
            return null;
        }
        final String missingClass = missingClassName(targetException);
        if (missingClass == null || missingClass.isBlank()) {
            return null;
        }
        final String normalizedClass = missingClass.replace('/', '.');
        return new JvmCompilationException(
                "TSJ-RUN-010",
                "Classloader isolation boundary violation under mode `"
                        + isolationMode.cliValue()
                        + "`: failed to resolve `"
                        + normalizedClass
                        + "` from dependency loader context.",
                null,
                null,
                targetException
        );
    }

    private static String missingClassName(final Throwable throwable) {
        if (throwable instanceof NoClassDefFoundError noClassDefFoundError) {
            return noClassDefFoundError.getMessage();
        }
        if (throwable instanceof ClassNotFoundException classNotFoundException) {
            return classNotFoundException.getMessage();
        }
        final Throwable cause = throwable.getCause();
        if (cause == null || cause == throwable) {
            return null;
        }
        return missingClassName(cause);
    }

    private static URL toUrl(final Path path) {
        try {
            return path.toUri().toURL();
        } catch (final MalformedURLException malformedURLException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-002",
                    "Invalid class output URL: " + malformedURLException.getMessage(),
                    null,
                    null,
                    malformedURLException
            );
        }
    }

    private record RunnerClassLoader(ClassLoader mainLoader, List<URLClassLoader> closeables)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (URLClassLoader closeable : closeables) {
                try {
                    closeable.close();
                } catch (final IOException ioException) {
                    if (failure == null) {
                        failure = ioException;
                    } else {
                        failure.addSuppressed(ioException);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(final URL[] urls, final ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected synchronized Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            if (isParentFirstNamespace(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (final ClassNotFoundException classNotFoundException) {
                    loaded = getParent().loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }

        private static boolean isParentFirstNamespace(final String className) {
            return className.startsWith("java.")
                    || className.startsWith("javax.")
                    || className.startsWith("sun.")
                    || className.startsWith("jdk.");
        }
    }
}
