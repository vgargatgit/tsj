package dev.tsj.compiler.backend.jvm;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

/**
 * Loads and executes compiled JVM artifact classes.
 */
public final class JvmBytecodeRunner {
    public Class<?> loadMainClass(final JvmCompiledArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        final URL classPathUrl;
        try {
            classPathUrl = artifact.outputDirectory().toUri().toURL();
        } catch (final MalformedURLException malformedURLException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-002",
                    "Invalid class output URL: " + malformedURLException.getMessage(),
                    null,
                    null,
                    malformedURLException
            );
        }

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classPathUrl},
                Thread.currentThread().getContextClassLoader()
        )) {
            return Class.forName(artifact.className(), true, classLoader);
        } catch (final ClassNotFoundException classNotFoundException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-003",
                    "Generated class not found: " + artifact.className(),
                    null,
                    null,
                    classNotFoundException
            );
        } catch (final Exception exception) {
            throw new JvmCompilationException(
                    "TSJ-RUN-004",
                    "Failed to load generated class: " + exception.getMessage(),
                    null,
                    null,
                    exception
            );
        }
    }

    public void run(final JvmCompiledArtifact artifact, final PrintStream stdout) {
        run(artifact, stdout, System.err);
    }

    public void run(final JvmCompiledArtifact artifact, final PrintStream stdout, final PrintStream stderr) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");

        final URL classPathUrl;
        try {
            classPathUrl = artifact.outputDirectory().toUri().toURL();
        } catch (final MalformedURLException malformedURLException) {
            throw new JvmCompilationException(
                    "TSJ-RUN-002",
                    "Invalid class output URL: " + malformedURLException.getMessage(),
                    null,
                    null,
                    malformedURLException
            );
        }

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classPathUrl},
                Thread.currentThread().getContextClassLoader()
        )) {
            final Class<?> mainClass = Class.forName(artifact.className(), true, classLoader);
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
            throw new JvmCompilationException(
                    "TSJ-RUN-006",
                    "Generated program failed: " + targetException.getMessage(),
                    null,
                    null,
                    targetException
            );
        } catch (final Exception exception) {
            throw new JvmCompilationException(
                    "TSJ-RUN-004",
                    "Failed to execute generated class: " + exception.getMessage(),
                    null,
                    null,
                    exception
            );
        }
    }
}
