package dev.tsj.compiler.backend.jvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.NestedOuterMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.RepeatableTag;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.RichMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.TypedAttributeMark;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.TypedAttributeMode;
import dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark;
import dev.tsj.runtime.TsjRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JvmBytecodeCompilerTest {
    private static final String TOKEN_BRIDGE_SCRIPT_PROPERTY = "tsj.backend.tokenBridgeScript";
    private static final String LEGACY_TOKENIZER_PROPERTY = "tsj.backend.legacyTokenizer";
    private static final String AST_NO_FALLBACK_PROPERTY = "tsj.backend.astNoFallback";

    @TempDir
    Path tempDir;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void emitsLoadableClassForArithmeticAndFunctionCalls() throws Exception {
        final Path sourceFile = tempDir.resolve("main.ts");
        Files.writeString(
                sourceFile,
                """
                function add(a: number, b: number) {
                  return a + b;
                }

                const total = add(2, 3) * 4;
                console.log("total=" + total);
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("out");
        final JvmBytecodeCompiler compiler = new JvmBytecodeCompiler();
        final JvmCompiledArtifact artifact = compiler.compile(sourceFile, outDir);

        assertTrue(Files.exists(artifact.classFile()));

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("total=20\n", stdout.toString(UTF_8));
    }

    @Test
    void compileInJvmStrictModeEmitsNativeClassDispatchForEligibleClassSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-class.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  value: number;

                  constructor(initial: number) {
                    this.value = initial;
                  }

                  getValue() {
                    return this.value;
                  }

                  increment(step: number) {
                    this.value = this.value + step;
                    return this.getValue();
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-class-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        final Path executableClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/Counter__TsjStrictNative.class");
        assertTrue(Files.exists(executableClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Counter",
                    "increment",
                    new Object[]{40},
                    new Object[]{2}
            );
            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmClassInvocationReturnsNativeInstanceInsteadOfTsjObjectCarrier() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-instance.ts");
        Files.writeString(
                sourceFile,
                """
                class Person {
                  name: string;

                  constructor(name: string) {
                    this.name = name;
                  }

                  self() {
                    return this;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-instance-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Person",
                    "self",
                    new Object[]{"Ada"},
                    new Object[0]
            );

            assertNotEquals("dev.tsj.runtime.TsjObject", result.getClass().getName());
            assertTrue(result.getClass().getName().contains("Person__TsjStrictNative"));
        }
    }

    @Test
    void strictJvmEligibleAnnotatedClassEmitsExecutableTopLevelClassWithoutMetadataCarrier() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-executable-class.ts");
        Files.writeString(
                sourceFile,
                """
                import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
                import { CtorMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark";
                import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                @TypeMark
                class Person {
                  @FieldMark
                  name: string;

                  @CtorMark
                  constructor(@ParamMark name: string) {
                    this.name = name;
                  }

                  @MethodMark
                  greet(@ParamMark prefix: string) {
                    return prefix + this.name;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-executable-class-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        final Path executableClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/Person__TsjStrictNative.class");
        final Path metadataCarrier = artifact.outputDirectory()
                .resolve("dev/tsj/generated/metadata/PersonTsjCarrier.class");
        assertTrue(Files.exists(executableClass));
        assertFalse(Files.exists(metadataCarrier));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.Person__TsjStrictNative",
                    true,
                    classLoader
            );
            assertTrue(executableClassType.isAnnotationPresent(TypeMark.class));
            assertTrue(executableClassType.getDeclaredField("name").isAnnotationPresent(FieldMark.class));

            final java.lang.reflect.Constructor<?> constructor = executableClassType.getDeclaredConstructor(String.class);
            assertTrue(constructor.isAnnotationPresent(CtorMark.class));
            assertEquals("name", constructor.getParameters()[0].getName());
            assertTrue(constructor.getParameters()[0].isAnnotationPresent(ParamMark.class));

            final Object instance = constructor.newInstance("Ada");
            final java.lang.reflect.Method greet = executableClassType.getDeclaredMethod("greet", String.class);
            assertTrue(greet.isAnnotationPresent(MethodMark.class));
            assertEquals("prefix", greet.getParameters()[0].getName());
            assertTrue(greet.getParameters()[0].isAnnotationPresent(ParamMark.class));
            assertEquals("hi Ada", greet.invoke(instance, "hi "));
        }
    }

    @Test
    void strictJvmExecutableClassDoesNotExposeRawObjectBridgeConstructor() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-constructor-bridge.ts");
        Files.writeString(
                sourceFile,
                """
                class Service {
                  name: string;

                  constructor(name: string) {
                    this.name = name;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-constructor-bridge-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.Service__TsjStrictNative",
                    true,
                    classLoader
            );

            assertEquals(1, executableClassType.getConstructors().length);

            final Constructor<?> typedConstructor = executableClassType.getDeclaredConstructor(String.class);
            assertTrue(Modifier.isPublic(typedConstructor.getModifiers()));
            assertThrows(NoSuchMethodException.class, () -> executableClassType.getDeclaredConstructor(Object.class));
        }
    }

    @Test
    void strictJvmExecutableClassEmitsEnumAndClassLiteralAnnotationAttributes() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-typed-annotation-values.ts");
        Files.writeString(
                sourceFile,
                """
                import { TypedAttributeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypedAttributeMark";
                import { TypedAttributeMode as Mode } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypedAttributeMode";
                import { String as JString } from "java:java.lang.String";
                import { Integer as JInteger } from "java:java.lang.Integer";
                import { Long as JLong } from "java:java.lang.Long";
                import { Double as JDouble } from "java:java.lang.Double";
                import { Boolean as JBoolean } from "java:java.lang.Boolean";
                import { Float as JFloat } from "java:java.lang.Float";
                import { Short as JShort } from "java:java.lang.Short";
                import { Byte as JByte } from "java:java.lang.Byte";

                @TypedAttributeMark({
                  mode: Mode.TYPE,
                  type: JString,
                  modes: [Mode.TYPE, Mode.METHOD],
                  types: [JString, JInteger]
                })
                class AnnotatedShape {
                  @TypedAttributeMark({
                    mode: Mode.FIELD,
                    type: JLong,
                    types: [JLong, JDouble]
                  })
                  value: number = 1;

                  @TypedAttributeMark({
                    mode: Mode.CONSTRUCTOR,
                    type: JBoolean
                  })
                  constructor(
                    @TypedAttributeMark({
                      mode: Mode.PARAMETER,
                      type: JFloat
                    })
                    name: string
                  ) {
                    this.value = 2;
                  }

                  @TypedAttributeMark({
                    mode: Mode.METHOD,
                    type: JShort,
                    modes: [Mode.METHOD],
                    types: [JShort, JByte]
                  })
                  greet(
                    @TypedAttributeMark({
                      mode: Mode.PARAMETER,
                      type: JByte
                    })
                    prefix: string
                  ) {
                    return prefix + this.value;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-typed-annotation-values-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        final Path executableClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/AnnotatedShape__TsjStrictNative.class");
        assertTrue(Files.exists(executableClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.AnnotatedShape__TsjStrictNative",
                    true,
                    classLoader
            );
            final TypedAttributeMark classMark = executableClassType.getAnnotation(TypedAttributeMark.class);
            assertEquals(TypedAttributeMode.TYPE, classMark.mode());
            assertEquals(String.class, classMark.type());
            assertArrayEquals(
                    new TypedAttributeMode[]{TypedAttributeMode.TYPE, TypedAttributeMode.METHOD},
                    classMark.modes()
            );
            assertArrayEquals(new Class<?>[]{String.class, Integer.class}, classMark.types());

            final TypedAttributeMark fieldMark =
                    executableClassType.getDeclaredField("value").getAnnotation(TypedAttributeMark.class);
            assertEquals(TypedAttributeMode.FIELD, fieldMark.mode());
            assertEquals(Long.class, fieldMark.type());
            assertArrayEquals(new Class<?>[]{Long.class, Double.class}, fieldMark.types());

            final java.lang.reflect.Constructor<?> constructor = executableClassType.getDeclaredConstructor(String.class);
            final TypedAttributeMark constructorMark = constructor.getAnnotation(TypedAttributeMark.class);
            assertEquals(TypedAttributeMode.CONSTRUCTOR, constructorMark.mode());
            assertEquals(Boolean.class, constructorMark.type());

            final TypedAttributeMark constructorParameterMark =
                    constructor.getParameters()[0].getAnnotation(TypedAttributeMark.class);
            assertEquals(TypedAttributeMode.PARAMETER, constructorParameterMark.mode());
            assertEquals(Float.class, constructorParameterMark.type());

            final java.lang.reflect.Method method = executableClassType.getDeclaredMethod("greet", String.class);
            final TypedAttributeMark methodMark = method.getAnnotation(TypedAttributeMark.class);
            assertEquals(TypedAttributeMode.METHOD, methodMark.mode());
            assertEquals(Short.class, methodMark.type());
            assertArrayEquals(new TypedAttributeMode[]{TypedAttributeMode.METHOD}, methodMark.modes());
            assertArrayEquals(new Class<?>[]{Short.class, Byte.class}, methodMark.types());

            final TypedAttributeMark methodParameterMark = method.getParameters()[0].getAnnotation(TypedAttributeMark.class);
            assertEquals(TypedAttributeMode.PARAMETER, methodParameterMark.mode());
            assertEquals(Byte.class, methodParameterMark.type());
        }
    }

    @Test
    void strictJvmExecutableClassPreservesRepeatableAnnotations() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-repeatable-annotations.ts");
        Files.writeString(
                sourceFile,
                """
                import { RepeatableTag } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.RepeatableTag";

                @RepeatableTag("type-a")
                @RepeatableTag("type-b")
                class RepeatableShape {
                  @RepeatableTag("method-a")
                  @RepeatableTag("method-b")
                  greet() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-repeatable-annotations-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.RepeatableShape__TsjStrictNative",
                    true,
                    classLoader
            );
            final RepeatableTag[] classTags = executableClassType.getAnnotationsByType(RepeatableTag.class);
            assertArrayEquals(new String[]{"type-a", "type-b"}, List.of(classTags).stream().map(RepeatableTag::value).toArray(String[]::new));

            final java.lang.reflect.Method greet = executableClassType.getDeclaredMethod("greet");
            final RepeatableTag[] methodTags = greet.getAnnotationsByType(RepeatableTag.class);
            assertArrayEquals(new String[]{"method-a", "method-b"}, List.of(methodTags).stream().map(RepeatableTag::value).toArray(String[]::new));
        }
    }

    @Test
    void strictJvmExecutableClassEmitsNestedAnnotationAttributes() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-nested-annotation-values.ts");
        Files.writeString(
                sourceFile,
                """
                import { NestedOuterMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.NestedOuterMark";

                @NestedOuterMark({
                  inner: { name: 'class-primary', count: 1 },
                  inners: [
                    { name: 'class-extra', count: 2 },
                    { name: 'class-last', count: 3 }
                  ]
                })
                class NestedShape {
                  @NestedOuterMark({
                    inner: { name: 'method-primary', count: 4 },
                    inners: [{ name: 'method-extra', count: 5 }]
                  })
                  greet() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-nested-annotation-values-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.NestedShape__TsjStrictNative",
                    true,
                    classLoader
            );
            final NestedOuterMark classMark = executableClassType.getAnnotation(NestedOuterMark.class);
            assertEquals("class-primary", classMark.inner().name());
            assertEquals(1, classMark.inner().count());
            assertEquals(2, classMark.inners().length);
            assertEquals("class-extra", classMark.inners()[0].name());
            assertEquals(2, classMark.inners()[0].count());
            assertEquals("class-last", classMark.inners()[1].name());
            assertEquals(3, classMark.inners()[1].count());

            final java.lang.reflect.Method greet = executableClassType.getDeclaredMethod("greet");
            final NestedOuterMark methodMark = greet.getAnnotation(NestedOuterMark.class);
            assertEquals("method-primary", methodMark.inner().name());
            assertEquals(4, methodMark.inner().count());
            assertEquals(1, methodMark.inners().length);
            assertEquals("method-extra", methodMark.inners()[0].name());
            assertEquals(5, methodMark.inners()[0].count());
        }
    }

    @Test
    void strictJvmExecutableClassExposesBeanPropertyDescriptors() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-bean-properties.ts");
        Files.writeString(
                sourceFile,
                """
                class BeanShape {
                  name: string;
                  city: string;

                  constructor(name: string, city: string) {
                    this.name = name;
                    this.city = city;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-bean-properties-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.BeanShape__TsjStrictNative",
                    true,
                    classLoader
            );
            java.beans.PropertyDescriptor nameDescriptor = null;
            java.beans.PropertyDescriptor cityDescriptor = null;
            for (java.beans.PropertyDescriptor descriptor : java.beans.Introspector.getBeanInfo(executableClassType)
                    .getPropertyDescriptors()) {
                if ("name".equals(descriptor.getName())) {
                    nameDescriptor = descriptor;
                } else if ("city".equals(descriptor.getName())) {
                    cityDescriptor = descriptor;
                }
            }

            assertTrue(nameDescriptor != null);
            assertEquals("getName", nameDescriptor.getReadMethod().getName());
            assertEquals("setName", nameDescriptor.getWriteMethod().getName());

            assertTrue(cityDescriptor != null);
            assertEquals("getCity", cityDescriptor.getReadMethod().getName());
            assertEquals("setCity", cityDescriptor.getWriteMethod().getName());

            final Object instance = executableClassType.getDeclaredConstructor(String.class, String.class)
                    .newInstance("Ada", "London");
            assertEquals("Ada", nameDescriptor.getReadMethod().invoke(instance));
            cityDescriptor.getWriteMethod().invoke(instance, "Paris");
            assertEquals("Paris", cityDescriptor.getReadMethod().invoke(instance));
        }
    }

    @Test
    void strictJvmExecutableClassEmitsClasspathNullabilityAnnotations() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-nullability.ts");
        Files.writeString(
                sourceFile,
                """
                class CustomerProfile {
                  requiredName: string;
                  nickname: string | null;

                  constructor(requiredName: string, nickname: string | null) {
                    this.requiredName = requiredName;
                    this.nickname = nickname;
                  }

                  primaryName(): string {
                    return this.requiredName;
                  }

                  displayName(prefix: string | null): string | null {
                    if (prefix === null) {
                      return null;
                    }
                    return prefix + this.requiredName;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-nullability-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        final JavaClassfileReader.RawClassInfo executableClass = new JavaClassfileReader().read(
                artifact.outputDirectory().resolve("dev/tsj/generated/CustomerProfile__TsjStrictNative.class")
        );

        final String nonNull = "Ljavax/annotation/Nonnull;";
        final String nullable = "Ljavax/annotation/Nullable;";

        final JavaClassfileReader.RawFieldInfo requiredName = executableClass.fields().stream()
                .filter(candidate -> "requiredName".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(hasAnnotation(requiredName.runtimeVisibleAnnotations(), requiredName.runtimeInvisibleAnnotations(), nonNull));

        final JavaClassfileReader.RawFieldInfo nickname = executableClass.fields().stream()
                .filter(candidate -> "nickname".equals(candidate.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(hasAnnotation(nickname.runtimeVisibleAnnotations(), nickname.runtimeInvisibleAnnotations(), nullable));

        final JavaClassfileReader.RawMethodInfo constructor = executableClass.methods().stream()
                .filter(candidate -> "<init>".equals(candidate.name()) && "(Ljava/lang/String;Ljava/lang/String;)V".equals(candidate.descriptor()))
                .findFirst()
                .orElseThrow();
        assertTrue(hasParameterAnnotation(constructor, 0, nonNull));
        assertTrue(hasParameterAnnotation(constructor, 1, nullable));

        final JavaClassfileReader.RawMethodInfo primaryName = executableClass.methods().stream()
                .filter(candidate -> "primaryName".equals(candidate.name()) && "()Ljava/lang/String;".equals(candidate.descriptor()))
                .findFirst()
                .orElseThrow();
        assertTrue(hasAnnotation(primaryName.runtimeVisibleAnnotations(), primaryName.runtimeInvisibleAnnotations(), nonNull));

        final JavaClassfileReader.RawMethodInfo displayName = executableClass.methods().stream()
                .filter(candidate -> "displayName".equals(candidate.name()) && "(Ljava/lang/String;)Ljava/lang/String;".equals(candidate.descriptor()))
                .findFirst()
                .orElseThrow();
        assertTrue(hasAnnotation(displayName.runtimeVisibleAnnotations(), displayName.runtimeInvisibleAnnotations(), nullable));
        assertTrue(hasParameterAnnotation(displayName, 0, nullable));
    }

    @Test
    void strictJvmExecutableClassPreservesGenericSignaturesAndProxyFriendlyShape() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-generic-shape.ts");
        Files.writeString(
                sourceFile,
                """
                class OwnerDirectory {
                  tags: Array<string>;

                  constructor(tags: Array<string>) {
                    this.tags = tags;
                  }

                  findAll(): Array<string> {
                    return this.tags;
                  }

                  byName(input: Record<string, string>): Array<string> {
                    return this.tags;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-generic-shape-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> executableClassType = Class.forName(
                    "dev.tsj.generated.OwnerDirectory__TsjStrictNative",
                    true,
                    classLoader
            );
            assertFalse(Modifier.isFinal(executableClassType.getModifiers()));

            final Field tags = executableClassType.getDeclaredField("tags");
            assertTrue(tags.getGenericType() instanceof ParameterizedType);
            assertEquals("java.util.List<java.lang.String>", tags.getGenericType().getTypeName());

            final Constructor<?> constructor = executableClassType.getDeclaredConstructor(List.class);
            assertEquals("tags", constructor.getParameters()[0].getName());
            assertEquals("java.util.List<java.lang.String>", constructor.getGenericParameterTypes()[0].getTypeName());

            final Method findAll = executableClassType.getDeclaredMethod("findAll");
            assertEquals("java.util.List<java.lang.String>", findAll.getGenericReturnType().getTypeName());

            final Method byName = executableClassType.getDeclaredMethod("byName", Map.class);
            assertEquals("input", byName.getParameters()[0].getName());
            assertEquals("java.util.Map<java.lang.String, java.lang.String>", byName.getGenericParameterTypes()[0].getTypeName());
            assertEquals("java.util.List<java.lang.String>", byName.getGenericReturnType().getTypeName());
        }
    }

    @Test
    void strictJvmNativeDtoSupportsJacksonRoundTripForFrameworkBoundaries() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-jackson-dto.ts");
        Files.writeString(
                sourceFile,
                """
                class PersonDto {
                  id: string = "";
                  name: string = "";
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-jackson-dto-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> dtoClass = Class.forName("dev.tsj.generated.PersonDto__TsjStrictNative", true, classLoader);
            final Object dto = dtoClass.getDeclaredConstructor().newInstance();
            dtoClass.getDeclaredMethod("setId", String.class).invoke(dto, "42");
            dtoClass.getDeclaredMethod("setName", String.class).invoke(dto, "Ada");
            final String serialized = OBJECT_MAPPER.writeValueAsString(dto);
            assertTrue(serialized.contains("\"id\":\"42\""));
            assertTrue(serialized.contains("\"name\":\"Ada\""));

            @SuppressWarnings("unchecked")
            final Class<Object> reboundType = (Class<Object>) dtoClass;
            final Object rebound = OBJECT_MAPPER.readValue(serialized, reboundType);
            final java.lang.reflect.Method getId = dtoClass.getDeclaredMethod("getId");
            final java.lang.reflect.Method getName = dtoClass.getDeclaredMethod("getName");
            assertEquals("42", getId.invoke(rebound));
            assertEquals("Ada", getName.invoke(rebound));
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsIfStatementInClassMethodBody() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-if-branch.ts");
        Files.writeString(
                sourceFile,
                """
                class Threshold {
                  value: number;

                  constructor(value: number) {
                    this.value = value;
                  }

                  normalize(limit: number) {
                    if (this.value > limit) {
                      this.value = limit;
                    } else {
                      this.value = this.value + 1;
                    }
                    return this.value;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-if-branch-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object high = invokeStrictInstanceMethod(
                    classLoader,
                    "Threshold",
                    "normalize",
                    new Object[]{5},
                    new Object[]{3}
            );
            final Object low = invokeStrictInstanceMethod(
                    classLoader,
                    "Threshold",
                    "normalize",
                    new Object[]{2},
                    new Object[]{3}
            );

            assertEquals(3.0d, ((Number) high).doubleValue());
            assertEquals(3.0d, ((Number) low).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsMemberCallsOnAutowiredLikeFields() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-member-invoke.ts");
        Files.writeString(
                sourceFile,
                """
                class Repository {
                  findNext(value: number) {
                    return value + 1;
                  }
                }

                class Service {
                  repository: Repository;

                  constructor(repository: Repository) {
                    this.repository = repository;
                  }

                  fetch(value: number) {
                    return this.repository.findNext(value);
                  }
                }

                const repository = new Repository();
                const service = new Service(repository);
                console.log("value=" + service.fetch(41));
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-member-invoke-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));
        assertEquals("value=42\n", stdout.toString(UTF_8));
    }

    @Test
    void strictJvmNativeSubsetSupportsMultiClassServiceChainWithArrayLiteralRepositoryResult() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-service-chain.ts");
        Files.writeString(
                sourceFile,
                """
                class Repo {
                  findAll() {
                    return ["Ada", "Linus"];
                  }
                }

                class Service {
                  repo: Repo;

                  list() {
                    return this.repo.findAll();
                  }
                }

                class Controller {
                  service: Service;

                  handle() {
                    return this.service.list();
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-service-chain-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> repoClass = Class.forName("dev.tsj.generated.Repo__TsjStrictNative", true, classLoader);
            final Object repo = repoClass.getDeclaredConstructor().newInstance();

            final Class<?> serviceClass = Class.forName("dev.tsj.generated.Service__TsjStrictNative", true, classLoader);
            final Object service = serviceClass.getDeclaredConstructor().newInstance();
            serviceClass.getDeclaredMethod("setRepo", repoClass).invoke(service, repo);

            final Class<?> controllerClass = Class.forName(
                    "dev.tsj.generated.Controller__TsjStrictNative",
                    true,
                    classLoader
            );
            final Object controller = controllerClass.getDeclaredConstructor().newInstance();
            controllerClass.getDeclaredMethod("setService", serviceClass).invoke(controller, service);

            final Object result = controllerClass.getDeclaredMethod("handle").invoke(controller);
            assertEquals(2.0d, ((Number) TsjRuntime.getProperty(result, "length")).doubleValue());
            assertEquals("Ada", TsjRuntime.getProperty(result, "0"));
            assertEquals("Linus", TsjRuntime.getProperty(result, "1"));
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsTryCatchFinallyInClassMethodBody() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-try-catch.ts");
        Files.writeString(
                sourceFile,
                """
                class TaskRunner {
                  attempts: number;

                  constructor() {
                    this.attempts = 0;
                  }

                  run(shouldFail: boolean) {
                    try {
                      if (shouldFail) {
                        throw "boom";
                      }
                      return "ok";
                    } catch (error) {
                      return "error=" + error;
                    } finally {
                      this.attempts = this.attempts + 1;
                    }
                  }

                  total() {
                    return this.attempts;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-try-catch-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> runnerClass = Class.forName("dev.tsj.generated.TaskRunner__TsjStrictNative", true, classLoader);
            final Object runner = runnerClass.getDeclaredConstructor().newInstance();

            assertEquals("ok", runnerClass.getDeclaredMethod("run", Boolean.class).invoke(runner, false));
            assertEquals("error=boom", runnerClass.getDeclaredMethod("run", Boolean.class).invoke(runner, true));
            assertEquals(2.0d, ((Number) runnerClass.getDeclaredMethod("total").invoke(runner)).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsWhileLoopInClassMethodBody() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-while.ts");
        Files.writeString(
                sourceFile,
                """
                class Accumulator {
                  total: number;

                  constructor() {
                    this.total = 0;
                  }

                  sum(limit: number) {
                    let current = 0;
                    while (current < limit) {
                      current = current + 1;
                      this.total = this.total + current;
                    }
                    return this.total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-while-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> accumulatorClass = Class.forName(
                    "dev.tsj.generated.Accumulator__TsjStrictNative",
                    true,
                    classLoader
            );
            final Object accumulator = accumulatorClass.getDeclaredConstructor().newInstance();
            final Object result = accumulatorClass.getDeclaredMethod("sum", Number.class).invoke(accumulator, 4);
            assertEquals(10.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsDerivedClassWithSuperConstructorAndInheritedDispatch() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-inheritance.ts");
        Files.writeString(
                sourceFile,
                """
                class PersonBase {
                  name: string;

                  constructor(name: string) {
                    this.name = name;
                  }

                  greet(prefix: string) {
                    return prefix + this.name;
                  }
                }

                class PersonController extends PersonBase {
                  constructor(name: string) {
                    super(name);
                  }

                  handle() {
                    return this.greet("hi ");
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-inheritance-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object handled = invokeStrictInstanceMethod(
                    classLoader,
                    "PersonController",
                    "handle",
                    new Object[]{"Ada"},
                    new Object[0]
            );
            final Object greeted = invokeStrictInstanceMethod(
                    classLoader,
                    "PersonController",
                    "greet",
                    new Object[]{"Ada"},
                    new Object[]{"hello "}
            );

            assertEquals("hi Ada", handled);
            assertEquals("hello Ada", greeted);
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsSuperMemberCallsInDerivedMethods() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-super-member.ts");
        Files.writeString(
                sourceFile,
                """
                class PersonBase {
                  greet(prefix: string) {
                    return prefix + "Ada";
                  }
                }

                class PersonController extends PersonBase {
                  greet(prefix: string) {
                    return super.greet(prefix) + "!";
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-super-member-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object greeted = invokeStrictInstanceMethod(
                    classLoader,
                    "PersonController",
                    "greet",
                    new Object[0],
                    new Object[]{"hi "}
            );

            assertEquals("hi Ada!", greeted);
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsLexicalArrowFunctionInsideClassMethod() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-arrow.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  base: number;

                  constructor(base: number) {
                    this.base = base;
                  }

                  compute(input: number) {
                    const addBase = (value: number) => {
                      return value + this.base;
                    };
                    return addBase(input);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-arrow-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[]{2},
                    new Object[]{40}
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsLexicalArrowCapturingOuterLocalAndParameter() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-arrow-capture.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute(input: number) {
                    const offset = 1;
                    const addBoth = (value: number) => {
                      return value + input + offset;
                    };
                    return addBoth(1);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-arrow-capture-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[]{40}
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsFunctionExpressionWithCapturedLocalAndDynamicThis() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-function-expression.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute(input: number) {
                    const offset = 1;
                    const holder = {
                      base: 10,
                      add: function(value: number) {
                        this.base = this.base + offset;
                        return this.base + value;
                      }
                    };
                    return holder.add(input);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-function-expression-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[]{31}
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsBreakAndContinueInWhileLoop() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-break-continue.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 0;
                    let index = 0;
                    while (index < 5) {
                      index = index + 1;
                      if (index == 2) {
                        continue;
                      }
                      if (index == 5) {
                        break;
                      }
                      total = total + index;
                    }
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-break-continue-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(8.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsDoWhileLoopWithoutContinue() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-do-while.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 0;
                    let index = 0;
                    do {
                      total = total + index;
                      index = index + 1;
                    } while (index < 3);
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-do-while-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(3.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsLocalFunctionDeclarationHoistingAndCapture() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-local-function.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute(input: number) {
                    const offset = 1;
                    const result = add(input);

                    function add(value: number) {
                      return value + offset;
                    }

                    return result;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-local-function-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[]{41}
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsForOfLoopOverArrayLiteral() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-for-of.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 0;
                    for (const value of [1, 2, 3]) {
                      total = total + value;
                    }
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-for-of-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(6.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsForInLoopOverObjectLiteral() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-for-in.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let keys = "";
                    for (const key in { first: 1, second: 2 }) {
                      keys = keys + key + ";";
                    }
                    return keys;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-for-in-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals("first;second;", result);
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsForOfLoopWithDestructuredArrayBinding() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-for-of-destructure.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 0;
                    for (const [left, right] of [[1, 2], [3, 4]]) {
                      total = total + left + right;
                    }
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-for-of-destructure-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(10.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsForOfLoopWithDestructuredObjectBinding() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-for-of-object-destructure.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 0;
                    for (const { left, right } of [{ left: 1, right: 2 }, { left: 3, right: 4 }]) {
                      total = total + left + right;
                    }
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-for-of-object-destructure-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(10.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsLabeledBreakAndContinueInWhileLoops() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-labeled-loop-control.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let outer = 0;
                    let total = 0;

                    outerLoop: while (outer < 4) {
                      outer = outer + 1;
                      let inner = 0;

                      while (inner < 4) {
                        inner = inner + 1;
                        if (inner == 2) {
                          continue outerLoop;
                        }
                        if (outer == 4) {
                          break outerLoop;
                        }
                        total = total + 1;
                      }
                    }

                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-labeled-loop-control-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(3.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsDoWhileLoopWithContinue() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-do-while-continue.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 0;
                    let index = 0;
                    do {
                      index = index + 1;
                      if (index == 2) {
                        continue;
                      }
                      total = total + index;
                    } while (index < 4);
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-do-while-continue-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(8.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsLocalObjectPropertyMutation() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-local-object-mutation.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    const state = { count: 1 };
                    state.count = state.count + 41;
                    return state.count;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-local-object-mutation-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsIndexAssignmentTargets() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-index-assignment.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    const key = "count";
                    const state = { count: 1 };
                    state[key] = state[key] + 41;
                    const values = [0, 0, 0];
                    values[1] = 5;
                    return state["count"] + values[1];
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-index-assignment-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(47.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsAssignmentExpressionResult() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-assignment-expression.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    const state = { count: 1 };
                    return (state.count = state.count + 41);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-assignment-expression-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsCompoundAssignmentExpressionResults() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-compound-assignment-expression.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let total = 1;
                    const state = { count: 1 };
                    const key = "count";
                    const values = [0, 0];
                    return (total += 40) + (state.count += 1) + (state[key] += 2) + (values[1] += 3);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-compound-assignment-expression-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(50.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsLogicalCompoundAssignmentExpressionResults() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-logical-compound-assignment-expression.ts");
        Files.writeString(
                sourceFile,
                """
                class Calculator {
                  compute() {
                    let nullable = undefined;
                    let seeded = "seed";
                    let zero = 0;
                    return "" + (nullable ??= "fallback")
                      + "|"
                      + (seeded &&= "next")
                      + "|"
                      + (zero ||= 5);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-logical-compound-assignment-expression-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals("fallback|next|5", result);
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsOptionalMemberAccessAndOptionalCall() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-optional-chain.ts");
        Files.writeString(
                sourceFile,
                """
                class Reader {
                  compute() {
                    const holder = {
                      value: 4,
                      read: () => "ok"
                    };
                    const missing = undefined;
                    const missingFn = undefined;
                    return "" + holder?.value
                      + "|"
                      + missing?.value
                      + "|"
                      + holder.read?.()
                      + "|"
                      + missingFn?.();
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-optional-chain-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Reader",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals("4|undefined|ok|undefined", result);
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsNewExpressionForStrictNativeClassNames() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-new-expression.ts");
        Files.writeString(
                sourceFile,
                """
                class Point {
                  x: number;
                  y: number;

                  constructor(x: number, y: number) {
                    this.x = x;
                    this.y = y;
                  }

                  sum() {
                    return this.x + this.y;
                  }
                }

                class Factory {
                  compute() {
                    const point = new Point(20, 22);
                    return point.sum();
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-new-expression-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Factory",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsTopLevelConstAndFunctionBindings() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-top-level-bindings.ts");
        Files.writeString(
                sourceFile,
                """
                const BASE = 41;

                function next(value: number) {
                  return value + 1;
                }

                class Calculator {
                  compute() {
                    return next(BASE);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-top-level-bindings-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsTopLevelLetMutation() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-top-level-let-mutation.ts");
        Files.writeString(
                sourceFile,
                """
                let total = 40;

                class Counter {
                  compute() {
                    total = total + 1;
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-top-level-let-mutation-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object first = invokeStrictInstanceMethod(
                    classLoader,
                    "Counter",
                    "compute",
                    new Object[0],
                    new Object[0]
            );
            final Object second = invokeStrictInstanceMethod(
                    classLoader,
                    "Counter",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(41.0d, ((Number) first).doubleValue());
            assertEquals(42.0d, ((Number) second).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsImportedConstAndFunctionBindings() throws Exception {
        final Path helperFile = tempDir.resolve("strict-native-imported-bindings-helper.ts");
        Files.writeString(
                helperFile,
                """
                export const BASE = 41;

                export function next(value: number) {
                  return value + 1;
                }
                """,
                UTF_8
        );
        final Path sourceFile = tempDir.resolve("strict-native-imported-bindings-main.ts");
        Files.writeString(
                sourceFile,
                """
                import { BASE, next } from "./strict-native-imported-bindings-helper";

                class Calculator {
                  compute() {
                    return next(BASE);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-imported-bindings-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Calculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetKeepsModuleBindingScopesSeparatedAcrossModules() throws Exception {
        final Path leftFile = tempDir.resolve("strict-native-module-scope-left.ts");
        Files.writeString(
                leftFile,
                """
                const BASE = 40;

                export class LeftCalculator {
                  compute() {
                    return BASE + 1;
                  }
                }
                """,
                UTF_8
        );
        final Path rightFile = tempDir.resolve("strict-native-module-scope-right.ts");
        Files.writeString(
                rightFile,
                """
                const BASE = 7;

                export class RightCalculator {
                  compute() {
                    return BASE + 1;
                  }
                }
                """,
                UTF_8
        );
        final Path sourceFile = tempDir.resolve("strict-native-module-scope-main.ts");
        Files.writeString(
                sourceFile,
                """
                import { LeftCalculator } from "./strict-native-module-scope-left";
                import { RightCalculator } from "./strict-native-module-scope-right";

                export { LeftCalculator, RightCalculator };
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-module-scope-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object left = invokeStrictInstanceMethod(
                    classLoader,
                    "LeftCalculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );
            final Object right = invokeStrictInstanceMethod(
                    classLoader,
                    "RightCalculator",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(41.0d, ((Number) left).doubleValue());
            assertEquals(8.0d, ((Number) right).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetPreservesLiveImportedLetBindingsAcrossModuleCalls() throws Exception {
        final Path helperFile = tempDir.resolve("strict-native-live-import-helper.ts");
        Files.writeString(
                helperFile,
                """
                export let total = 40;

                export function bump() {
                  total = total + 1;
                  return total;
                }
                """,
                UTF_8
        );
        final Path sourceFile = tempDir.resolve("strict-native-live-import-main.ts");
        Files.writeString(
                sourceFile,
                """
                import { total, bump } from "./strict-native-live-import-helper";

                class Counter {
                  compute() {
                    bump();
                    return total;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-live-import-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object first = invokeStrictInstanceMethod(
                    classLoader,
                    "Counter",
                    "compute",
                    new Object[0],
                    new Object[0]
            );
            final Object second = invokeStrictInstanceMethod(
                    classLoader,
                    "Counter",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(41.0d, ((Number) first).doubleValue());
            assertEquals(42.0d, ((Number) second).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsNewExpressionForImportedStrictNativeClassAlias() throws Exception {
        final Path helperFile = tempDir.resolve("strict-native-imported-constructor-helper.ts");
        Files.writeString(
                helperFile,
                """
                export class Point {
                  x: number;
                  y: number;

                  constructor(x: number, y: number) {
                    this.x = x;
                    this.y = y;
                  }

                  sum() {
                    return this.x + this.y;
                  }
                }
                """,
                UTF_8
        );
        final Path sourceFile = tempDir.resolve("strict-native-imported-constructor-main.ts");
        Files.writeString(
                sourceFile,
                """
                import { Point as ImportedPoint } from "./strict-native-imported-constructor-helper";

                class Factory {
                  compute() {
                    const point = new ImportedPoint(20, 22);
                    return point.sum();
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-imported-constructor-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Factory",
                    "compute",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmExecutableClassSupportsStaticMethodEmission() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-static-method.ts");
        Files.writeString(
                sourceFile,
                """
                class Metrics {
                  static twice(value: number) {
                    return value * 2;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-static-method-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> metricsClass = Class.forName("dev.tsj.generated.Metrics__TsjStrictNative", true, classLoader);
            final Method twice = metricsClass.getDeclaredMethod("twice", Number.class);
            assertTrue(Modifier.isStatic(twice.getModifiers()));
            assertEquals(42.0d, ((Number) twice.invoke(null, 21)).doubleValue());
        }
    }

    @Test
    void strictJvmExecutableClassCanPassSelfAsJavaLangClassToInteropCall() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-application-class-value.ts");
        Files.writeString(
                sourceFile,
                """
                import { describeApplicationClass } from "java:dev.tsj.compiler.backend.jvm.fixtures.boot.StrictAppInteropProbe";

                class BootApp {
                  static describe() {
                    return describeApplicationClass(BootApp);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-application-class-value-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> bootAppClass = Class.forName("dev.tsj.generated.BootApp__TsjStrictNative", true, classLoader);
            final Method describe = bootAppClass.getDeclaredMethod("describe");
            assertTrue(Modifier.isStatic(describe.getModifiers()));
            assertEquals("dev.tsj.generated.BootApp__TsjStrictNative", describe.invoke(null));
        }
    }

    @Test
    void strictJvmExecutableClassCanExposeJvmMainSignature() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-application-main.ts");
        Files.writeString(
                sourceFile,
                """
                class BootApp {
                  static main(args: string[]) {
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-application-main-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> bootAppClass = Class.forName("dev.tsj.generated.BootApp__TsjStrictNative", true, classLoader);
            final Method main = bootAppClass.getDeclaredMethod("main", String[].class);
            assertTrue(Modifier.isPublic(main.getModifiers()));
            assertTrue(Modifier.isStatic(main.getModifiers()));
            assertEquals(void.class, main.getReturnType());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsInstanceFieldInitializers() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-field-initializers.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  base: number = 40;
                  extra: number = 2;

                  total() {
                    return this.base + this.extra;
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-field-initializers-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "Counter",
                    "total",
                    new Object[0],
                    new Object[0]
            );

            assertEquals(42.0d, ((Number) result).doubleValue());
        }
    }

    @Test
    void strictJvmExecutableClassSupportsStaticFieldEmissionAndInitialization() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-static-field.ts");
        Files.writeString(
                sourceFile,
                """
                class Metrics {
                  static base: number = 42;
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-static-field-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> metricsClass = Class.forName("dev.tsj.generated.Metrics__TsjStrictNative", true, classLoader);
            final Field base = metricsClass.getDeclaredField("base");
            base.setAccessible(true);
            assertTrue(Modifier.isStatic(base.getModifiers()));
            assertEquals(42.0d, ((Number) base.get(null)).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsStaticMemberAccessByClassName() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-static-member-access.ts");
        Files.writeString(
                sourceFile,
                """
                class Metrics {
                  static base: number = 40;

                  static twice(value: number) {
                    return value * 2;
                  }

                  static total() {
                    return Metrics.base + Metrics.twice(1);
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-static-member-access-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> metricsClass = Class.forName("dev.tsj.generated.Metrics__TsjStrictNative", true, classLoader);
            final Method total = metricsClass.getDeclaredMethod("total");
            assertTrue(Modifier.isStatic(total.getModifiers()));
            assertEquals(42.0d, ((Number) total.invoke(null)).doubleValue());
        }
    }

    @Test
    void strictJvmNativeSubsetSupportsStaticWritesAndCrossClassStaticAccess() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-native-cross-class-static.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  static total: number = 40;

                  static inc() {
                    Counter.total = Counter.total + 1;
                    return Counter.total;
                  }
                }

                class Reporter {
                  static read() {
                    return Counter.total + Counter.inc();
                  }
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("strict-native-cross-class-static-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> reporterClass = Class.forName("dev.tsj.generated.Reporter__TsjStrictNative", true, classLoader);
            final Method read = reporterClass.getDeclaredMethod("read");
            assertTrue(Modifier.isStatic(read.getModifiers()));
            assertEquals(81.0d, ((Number) read.invoke(null)).doubleValue());
        }
    }

    @Test
    void strictJvmNativeRegistersTopLevelClassesFromBundledModuleInitializers() throws Exception {
        final Path moduleFile = tempDir.resolve("strict-runtime-carrier-controller.ts");
        final Path entryFile = tempDir.resolve("strict-runtime-carrier-main.ts");
        Files.writeString(
                moduleFile,
                """
                export class PetClinicController {
                  listOwners() {
                    return "ok";
                  }
                }
                """,
                UTF_8
        );
        Files.writeString(entryFile, "import \"./strict-runtime-carrier-controller.ts\";\n", UTF_8);

        final Path outDir = tempDir.resolve("strict-runtime-carrier-main-out");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                entryFile,
                outDir,
                JvmOptimizationOptions.defaults(),
                JvmBytecodeCompiler.BackendMode.JVM_STRICT
        );

        assertEquals("jvm-native-class-subset", artifact.strictLoweringPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Object result = invokeStrictInstanceMethod(
                    classLoader,
                    "PetClinicController",
                    "listOwners",
                    new Object[0],
                    new Object[0]
            );
            assertEquals("ok", result);
        }
    }

    @Test
    void strictJvmModeRejectsClassMethodsThatRequireRuntimeCarrierFallback() throws Exception {
        final Path sourceFile = tempDir.resolve("strict-bridge-unsupported.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  value: number;

                  constructor(initial: number) {
                    this.value = initial;
                  }

                  reset() {
                    return delete this.value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(
                        sourceFile,
                        tempDir.resolve("strict-bridge-unsupported-out"),
                        JvmOptimizationOptions.defaults(),
                        JvmBytecodeCompiler.BackendMode.JVM_STRICT
                )
        );

        assertEquals("TSJ-STRICT-BRIDGE", exception.code());
        assertEquals("TSJ80-STRICT-BRIDGE", exception.featureId());
    }

    @Test
    void emitsSourceMapFileForGeneratedProgram() throws Exception {
        final Path sourceFile = tempDir.resolve("source-map.ts");
        Files.writeString(
                sourceFile,
                """
                function fail(value: number) {
                  if (value === 1) {
                    throw "boom";
                  }
                  return value;
                }
                fail(1);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("map-out"));
        final String sourceMap = Files.readString(artifact.sourceMapFile(), UTF_8);

        assertTrue(Files.exists(artifact.sourceMapFile()));
        assertTrue(sourceMap.startsWith("TSJ-SOURCE-MAP\t1"));
        assertTrue(sourceMap.contains("source-map.ts"));
    }

    @Test
    void emitsLoadableMetadataCarrierClassForTopLevelTsClass() throws Exception {
        final Path sourceFile = tempDir.resolve("carrier.ts");
        Files.writeString(
                sourceFile,
                """
                class Person {
                  name: string = "";

                  constructor(name: string, age: number) {
                    this.name = name;
                  }

                  greet(prefix: string) {
                    return prefix + this.name;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("carrier-out"));
        final Path carrierClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/metadata/PersonTsjCarrier.class");
        assertTrue(Files.exists(carrierClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> carrierClassType = Class.forName(
                    "dev.tsj.generated.metadata.PersonTsjCarrier",
                    true,
                    classLoader
            );

            assertEquals(Object.class, carrierClassType.getDeclaredField("name").getType());
            final java.lang.reflect.Constructor<?> constructor =
                    carrierClassType.getDeclaredConstructor(Object.class, Object.class);
            assertEquals("name", constructor.getParameters()[0].getName());
            assertEquals("age", constructor.getParameters()[1].getName());

            final java.lang.reflect.Method greet = carrierClassType.getDeclaredMethod("greet", Object.class);
            assertEquals(Object.class, greet.getReturnType());
            assertEquals("prefix", greet.getParameters()[0].getName());
        }
    }

    @Test
    void emitsDefaultConstructorForMetadataCarrierWhenTsClassHasNoConstructor() throws Exception {
        final Path sourceFile = tempDir.resolve("carrier-default-ctor.ts");
        Files.writeString(
                sourceFile,
                """
                class EmptyCarrierTarget {
                  value: number = 1;
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("carrier-default-ctor-out"));
        final Path carrierClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/metadata/EmptyCarrierTargetTsjCarrier.class");
        assertTrue(Files.exists(carrierClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> carrierClassType = Class.forName(
                    "dev.tsj.generated.metadata.EmptyCarrierTargetTsjCarrier",
                    true,
                    classLoader
            );
            carrierClassType.getDeclaredConstructor();
        }
    }

    @Test
    void emitsImportedRuntimeAnnotationsOnMetadataCarrierClassMembersAndParameters() throws Exception {
        final Path sourceFile = tempDir.resolve("carrier-annotations.ts");
        Files.writeString(
                sourceFile,
                """
                import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
                import { CtorMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark";
                import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                @TypeMark
                class AnnotatedCarrierTarget {
                  @FieldMark
                  value: number = 1;

                  @CtorMark
                  constructor(@ParamMark name: string) {
                    this.value = 2;
                  }

                  @MethodMark
                  greet(@ParamMark prefix: string) {
                    return prefix + this.value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("carrier-annotations-out"));
        final Path carrierClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/metadata/AnnotatedCarrierTargetTsjCarrier.class");
        assertTrue(Files.exists(carrierClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> carrierClassType = Class.forName(
                    "dev.tsj.generated.metadata.AnnotatedCarrierTargetTsjCarrier",
                    true,
                    classLoader
            );
            assertTrue(carrierClassType.isAnnotationPresent(TypeMark.class));
            assertTrue(carrierClassType.getDeclaredField("value").isAnnotationPresent(FieldMark.class));

            final java.lang.reflect.Constructor<?> constructor = carrierClassType.getDeclaredConstructor(Object.class);
            assertTrue(constructor.isAnnotationPresent(CtorMark.class));
            assertTrue(constructor.getParameters()[0].isAnnotationPresent(ParamMark.class));

            final java.lang.reflect.Method method = carrierClassType.getDeclaredMethod("greet", Object.class);
            assertTrue(method.isAnnotationPresent(MethodMark.class));
            assertTrue(method.getParameters()[0].isAnnotationPresent(ParamMark.class));
        }
    }

    @Test
    void emitsImportedRuntimeAnnotationAttributeValuesOnMetadataCarrierMembers() throws Exception {
        final Path sourceFile = tempDir.resolve("carrier-annotation-values.ts");
        Files.writeString(
                sourceFile,
                """
                import { RichMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.RichMark";

                @RichMark('class-value')
                class RichCarrierTarget {
                  @RichMark({ name: 'field-name', count: 2 })
                  value: number = 1;

                  @RichMark({ tags: ['ctor', 'init'] })
                  constructor(@RichMark('ctor-param') name: string) {
                    this.value = 2;
                  }

                  @RichMark({ tags: ['method', 'value'] })
                  greet(@RichMark({ name: 'param-name', count: 9 }) prefix: string) {
                    return prefix + this.value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("carrier-annotation-values-out"));
        final Path carrierClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/metadata/RichCarrierTargetTsjCarrier.class");
        assertTrue(Files.exists(carrierClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> carrierClassType = Class.forName(
                    "dev.tsj.generated.metadata.RichCarrierTargetTsjCarrier",
                    true,
                    classLoader
            );
            final RichMark classMark = carrierClassType.getAnnotation(RichMark.class);
            assertEquals("class-value", classMark.value());

            final RichMark fieldMark = carrierClassType.getDeclaredField("value").getAnnotation(RichMark.class);
            assertEquals("field-name", fieldMark.name());
            assertEquals(2, fieldMark.count());

            final java.lang.reflect.Constructor<?> constructor = carrierClassType.getDeclaredConstructor(Object.class);
            final RichMark constructorMark = constructor.getAnnotation(RichMark.class);
            assertArrayEquals(new String[]{"ctor", "init"}, constructorMark.tags());

            final RichMark constructorParameterMark =
                    constructor.getParameters()[0].getAnnotation(RichMark.class);
            assertEquals("ctor-param", constructorParameterMark.value());

            final java.lang.reflect.Method method = carrierClassType.getDeclaredMethod("greet", Object.class);
            final RichMark methodMark = method.getAnnotation(RichMark.class);
            assertArrayEquals(new String[]{"method", "value"}, methodMark.tags());

            final RichMark methodParameterMark = method.getParameters()[0].getAnnotation(RichMark.class);
            assertEquals("param-name", methodParameterMark.name());
            assertEquals(9, methodParameterMark.count());
        }
    }

    @Test
    void emitsImportedRuntimeAnnotationsOnMetadataCarrierForMultilineCtorMethodAndDefiniteField() throws Exception {
        final Path sourceFile = tempDir.resolve("carrier-multiline-annotations.ts");
        Files.writeString(
                sourceFile,
                """
                import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
                import { CtorMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark";
                import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                @TypeMark
                class ComplexCarrierTarget {
                  @FieldMark
                  value!: number;

                  @CtorMark
                  constructor(
                    @ParamMark name: string,
                  ) {
                    this.value = 2;
                  }

                  @MethodMark
                  greet(
                    @ParamMark prefix: string,
                  ) {
                    return prefix + this.value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("carrier-multiline-annotations-out"));
        final Path carrierClass = artifact.outputDirectory()
                .resolve("dev/tsj/generated/metadata/ComplexCarrierTargetTsjCarrier.class");
        assertTrue(Files.exists(carrierClass));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{artifact.outputDirectory().toUri().toURL()})) {
            final Class<?> carrierClassType = Class.forName(
                    "dev.tsj.generated.metadata.ComplexCarrierTargetTsjCarrier",
                    true,
                    classLoader
            );
            assertTrue(carrierClassType.isAnnotationPresent(TypeMark.class));
            assertTrue(carrierClassType.getDeclaredField("value").isAnnotationPresent(FieldMark.class));

            final java.lang.reflect.Constructor<?> constructor = carrierClassType.getDeclaredConstructor(Object.class);
            assertTrue(constructor.isAnnotationPresent(CtorMark.class));
            assertTrue(constructor.getParameters()[0].isAnnotationPresent(ParamMark.class));

            final java.lang.reflect.Method method = carrierClassType.getDeclaredMethod("greet", Object.class);
            assertTrue(method.isAnnotationPresent(MethodMark.class));
            assertTrue(method.getParameters()[0].isAnnotationPresent(ParamMark.class));
        }
    }

    @Test
    void supportsIfElseAndWhileControlFlow() throws Exception {
        final Path sourceFile = tempDir.resolve("control-flow.ts");
        Files.writeString(
                sourceFile,
                """
                let sum = 0;
                let i = 1;
                while (i <= 5) {
                  sum = sum + i;
                  i = i + 1;
                }
                if (sum === 15) {
                  console.log("ok " + sum);
                } else {
                  console.log("bad " + sum);
                }
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("build");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, outDir);

        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("ok 15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDoWhileControlFlowInTsj59Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("do-while-control-flow.ts");
        Files.writeString(
                sourceFile,
                """
                let sum = 0;
                let i = 1;
                do {
                  sum = sum + i;
                  i = i + 1;
                } while (i <= 4);
                console.log("do=" + sum);
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("do-while-build");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, outDir);
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("do=10\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsContinueTargetingDoWhileLoopInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("do-while-continue.ts");
        Files.writeString(
                sourceFile,
                """
                let i = 0;
                let sum = 0;
                do {
                  i = i + 1;
                  if (i === 2) {
                    continue;
                  }
                  sum = sum + i;
                } while (i < 3);
                console.log(sum);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("do-while-supported"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsElseBranchWhenConditionIsFalse() throws Exception {
        final Path sourceFile = tempDir.resolve("else-branch.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 7;
                if (value === 8) {
                  console.log("match");
                } else {
                  console.log("no-match");
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out3"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("no-match\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsReassignmentAndUnaryNegation() throws Exception {
        final Path sourceFile = tempDir.resolve("reassign.ts");
        Files.writeString(
                sourceFile,
                """
                let score = 10;
                score = score + 5;
                const delta = -2;
                if (score + delta > 10) {
                  console.log("high " + score);
                } else {
                  console.log("low " + score);
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out4"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("high 15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsGeneratorFunctionsWithYieldYieldStarAndNextArguments() throws Exception {
        final Path sourceFile = tempDir.resolve("generators.ts");
        Files.writeString(
                sourceFile,
                """
                function* count() {
                  yield 1;
                  yield 2;
                  return 3;
                }
                const c = count();
                const s1 = c.next();
                const s2 = c.next();
                const s3 = c.next();
                console.log("basic=" + (s1.value === 1 && s2.value === 2 && s3.value === 3 && s3.done === true));

                function* accumulator() {
                  let total = 0;
                  while (true) {
                    const step: number = yield total;
                    total = total + step;
                  }
                }
                const acc = accumulator();
                acc.next();
                acc.next(10);
                const accStep = acc.next(20);
                console.log("nextArg=" + (accStep.value === 30));

                function* naturals() {
                  let n = 1;
                  while (true) {
                    yield n++;
                  }
                }
                const n = naturals();
                const first = n.next().value;
                const second = n.next().value;
                const third = n.next().value;
                console.log("naturals=" + (first === 1 && second === 2 && third === 3));

                function* inner() {
                  yield "a";
                  yield "b";
                }
                function* outer() {
                  yield "start";
                  yield* inner();
                  yield "end";
                }
                const values: string[] = [];
                for (const value of outer()) {
                  values.push(value);
                }
                console.log("yieldStar=" + (values.length === 4 && values[0] === "start" && values[1] === "a" && values[3] === "end"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("generators-out"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                basic=true
                nextArg=true
                naturals=true
                yieldStar=true
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsBigIntAndExtendedNumericLiteralFormsInTsjGrammarPath() throws Exception {
        final Path sourceFile = tempDir.resolve("numeric-literals.ts");
        Files.writeString(
                sourceFile,
                """
                const million = 1_000_000;
                const mask = 0b1010_1010;
                const hex = 0xCAFE_F00D;
                const big = 123n;
                console.log(million);
                console.log(mask);
                console.log(hex);
                console.log(big);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("numeric-out"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("1000000\n170\n3405705229\n123\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsUnaryPlusExponentBitwiseShiftAndTypeRelationOperatorsInTsjGrammarPath() throws Exception {
        final Path sourceFile = tempDir.resolve("operators.ts");
        Files.writeString(
                sourceFile,
                """
                class Box {
                }

                const unary = +"5";
                const exp = 2 ** 3;
                const left = 1 << 3;
                const right = 9 >> 1;
                const unsigned = -1 >>> 0;
                const band = 6 & 3;
                const bxor = 6 ^ 2;
                const bor = 4 | 1;
                const kind = typeof 1;
                const has = "a" in { a: 1 };
                const isBox = new Box() instanceof Box;
                console.log("unary=" + unary);
                console.log("exp=" + exp);
                console.log("left=" + left);
                console.log("right=" + right);
                console.log("unsigned=" + unsigned);
                console.log("band=" + band);
                console.log("bxor=" + bxor);
                console.log("bor=" + bor);
                console.log("typeof=" + kind);
                console.log("in=" + has);
                console.log("instanceof=" + isBox);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("operators-out"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "unary=5\n"
                        + "exp=8\n"
                        + "left=8\n"
                        + "right=4\n"
                        + "unsigned=4294967295\n"
                        + "band=2\n"
                        + "bxor=4\n"
                        + "bor=5\n"
                        + "typeof=number\n"
                        + "in=true\n"
                        + "instanceof=true\n",
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsTypeofOnUndeclaredIdentifierWithoutCompileFailure() throws Exception {
        final Path sourceFile = tempDir.resolve("typeof-undeclared.ts");
        Files.writeString(
                sourceFile,
                """
                console.log(typeof undeclaredVariable === "undefined");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("typeof-undeclared-out"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAssignmentExpressionInVariableInitializer() throws Exception {
        final Path sourceFile = tempDir.resolve("assignment-expression.ts");
        Files.writeString(
                sourceFile,
                """
                let value = 1;
                const captured = (value = 7);
                console.log(value);
                console.log(captured);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-assignment-expression"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("7\n7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsCompoundAssignmentOperators() throws Exception {
        final Path sourceFile = tempDir.resolve("compound-assignment.ts");
        Files.writeString(
                sourceFile,
                """
                let total = 10;
                total += 5;
                total -= 2;
                total *= 3;
                total /= 2;
                console.log(total);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-compound-assignment"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("19.5\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsBitwiseAndShiftCompoundAssignmentOperators() throws Exception {
        final Path sourceFile = tempDir.resolve("bitwise-compound-assignment.ts");
        Files.writeString(
                sourceFile,
                """
                let a = 0xFF;
                a &= 0x0F;
                let b = 0x00;
                b |= 0xFF;
                let c = 0xFF;
                c ^= 0x0F;
                let d = 1;
                d <<= 8;
                let e = 256;
                e >>= 4;
                let f = -1;
                f >>>= 0;
                console.log("and_eq=" + a);
                console.log("or_eq=" + b);
                console.log("xor_eq=" + c);
                console.log("shl_eq=" + d);
                console.log("shr_eq=" + e);
                console.log("ushr_eq=" + f);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-bitwise-compound-assignment"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "and_eq=15\n"
                        + "or_eq=255\n"
                        + "xor_eq=240\n"
                        + "shl_eq=256\n"
                        + "shr_eq=16\n"
                        + "ushr_eq=4294967295\n",
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsLogicalCompoundAssignments() throws Exception {
        final Path sourceFile = tempDir.resolve("logical-compound-assignment.ts");
        Files.writeString(
                sourceFile,
                """
                let nullable = null;
                nullable ??= "fallback";
                let orValue = false;
                orValue ||= "alt";
                let andValue = "seed";
                andValue &&= "next";
                console.log(nullable);
                console.log(orValue);
                console.log(andValue);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-logical-compound-assignment"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("fallback\nalt\nnext\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsElementAccessAssignmentTargets() throws Exception {
        final Path sourceFile = tempDir.resolve("element-access-assignment.ts");
        Files.writeString(
                sourceFile,
                """
                const key = "k";
                const obj: any = {};
                obj[key] = 41;
                obj["x"] = 1;
                const items = ["a", "b", "c"];
                const map: Record<string, number> = {};
                items.forEach((item, i) => { map[item] = i; });
                console.log("obj=" + (obj[key] === 41 && obj.x === 1));
                console.log("map=" + (map.a === 0 && map.c === 2));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-element-access-assignment"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("obj=true\nmap=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPostfixUpdateOnMemberAccessReturningPreviousValue() throws Exception {
        final Path sourceFile = tempDir.resolve("postfix-member-update.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  nextId = 1;
                  allocate() {
                    const id = this.nextId++;
                    return id;
                  }
                }
                const counter = new Counter();
                console.log("id1=" + counter.allocate());
                console.log("id2=" + counter.allocate());
                console.log("next=" + counter.nextId);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-postfix-member-update"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("id1=1\nid2=2\nnext=3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsOptionalMemberAccessAndOptionalCall() throws Exception {
        final Path sourceFile = tempDir.resolve("optional-chain.ts");
        Files.writeString(
                sourceFile,
                """
                const holder = {
                  value: 4,
                  read: () => "ok"
                };
                const maybeHolder = null;
                const maybeFn = undefined;

                console.log(holder?.value);
                console.log(maybeHolder?.value);
                console.log(holder.read?.());
                console.log(maybeFn?.());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-optional-chain"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("4\nundefined\nok\nundefined\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsOptionalPrimitiveMemberMethodCall() throws Exception {
        final Path sourceFile = tempDir.resolve("optional-primitive-member-call.ts");
        Files.writeString(
                sourceFile,
                """
                const str = "hello";
                const maybe = null;

                console.log(str?.toUpperCase());
                console.log(maybe?.toUpperCase());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-optional-primitive-member-call"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("HELLO\nundefined\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsOptionalElementAccessAndOptionalDeleteChains() throws Exception {
        final Path sourceFile = tempDir.resolve("optional-element-and-delete.ts");
        Files.writeString(
                sourceFile,
                """
                const values = [10, 20, 30];
                const maybeValues: any = values;
                const noValues: any = null;
                console.log("elem=" + (maybeValues?.[1] === 20));
                console.log("elem_null=" + (noValues?.[1] === undefined));

                const mutable: any = { a: { b: 1 } };
                delete mutable?.a?.b;
                console.log("delete_chain=" + (mutable.a.b === undefined));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-optional-element-and-delete"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("elem=true\nelem_null=true\ndelete_chain=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTemplateLiteralsWithInterpolation() throws Exception {
        final Path sourceFile = tempDir.resolve("template-literals.ts");
        Files.writeString(
                sourceFile,
                """
                const name = "tsj";
                const count = 3;
                console.log(`hello ${name} #${count}`);
                console.log(`plain`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-template-literals"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("hello tsj #3\nplain\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsMultilineTemplateLiteralsWithoutBreakingJavaEmission() throws Exception {
        final Path sourceFile = tempDir.resolve("template-literals-multiline.ts");
        Files.writeString(
                sourceFile,
                """
                const multi = `line1
                line2
                line3`;
                console.log(multi === "line1\\nline2\\nline3");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-template-literals-multiline"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTaggedTemplateThatUsesGlobalStringCoercionFunction() throws Exception {
        final Path sourceFile = tempDir.resolve("template-literals-tagged-string.ts");
        Files.writeString(
                sourceFile,
                """
                function tag(strings: TemplateStringsArray, ...values: any[]): string {
                  let result = "";
                  for (let i = 0; i < strings.length; i++) {
                    result += strings[i];
                    if (i < values.length) {
                      result += String(values[i]).toUpperCase();
                    }
                  }
                  return result;
                }

                const tagged = tag`hello ${"world"} and ${"test"}`;
                console.log(tagged);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-template-literals-tagged-string"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("hello WORLD and TEST\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsBigIntElementAccessAndGenericInstantiationExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("bigint-element-access.ts");
        Files.writeString(
                sourceFile,
                """
                function id<T>(value: T): T {
                  return value;
                }

                const arr = [5, 6];
                const first = arr[0];
                const big = 123n;
                const instantiate = id<string>;

                console.log(first);
                console.log(big);
                console.log(instantiate(9));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-bigint-element-access"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("5\n123\n9\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsBigIntLiteralTypeofAndConstructorInTsjRuntime() throws Exception {
        final Path sourceFile = tempDir.resolve("bigint-runtime.ts");
        Files.writeString(
                sourceFile,
                """
                const literal = 42n;
                const ctor = BigInt(7);
                console.log("type=" + typeof literal);
                console.log("ctor_type=" + typeof ctor);
                console.log("sum=" + (literal + ctor === 49n));
                console.log("str=" + (String(42n) === "42"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-bigint-runtime"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("type=bigint\nctor_type=bigint\nsum=true\nstr=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSymbolCreationRegistryAndSymbolPropertyKeys() throws Exception {
        final Path sourceFile = tempDir.resolve("symbol-runtime.ts");
        Files.writeString(
                sourceFile,
                """
                const s1 = Symbol("test");
                const s2 = Symbol("test");
                const shared1 = Symbol.for("shared");
                const shared2 = Symbol.for("shared");
                const key = Symbol("k");
                const obj: any = {};
                obj[key] = 41;

                console.log("unique=" + (s1 !== s2));
                console.log("global=" + (shared1 === shared2));
                console.log("key_for=" + (Symbol.keyFor(shared1) === "shared"));
                console.log("prop=" + (obj[key] === 41));
                console.log("typeof=" + (typeof key === "symbol"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-symbol-runtime"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("unique=true\nglobal=true\nkey_for=true\nprop=true\ntypeof=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsClassExpressionReturnedFromMixinFactory() throws Exception {
        final Path sourceFile = tempDir.resolve("class-expression-mixin.ts");
        Files.writeString(
                sourceFile,
                """
                function addTimestamp<T extends new (...args: any[]) => any>(Base: T) {
                  return class extends Base {
                    timestamp = 123;
                    getTimestamp() { return this.timestamp; }
                  };
                }

                class BaseEntity { name = "entity"; }
                const Enhanced = addTimestamp(BaseEntity);
                const inst = new Enhanced();
                console.log("name=" + inst.name);
                console.log("ts=" + inst.getTimestamp());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-class-expression-mixin"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("name=entity\nts=123\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNamespaceValueExportsInTsjGrammarPath() throws Exception {
        final Path sourceFile = tempDir.resolve("namespace-value-export.ts");
        Files.writeString(
                sourceFile,
                """
                namespace StressSpace {
                  export const defaultPayload = { id: "a", value: "b" };
                }

                console.log(StressSpace.defaultPayload.value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-namespace-value-export"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("b\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDestructuringInDeclarationsAssignmentsAndParameters() throws Exception {
        final Path sourceFile = tempDir.resolve("destructuring.ts");
        Files.writeString(
                sourceFile,
                """
                function sumPair([left, right]) {
                  return left + right;
                }

                function readTitle({ title }) {
                  return title;
                }

                const [first, second] = [3, 4];
                const { x, y } = { x: 1, y: 2, title: "ok" };
                console.log(`decl:${first}|${second}|${x}|${y}|${readTitle({ title: "ok" })}|${sumPair([5, 6])}`);

                let a = 0;
                let b = 0;
                [a, b] = [9, 8];
                let left = 0;
                let right = 0;
                ({ x: left, y: right } = { x: 1, y: 2 });
                console.log(`assign:${a}|${b}|${left}|${right}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-destructuring"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("decl:3|4|1|2|ok|11\nassign:9|8|1|2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDestructuringDefaultsAndRestAcrossDeclarationsAssignmentsParametersAndLoopHeaders() throws Exception {
        final Path sourceFile = tempDir.resolve("destructuring-defaults-rest.ts");
        Files.writeString(
                sourceFile,
                """
                function summarize({ a = 1, b = 2, ...rest }) {
                  return `${a}|${b}|${rest.c}|${rest.d}`;
                }

                const { x = 10, y, ...others } = { y: 20, z: 30, w: 40 };

                let first = 0;
                let tail = [];
                [first, ...tail] = [5, 6, 7];

                let picked = 0;
                let remaining = {};
                ({ z: picked, ...remaining } = { z: 9, q: 8, r: 7 });

                let loopTotal = 0;
                for (const [left, right = 0] of [[1, 2], [3]]) {
                  loopTotal += left + right;
                }

                console.log("params=" + summarize({ b: 5, c: 6, d: 7 }));
                console.log(`decl=${x}|${y}|${others.z}|${others.w}`);
                console.log(`assign=${first}|${tail[0]}|${tail[1]}|${picked}|${remaining.q}|${remaining.r}`);
                console.log("loop=" + loopTotal);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-destructuring-defaults-rest"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "params=1|5|6|7\ndecl=10|20|30|40\nassign=5|6|7|9|8|7\nloop=6\n",
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsArrayObjectAndCallSpreadSyntax() throws Exception {
        final Path sourceFile = tempDir.resolve("spread.ts");
        Files.writeString(
                sourceFile,
                """
                function sum4(a, b, c, d) {
                  return a + b + c + d;
                }

                const base = [2, 3];
                const combined = [1, ...base, 4];
                const [w, x, y, z] = combined;
                console.log(`array:${w}|${x}|${y}|${z}`);
                console.log(`call:${sum4(...combined)}`);

                const left = { a: 1 };
                const right = { b: 2, c: 3 };
                const merged = { ...left, ...right, d: 4 };
                const { a, b, c, d } = merged;
                console.log(`object:${a}|${b}|${c}|${d}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-spread"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("array:1|2|3|4\ncall:10\nobject:1|2|3|4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDefaultAndRestParametersAcrossFunctionsAndMethods() throws Exception {
        final Path sourceFile = tempDir.resolve("default-rest-params.ts");
        Files.writeString(
                sourceFile,
                """
                function summary(a = 10, b = a + 1, ...rest) {
                  const [first, second] = rest;
                  return `${a}|${b}|${rest.length}|${first ?? "none"}|${second ?? "none"}`;
                }

                const combine = (prefix = "P", ...parts) => {
                  const [head] = parts;
                  return `${prefix}-${head ?? "none"}-${parts.length}`;
                };

                class Runner {
                  run(seed = 1, ...tail) {
                    const [first] = tail;
                    return seed + (first ?? 0);
                  }
                }

                const runner = new Runner();
                console.log(summary());
                console.log(summary(undefined, undefined, 7, 8));
                console.log(summary(3, undefined, 9));
                console.log(summary(null, undefined));
                console.log(combine());
                console.log(combine(undefined, "x", "y"));
                console.log(`method:${runner.run(undefined, 4)}|${runner.run(2)}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-default-rest-params"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                10|11|0|none|none
                10|11|2|7|8
                3|4|1|9|none
                null|1|0|none|none
                P-none-0
                P-x-2
                method:5|2
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsComputedObjectPropertyNameFromStringConcatenation() throws Exception {
        final Path sourceFile = tempDir.resolve("computed-object-key.ts");
        Files.writeString(
                sourceFile,
                """
                const key = "a" + "b";
                const obj = { [key]: 99, ["x" + "y"]: 42 };
                console.log(`v:${obj.ab}|${obj.xy}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-computed-object-key"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("v:99|42\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsObjectLiteralGettersAndSetters() throws Exception {
        final Path sourceFile = tempDir.resolve("object-literal-accessors.ts");
        Files.writeString(
                sourceFile,
                """
                const person = {
                  _name: "Alice",
                  get name() { return this._name.toUpperCase(); }
                };

                const counter = {
                  _count: 0,
                  get count() { return this._count; },
                  set count(v: number) { this._count = v > 0 ? v : 0; }
                };

                counter.count = 5;
                const first = counter.count;
                counter.count = -1;
                const second = counter.count;
                console.log(`getter:${person.name}|setter:${first}|guard:${second}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-object-literal-accessors"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("getter:ALICE|setter:5|guard:0\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsClassAccessorsPrivateFieldsAndConstructorParameterProperties() throws Exception {
        final Path sourceFile = tempDir.resolve("class-accessor-parameter-properties.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  #count = 0;
                  increment() { this.#count++; }
                  get value() { return this.#count; }
                }

                abstract class Shape {
                  abstract area(): number;
                }

                class Circle extends Shape {
                  constructor(private radius: number) { super(); }
                  area() { return Math.PI * this.radius * this.radius; }
                }

                class Document {
                  constructor(private content: string) {}
                  print() { return "DOC:" + this.content; }
                }

                const c = new Counter();
                c.increment();
                c.increment();
                const circle = new Circle(1);
                const doc = new Document("hello");
                console.log(`counter:${c.value}`);
                console.log(`area:${circle.area() > 3.14 && circle.area() < 3.15}`);
                console.log(`doc:${doc.print()}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-class-accessor-parameter-properties"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                counter:2
                area:true
                doc:DOC:hello
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsTopLevelAwaitOnAsyncIifeCallPattern() throws Exception {
        final Path sourceFile = tempDir.resolve("top-level-await-async-iife.ts");
        Files.writeString(
                sourceFile,
                """
                const value = await (async () => {
                  return 42;
                })();
                console.log(value === 42);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-top-level-await-async-iife"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNamespaceExportedFunctionsAndNumericEnumReverseMapping() throws Exception {
        final Path sourceFile = tempDir.resolve("namespace-enum-reverse.ts");
        Files.writeString(
                sourceFile,
                """
                enum Direction { Up, Down, Left, Right }
                namespace MathOps {
                  export function add(a: number, b: number) { return a + b; }
                  export function mul(a: number, b: number) { return a * b; }
                }

                console.log(`reverse:${Direction[0]}`);
                console.log(`ops:${MathOps.add(2, 3)}|${MathOps.mul(2, 3)}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-namespace-enum-reverse"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                reverse:Up
                ops:5|6
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsForOfAndForInLoopsWithContinueAndDestructuring() throws Exception {
        final Path sourceFile = tempDir.resolve("for-of-for-in.ts");
        Files.writeString(
                sourceFile,
                """
                const values = [1, 2, 3];
                let sum = 0;
                for (const value of values) {
                  if (value === 2) {
                    continue;
                  }
                  sum += value;
                }
                console.log(`of:${sum}`);

                const obj = { a: 1, b: 2 };
                let keys = "";
                for (const key in obj) {
                  keys = keys + key;
                }
                console.log(`in:${keys}`);

                let last = "";
                for (last in obj) {
                }
                console.log(`last:${last}`);

                let pairTotal = 0;
                for (const [left, right] of [[1, 2], [3, 4]]) {
                  pairTotal += left + right;
                }
                console.log(`pair:${pairTotal}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-for-of-for-in"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                of:4
                in:ab
                last:b
                pair:10
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsClassComputedKeysStaticBlocksFieldInitializersAndPrivateFields() throws Exception {
        final Path sourceFile = tempDir.resolve("class-extended-grammar.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  #seed = 2;
                  value = this.#seed;

                  ["plus"](delta) {
                    return this.#seed + delta;
                  }

                  static total = 1;

                  static {
                    Counter.total = Counter.total + 2;
                  }

                  static bump(step = 1) {
                    Counter.total = Counter.total + step;
                    return Counter.total;
                  }
                }

                const counter = new Counter();
                console.log(`class:${counter.value}|${counter.plus(3)}|${Counter.total}|${Counter.bump()}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-class-extended-grammar"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("class:2|5|3|4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTypeOnlyImportsExportsAndTsOnlyAssertionSyntax() throws Exception {
        final Path moduleFile = tempDir.resolve("types.ts");
        final Path sourceFile = tempDir.resolve("ts-only-syntax.ts");

        Files.writeString(
                moduleFile,
                """
                export type Person = { name: string };
                export const base = 7 as const;
                """,
                UTF_8
        );
        Files.writeString(
                sourceFile,
                """
                import type { Person } from "./types.ts";
                import { base } from "./types.ts";

                const checked = { name: "ok" } satisfies { name: string };
                const person: Person = checked;
                const total = (<number>base) + (base as number);
                console.log(`types:${total}|${person.name}`);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-ts-only-syntax"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("types:14|ok\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsFunctionCallsInsideExpressions() throws Exception {
        final Path sourceFile = tempDir.resolve("calls.ts");
        Files.writeString(
                sourceFile,
                """
                function inc(value: number) {
                  return value + 1;
                }

                const result = inc(inc(3));
                console.log("result=" + result);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out5"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("result=5\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLogicalOperatorsAndNullishCoalescing() throws Exception {
        final Path sourceFile = tempDir.resolve("logical-operators.ts");
        Files.writeString(
                sourceFile,
                """
                const andValue = "left" && "right";
                const orValue = "" || "fallback";
                const nullValue = null ?? "null-fallback";
                const undefinedValue = undefined ?? "undefined-fallback";
                const keptValue = "kept" ?? "ignored";
                console.log(andValue);
                console.log(orValue);
                console.log(nullValue);
                console.log(undefinedValue);
                console.log(keptValue);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("logical-out"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                right
                fallback
                null-fallback
                undefined-fallback
                kept
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void logicalOperatorsShortCircuitRightHandEvaluation() throws Exception {
        final Path sourceFile = tempDir.resolve("logical-short-circuit.ts");
        Files.writeString(
                sourceFile,
                """
                let hits = 0;
                function tick(value: string) {
                  hits = hits + 1;
                  return value;
                }

                const andValue = false && tick("and-rhs");
                const orValue = true || tick("or-rhs");
                const coalesceNull = null ?? tick("nullish-rhs");
                const coalesceUndefined = undefined ?? tick("undefined-rhs");
                const coalesceKept = "kept" ?? tick("coalesce-ignored");

                console.log("hits=" + hits);
                console.log(andValue);
                console.log(orValue);
                console.log(coalesceNull);
                console.log(coalesceUndefined);
                console.log(coalesceKept);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("logical-short-out"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                hits=2
                false
                true
                nullish-rhs
                undefined-rhs
                kept
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsConditionalExpressionOperator() throws Exception {
        final Path sourceFile = tempDir.resolve("conditional-expression.ts");
        Files.writeString(
                sourceFile,
                """
                const score = 7;
                const label = score > 5 ? "high" : "low";
                console.log(label);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                tempDir.resolve("conditional-expression-out")
        );
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("high\n", stdout.toString(UTF_8));
    }

    @Test
    void conditionalExpressionEvaluatesOnlySelectedBranch() throws Exception {
        final Path sourceFile = tempDir.resolve("conditional-branch-selection.ts");
        Files.writeString(
                sourceFile,
                """
                let hits = 0;
                function tick(value: string) {
                  hits = hits + 1;
                  return value;
                }

                const first = true ? tick("then") : tick("else");
                const second = false ? tick("then-2") : tick("else-2");

                console.log(first);
                console.log(second);
                console.log("hits=" + hits);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                tempDir.resolve("conditional-branch-selection-out")
        );
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                then
                else-2
                hits=2
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void generatesFibonacciSequenceValue() throws Exception {
        final Path sourceFile = tempDir.resolve("fibonacci.ts");
        Files.writeString(
                sourceFile,
                """
                function fib(n: number) {
                  if (n <= 1) {
                    return n;
                  }

                  let prev = 0;
                  let curr = 1;
                  let i = 2;
                  while (i <= n) {
                    const next = prev + curr;
                    prev = curr;
                    curr = next;
                    i = i + 1;
                  }
                  return curr;
                }

                console.log("fib(10)=" + fib(10));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out9"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("fib(10)=55\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsFactoryFunctionReturningClosure() throws Exception {
        final Path sourceFile = tempDir.resolve("nested-function.ts");
        Files.writeString(
                sourceFile,
                """
                function makeAdder(base: number) {
                  function add(step: number) {
                    return base + step;
                  }
                  return add;
                }

                const plus3 = makeAdder(3);
                console.log("adder=" + plus3(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out6"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("adder=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsCounterClosureWithMutableCapture() throws Exception {
        final Path sourceFile = tempDir.resolve("counter.ts");
        Files.writeString(
                sourceFile,
                """
                function createCounter(seed: number) {
                  let value = seed;
                  function inc() {
                    value = value + 1;
                    return value;
                  }
                  return inc;
                }

                const c1 = createCounter(2);
                console.log("c1=" + c1());
                console.log("c1=" + c1());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out10"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("c1=3\nc1=4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNestedMultiLevelClosureCapture() throws Exception {
        final Path sourceFile = tempDir.resolve("nested-capture.ts");
        Files.writeString(
                sourceFile,
                """
                function outer(a: number) {
                  let b = a + 1;
                  function middle(c: number) {
                    function inner(d: number) {
                      return b + c + d;
                    }
                    return inner;
                  }
                  return middle;
                }

                const middleFn = outer(2);
                const innerFn = middleFn(3);
                console.log("nested=" + innerFn(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out11"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("nested=10\n", stdout.toString(UTF_8));
    }

    @Test
    void keepsMultipleClosureInstancesIsolated() throws Exception {
        final Path sourceFile = tempDir.resolve("closure-isolation.ts");
        Files.writeString(
                sourceFile,
                """
                function createCounter(seed: number) {
                  let value = seed;
                  function inc() {
                    value = value + 1;
                    return value;
                  }
                  return inc;
                }

                const left = createCounter(0);
                const right = createCounter(10);
                console.log("iso=" + left() + "," + left() + "," + right());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out12"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("iso=1,2,11\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDynamicThisInObjectMethodShorthand() throws Exception {
        final Path sourceFile = tempDir.resolve("object-method-this.ts");
        Files.writeString(
                sourceFile,
                """
                const counter = {
                  value: 1,
                  inc() {
                    this.value = this.value + 1;
                    return this.value;
                  }
                };

                console.log("this=" + counter.inc() + ":" + counter.inc());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out12a"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("this=2:3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDynamicThisInObjectFunctionExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("object-function-this.ts");
        Files.writeString(
                sourceFile,
                """
                const counter = {
                  value: 10,
                  add: function(step: number) {
                    this.value = this.value + step;
                    return this.value;
                  }
                };

                console.log("fn-this=" + counter.add(5));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out12b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("fn-this=15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLexicalThisForArrowFunctionInsideMethod() throws Exception {
        final Path sourceFile = tempDir.resolve("arrow-lexical-this.ts");
        Files.writeString(
                sourceFile,
                """
                const counter = {
                  value: 3,
                  makeAdder() {
                    const apply = (step: number) => {
                      this.value = this.value + step;
                      return this.value;
                    };
                    return apply;
                  }
                };

                const apply = counter.makeAdder();
                console.log("arrow-this=" + apply(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out12c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("arrow-this=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsClassConstructorFieldAndMethod() throws Exception {
        final Path sourceFile = tempDir.resolve("class-basic.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  value: number;

                  constructor(seed: number) {
                    this.value = seed;
                  }

                  inc(step: number) {
                    this.value = this.value + step;
                    return this.value;
                  }
                }

                const counter = new Counter(10);
                console.log("class=" + counter.inc(5));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out13"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("class=15\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsInheritanceWithSuperCallAndBaseMethod() throws Exception {
        final Path sourceFile = tempDir.resolve("class-inheritance.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed + 1);
                  }
                  doubled() {
                    return this.value * 2;
                  }
                }

                const d = new Derived(4);
                console.log("inherit=" + d.read() + "," + d.doubled());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out14"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("inherit=5,10\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSuperMemberMethodCallsInDerivedMethods() throws Exception {
        final Path sourceFile = tempDir.resolve("class-super-member-call.ts");
        Files.writeString(
                sourceFile,
                """
                class A {
                  greet(): string {
                    return "A";
                  }
                }

                class B extends A {
                  greet(): string {
                    return super.greet() + "B";
                  }
                }

                class C extends B {
                  greet(): string {
                    return super.greet() + "C";
                  }
                }

                console.log("super_chain=" + new C().greet());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out14b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("super_chain=ABC\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsObjectLiteralPropertyAccessAndAssignment() throws Exception {
        final Path sourceFile = tempDir.resolve("object-literal.ts");
        Files.writeString(
                sourceFile,
                """
                const item = { count: 2, name: "box" };
                item.count = item.count + 3;
                console.log("obj=" + item.name + ":" + item.count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out15"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("obj=box:5\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsInheritedBaseMethodWithoutOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("inherit-base-method.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed + 3);
                  }
                }

                const d = new Derived(4);
                console.log("base=" + d.read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out16"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("base=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDerivedMethodOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("inherit-override.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                  read() {
                    return this.value;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed);
                  }
                  read() {
                    return this.value + 100;
                  }
                }

                const d = new Derived(5);
                console.log("override=" + d.read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out17"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("override=105\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsEmptyObjectLiteralAndLatePropertyWrites() throws Exception {
        final Path sourceFile = tempDir.resolve("object-empty.ts");
        Files.writeString(
                sourceFile,
                """
                const payload = {};
                payload.count = 1;
                payload.label = "ok";
                payload.count = payload.count + 2;
                console.log("late=" + payload.label + ":" + payload.count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out18"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("late=ok:3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLooseEqualsCoercionAndStrictEqualsDifference() throws Exception {
        final Path sourceFile = tempDir.resolve("coercion-eq.ts");
        Files.writeString(
                sourceFile,
                """
                const undef = undefined;
                console.log("eq1=" + (1 == "1"));
                console.log("eq2=" + (1 === "1"));
                console.log("eq3=" + (undef == null));
                console.log("eq4=" + (undef === null));
                console.log("eq5=" + (false == 0));
                console.log("eq6=" + (false === 0));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out20"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("eq1=true\neq2=false\neq3=true\neq4=false\neq5=true\neq6=false\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsObjectToPrimitiveCoercionInLooseEquality() throws Exception {
        final Path sourceFile = tempDir.resolve("coercion-object-eq.ts");
        Files.writeString(
                sourceFile,
                """
                const valueOfObject = {
                  valueOf() {
                    return 7;
                  }
                };
                console.log("eq1=" + (valueOfObject == 7));

                const toStringObject = {
                  valueOf() {
                    return {};
                  },
                  toString() {
                    return "8";
                  }
                };
                console.log("eq2=" + (toStringObject == 8));

                const boolObject = {
                  valueOf() {
                    return 1;
                  }
                };
                console.log("eq3=" + (boolObject == true));

                const plain = {};
                console.log("eq4=" + (plain == "[object Object]"));
                console.log("eq5=" + (plain == null));

                class Box {
                  value: number;
                  constructor(value: number) {
                    this.value = value;
                  }
                  valueOf() {
                    return this.value;
                  }
                }
                const box = new Box(9);
                console.log("eq6=" + (box == 9));

                const nonCallableValueOf = {
                  valueOf: 3,
                  toString() {
                    return "11";
                  }
                };
                console.log("eq7=" + (nonCallableValueOf == 11));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out20b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "eq1=true\neq2=true\neq3=true\neq4=true\neq5=false\neq6=true\neq7=true\n",
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsMissingPropertyReadAsUndefined() throws Exception {
        final Path sourceFile = tempDir.resolve("missing-property.ts");
        Files.writeString(
                sourceFile,
                """
                const payload = { name: "box" };
                console.log("missing=" + payload.count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("missing=undefined\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDeleteOperatorForObjectProperties() throws Exception {
        final Path sourceFile = tempDir.resolve("delete-operator.ts");
        Files.writeString(
                sourceFile,
                """
                const proto = { shared: "base" };
                const payload = { own: "x", shared: "local" };
                payload.__proto__ = proto;
                console.log("del1=" + delete payload.own);
                console.log("own=" + payload.own);
                console.log("del2=" + delete payload.shared);
                console.log("shared=" + payload.shared);
                console.log("del3=" + delete payload.missing);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("del1=true\nown=undefined\ndel2=true\nshared=base\ndel3=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPrototypeMutationSyntaxViaObjectSetPrototypeOfAndProtoAssignment() throws Exception {
        final Path sourceFile = tempDir.resolve("prototype-mutation.ts");
        Files.writeString(
                sourceFile,
                """
                const base = { label: "base" };
                const target = {};
                const returned = Object.setPrototypeOf(target, base);
                console.log("ret=" + (returned === target));
                console.log("label=" + target.label);

                const alt = { label: "alt" };
                target.__proto__ = alt;
                console.log("label2=" + target.label);

                const cleared = Object.setPrototypeOf(target, null);
                console.log("cleared=" + (cleared === target));
                console.log("label3=" + target.label);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("ret=true\nlabel=base\nlabel2=alt\ncleared=true\nlabel3=undefined\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsDeleteOnUnsupportedOperandInTsj21Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("delete-unsupported.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 1;
                console.log(delete value);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out21d"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("delete"));
    }

    @Test
    void generatedSourceUsesMonomorphicPropertyAccessCaches() throws Exception {
        final Path sourceFile = tempDir.resolve("cache-shape.ts");
        Files.writeString(
                sourceFile,
                """
                const item = { count: 1 };
                console.log(item.count);
                console.log(item.count);
                """,
                UTF_8
        );

        final Path outDir = tempDir.resolve("out22");
        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, outDir);
        final String simpleName = artifact.className().substring(artifact.className().lastIndexOf('.') + 1);
        final Path generatedSource = outDir.resolve("generated-src/dev/tsj/generated/" + simpleName + ".java");
        final String javaSource = Files.readString(generatedSource, UTF_8);

        assertTrue(javaSource.contains("TsjPropertyAccessCache"));
        assertTrue(javaSource.contains("TsjRuntime.getPropertyCached("));
    }

    @Test
    void foldsConstantArithmeticExpressionsWhenOptimizationsAreEnabled() throws Exception {
        final Path sourceFile = tempDir.resolve("constant-folding.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 1 + 2 * 3;
                console.log("value=" + value);
                """,
                UTF_8
        );

        final Path optimizedOut = tempDir.resolve("opt-on");
        final JvmCompiledArtifact optimizedArtifact = new JvmBytecodeCompiler().compile(sourceFile, optimizedOut);
        final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);

        final Path baselineOut = tempDir.resolve("opt-off");
        final JvmCompiledArtifact baselineArtifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                baselineOut,
                JvmOptimizationOptions.disabled()
        );
        final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);

        assertTrue(optimizedSource.contains("Integer.valueOf(7)"));
        assertFalse(optimizedSource.contains("TsjRuntime.multiply("));
        assertTrue(baselineSource.contains("TsjRuntime.multiply("));
    }

    @Test
    void eliminatesUnreachableStatementsAfterReturnWhenDceIsEnabled() throws Exception {
        final Path sourceFile = tempDir.resolve("dead-after-return.ts");
        Files.writeString(
                sourceFile,
                """
                function pick() {
                  return 1;
                  console.log("dead-branch");
                }
                console.log("value=" + pick());
                """,
                UTF_8
        );

        final Path optimizedOut = tempDir.resolve("dce-on");
        final JvmCompiledArtifact optimizedArtifact = new JvmBytecodeCompiler().compile(sourceFile, optimizedOut);
        final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);
        assertFalse(optimizedSource.contains("dead-branch"));

        final Path baselineOut = tempDir.resolve("dce-off");
        final JvmCompiledArtifact baselineArtifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                baselineOut,
                JvmOptimizationOptions.disabled()
        );
        final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);
        assertFalse(baselineSource.contains("dead-branch"));
    }

    @Test
    void removesWhileFalseLoopWhenDceIsEnabled() throws Exception {
        final Path sourceFile = tempDir.resolve("while-false.ts");
        Files.writeString(
                sourceFile,
                """
                let value = 1;
                while (false) {
                  value = value + 1;
                }
                console.log("value=" + value);
                """,
                UTF_8
        );

        final Path optimizedOut = tempDir.resolve("while-dce-on");
        final JvmCompiledArtifact optimizedArtifact = new JvmBytecodeCompiler().compile(sourceFile, optimizedOut);
        final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);

        final Path baselineOut = tempDir.resolve("while-dce-off");
        final JvmCompiledArtifact baselineArtifact = new JvmBytecodeCompiler().compile(
                sourceFile,
                baselineOut,
                JvmOptimizationOptions.disabled()
        );
        final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);

        assertFalse(optimizedSource.contains("while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.FALSE))"));
        assertTrue(baselineSource.contains("while (dev.tsj.runtime.TsjRuntime.truthy(Boolean.FALSE))"));
    }

    @Test
    void optimizationBenchmarkShowsGeneratedSourceReductionAcrossFixtureSet() throws Exception {
        final Path fixturesRoot = tempDir.resolve("tsj17-fixtures");
        Files.createDirectories(fixturesRoot);

        final Path fixtureOne = fixturesRoot.resolve("fixture-one.ts");
        Files.writeString(
                fixtureOne,
                """
                function score() {
                  const base = 1 + 2 + 3 + 4;
                  if (false) {
                    console.log("dead-one");
                  }
                  return base;
                }
                console.log("score=" + score());
                """,
                UTF_8
        );

        final Path fixtureTwo = fixturesRoot.resolve("fixture-two.ts");
        Files.writeString(
                fixtureTwo,
                """
                let acc = 0;
                while (false) {
                  acc = acc + 100;
                }
                if (true) {
                  acc = acc + (5 * 6);
                } else {
                  acc = acc + 999;
                }
                console.log("acc=" + acc);
                """,
                UTF_8
        );

        final Path fixtureThree = fixturesRoot.resolve("fixture-three.ts");
        Files.writeString(
                fixtureThree,
                """
                async function run() {
                  const left = await Promise.resolve(2 + 3);
                  if (false) {
                    console.log("dead-async");
                  }
                  return left + (10 - 7);
                }
                function onDone(value: number) {
                  console.log("value=" + value);
                  return value;
                }
                run().then(onDone);
                """,
                UTF_8
        );

        final JvmBytecodeCompiler compiler = new JvmBytecodeCompiler();
        int optimizedBytes = 0;
        int baselineBytes = 0;
        int optimizedOps = 0;
        int baselineOps = 0;
        for (Path fixture : new Path[]{fixtureOne, fixtureTwo, fixtureThree}) {
            final Path optimizedOut = tempDir.resolve("bench-opt-" + fixture.getFileName().toString());
            final JvmCompiledArtifact optimizedArtifact = compiler.compile(fixture, optimizedOut);
            final String optimizedSource = generatedJavaSource(optimizedOut, optimizedArtifact);
            optimizedBytes += optimizedSource.getBytes(UTF_8).length;
            optimizedOps += runtimeOperationCount(optimizedSource);

            final Path baselineOut = tempDir.resolve("bench-base-" + fixture.getFileName().toString());
            final JvmCompiledArtifact baselineArtifact = compiler.compile(
                    fixture,
                    baselineOut,
                    JvmOptimizationOptions.disabled()
            );
            final String baselineSource = generatedJavaSource(baselineOut, baselineArtifact);
            baselineBytes += baselineSource.getBytes(UTF_8).length;
            baselineOps += runtimeOperationCount(baselineSource);
        }

        assertTrue(optimizedBytes < baselineBytes);
        assertTrue(optimizedOps < baselineOps);
    }

    @Test
    void supportsPromiseResolveThenChainingWithMicrotaskOrdering() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-chain.ts");
        Files.writeString(
                sourceFile,
                """
                function step(v: number) {
                  console.log("step=" + v);
                  return v + 1;
                }
                function done(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                Promise.resolve(1).then(step).then(done);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nstep=1\ndone=2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsThenableAssimilationAndFirstSettleWins() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-thenable.ts");
        Files.writeString(
                sourceFile,
                """
                const thenable = {
                  then(resolve: any, reject: any) {
                    resolve(41);
                    reject("bad");
                    resolve(99);
                  }
                };

                Promise.resolve(thenable)
                  .then((value: number) => {
                    console.log("value=" + value);
                    return value + 1;
                  })
                  .then((value: number) => {
                    console.log("next=" + value);
                    return value;
                  });
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b2"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nvalue=41\nnext=42\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsThenableRejectBeforeSettlementAsRejection() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-thenable-reject.ts");
        Files.writeString(
                sourceFile,
                """
                const badThenable = {
                  then(resolve: any, reject: any) {
                    reject("boom");
                    resolve(99);
                  }
                };

                Promise.resolve(badThenable).then(
                  undefined,
                  (reason: string) => {
                    console.log("error=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b3"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nerror=boom\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseCatchAndFinallyPassThrough() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-catch-finally.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.reject("boom")
                  .catch((reason: string) => {
                    console.log("catch=" + reason);
                    return 7;
                  })
                  .finally(() => {
                    console.log("finally");
                    return 999;
                  })
                  .then((value: number) => {
                    console.log("value=" + value);
                    return value;
                  });
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b4"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ncatch=boom\nfinally\nvalue=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseFinallyRejectionOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-finally-reject.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.resolve(1)
                  .finally(() => Promise.reject("fin"))
                  .then(
                    (value: number) => {
                      console.log("value=" + value);
                      return value;
                    },
                    (reason: string) => {
                      console.log("error=" + reason);
                      return reason;
                    }
                  );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b5"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nerror=fin\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseAllAndRaceCombinators() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-all-race.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.all([Promise.resolve(1), Promise.resolve(2), 3]).then((values: any) => {
                  console.log("all=" + values.length);
                  return values;
                });

                Promise.race([Promise.resolve("win"), Promise.reject("lose")]).then(
                  (value: string) => {
                    console.log("race=" + value);
                    return value;
                  },
                  (reason: string) => {
                    console.log("race-err=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b6"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nall=3\nrace=win\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseAllSettledAndAnyCombinators() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-allsettled-any.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.allSettled([Promise.resolve(1), Promise.reject("x")]).then((entries: any) => {
                  console.log("settled=" + entries.length);
                  return entries;
                });

                Promise.any([Promise.reject("a"), Promise.resolve("ok")]).then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("any-err=" + reason.name);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b7"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nsettled=2\nany=ok\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseAnyAggregateErrorWhenAllReject() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-any-reject.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.any([Promise.reject("a"), Promise.reject("b")]).then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("anyErr=" + reason.name + ":" + reason.errors.length);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b8"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nanyErr=AggregateError:2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsPromiseCombinatorsWithStringIterableInput() throws Exception {
        final Path sourceFile = tempDir.resolve("promise-combinators-string-iterable.ts");
        Files.writeString(
                sourceFile,
                """
                Promise.all("ab").then((values: any) => {
                  console.log("all=" + values.length);
                  return values;
                });

                Promise.race("ab").then(
                  (value: string) => {
                    console.log("race=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("race-err=" + reason);
                    return reason;
                  }
                );

                Promise.allSettled("ab").then((entries: any) => {
                  console.log("settled=" + entries.length);
                  return entries;
                });

                Promise.any("ab").then(
                  (value: string) => {
                    console.log("any=" + value);
                    return value;
                  },
                  (reason: any) => {
                    console.log("any-err=" + reason.name);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22b9"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nall=2\nrace=a\nsettled=2\nany=a\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncFunctionAwaitAndThenSequencing() throws Exception {
        final Path sourceFile = tempDir.resolve("async-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function compute(seed: number) {
                  console.log("start=" + seed);
                  const next = await Promise.resolve(seed + 1);
                  console.log("after=" + next);
                  return next + 1;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                compute(4).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("start=4\nsync\nafter=5\ndone=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAwaitOnNonPromiseValueViaMicrotaskContinuation() throws Exception {
        final Path sourceFile = tempDir.resolve("async-await-value.ts");
        Files.writeString(
                sourceFile,
                """
                async function readValue() {
                  const value = await 3;
                  console.log("await=" + value);
                  return value;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                readValue().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22d"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nawait=3\ndone=3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncRejectionFlowWithThrowAfterAwait() throws Exception {
        final Path sourceFile = tempDir.resolve("async-reject.ts");
        Files.writeString(
                sourceFile,
                """
                async function failLater() {
                  await Promise.resolve(1);
                  throw "boom";
                }

                function onError(reason: string) {
                  console.log("error=" + reason);
                  return reason;
                }

                failLater().then(undefined, onError);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22e"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nerror=boom\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAwaitOutsideAsyncFunction() throws Exception {
        final Path sourceFile = tempDir.resolve("await-outside.ts");
        Files.writeString(
                sourceFile,
                """
                function bad() {
                  const value = await Promise.resolve(1);
                  return value;
                }

                console.log(bad());
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22f"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("await"));
    }

    @Test
    void supportsAsyncIfBranchAwaitAndPostBranchContinuation() throws Exception {
        final Path sourceFile = tempDir.resolve("async-if.ts");
        Files.writeString(
                sourceFile,
                """
                async function pick(flag: number) {
                  let value = 0;
                  if (flag === 1) {
                    value = await Promise.resolve(10);
                    console.log("then=" + value);
                  } else {
                    value = await Promise.resolve(20);
                    console.log("else=" + value);
                  }
                  console.log("after=" + value);
                  return value;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                pick(1).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22g"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nthen=10\nafter=10\ndone=10\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAwaitInAsyncIfCondition() throws Exception {
        final Path sourceFile = tempDir.resolve("async-if-condition-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function choose() {
                  if (await Promise.resolve(true)) {
                    return 1;
                  }
                  return 0;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                choose().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22h"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncWhileLoopWithAwaitInBodyAndTailContinuation() throws Exception {
        final Path sourceFile = tempDir.resolve("async-while.ts");
        Files.writeString(
                sourceFile,
                """
                async function accumulate(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    const step = await Promise.resolve(i + 1);
                    sum = sum + step;
                    i = i + 1;
                  }
                  console.log("sum=" + sum);
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                accumulate(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22i"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nsum=6\ndone=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNestedAsyncIfWithinAsyncWhile() throws Exception {
        final Path sourceFile = tempDir.resolve("async-while-if.ts");
        Files.writeString(
                sourceFile,
                """
                async function classify(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    if (i === 1) {
                      const extra = await Promise.resolve(10);
                      sum = sum + extra;
                    } else {
                      const extra = await Promise.resolve(1);
                      sum = sum + extra;
                    }
                    i = i + 1;
                  }
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                classify(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22j"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=12\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAwaitInAsyncWhileCondition() throws Exception {
        final Path sourceFile = tempDir.resolve("async-while-condition-await.ts");
        Files.writeString(
                sourceFile,
                """
                async function run() {
                  let i = 0;
                  while (await Promise.resolve(i < 1)) {
                    i = i + 1;
                  }
                  return i;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsBreakAndContinueInAsyncWhileLoop() throws Exception {
        final Path sourceFile = tempDir.resolve("async-break-continue.ts");
        Files.writeString(
                sourceFile,
                """
                async function flow(limit: number) {
                  let i = 0;
                  let sum = 0;
                  while (i < limit) {
                    i = i + 1;
                    if (i === 2) {
                      continue;
                    }
                    if (i === 4) {
                      break;
                    }
                    const step = await Promise.resolve(i);
                    sum = sum + step;
                  }
                  console.log("sum=" + sum);
                  return sum;
                }

                function onDone(v: number) {
                  console.log("done=" + v);
                  return v;
                }

                flow(6).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k2"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nsum=4\ndone=4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsBreakAndContinueInSyncWhileLoop() throws Exception {
        final Path sourceFile = tempDir.resolve("sync-break-continue.ts");
        Files.writeString(
                sourceFile,
                """
                function run() {
                  let i = 0;
                  let sum = 0;
                  while (i < 5) {
                    i = i + 1;
                    if (i === 2) {
                      continue;
                    }
                    if (i === 4) {
                      break;
                    }
                    sum = sum + i;
                  }
                  console.log("sum=" + sum);
                  return sum;
                }

                run();
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k3"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sum=4\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncTryCatchFinallyWithAwaitOnSuccessPath() throws Exception {
        final Path sourceFile = tempDir.resolve("async-try-success.ts");
        Files.writeString(
                sourceFile,
                """
                async function run() {
                  try {
                    const value = await Promise.resolve(2);
                    console.log("try=" + value);
                    return "ok-" + value;
                  } catch (err: string) {
                    console.log("catch=" + err);
                    return "catch-" + err;
                  } finally {
                    const marker = await Promise.resolve("fin");
                    console.log("finally=" + marker);
                  }
                }

                function onDone(value: string) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k4"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ntry=2\nfinally=fin\ndone=ok-2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncTryCatchFinallyWithAwaitOnCatchPath() throws Exception {
        final Path sourceFile = tempDir.resolve("async-try-catch.ts");
        Files.writeString(
                sourceFile,
                """
                async function run() {
                  try {
                    const value = await Promise.resolve(0);
                    if (value === 0) {
                      throw "boom";
                    }
                    return "ok";
                  } catch (err: string) {
                    const caught = await Promise.resolve(err + "-handled");
                    console.log("catch=" + caught);
                    return "catch-" + caught;
                  } finally {
                    const marker = await Promise.resolve("fin");
                    console.log("finally=" + marker);
                  }
                }

                function onDone(value: string) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k5"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ncatch=boom-handled\nfinally=fin\ndone=catch-boom-handled\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncTryFinallyReturnOverride() throws Exception {
        final Path sourceFile = tempDir.resolve("async-try-finally-override.ts");
        Files.writeString(
                sourceFile,
                """
                async function run() {
                  try {
                    return await Promise.resolve("try");
                  } finally {
                    const marker = await Promise.resolve("fin");
                    return "override-" + marker;
                  }
                }

                function onDone(value: string) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k6"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=override-fin\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncTryFinallyOnRejectedPathWithoutCatch() throws Exception {
        final Path sourceFile = tempDir.resolve("async-try-finally-reject.ts");
        Files.writeString(
                sourceFile,
                """
                async function run() {
                  try {
                    throw await Promise.resolve("boom");
                  } finally {
                    const marker = await Promise.resolve("fin");
                    console.log("finally=" + marker);
                  }
                }

                run().then(
                  (value: string) => {
                    console.log("done=" + value);
                    return value;
                  },
                  (reason: string) => {
                    console.log("error=" + reason);
                    return reason;
                  }
                );
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k7"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\nfinally=fin\nerror=boom\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSyncTryCatchFinally() throws Exception {
        final Path sourceFile = tempDir.resolve("sync-try-catch-finally.ts");
        Files.writeString(
                sourceFile,
                """
                function run(flag: number) {
                  try {
                    if (flag === 0) {
                      throw "boom";
                    }
                    console.log("try");
                    return "ok";
                  } catch (err: string) {
                    console.log("catch=" + err);
                    return "caught";
                  } finally {
                    console.log("finally");
                  }
                }

                console.log(run(1));
                console.log(run(0));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("try\nfinally\nok\ncatch=boom\nfinally\ncaught\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTryStatementsWithEmptyCatchOrFinallyBodies() throws Exception {
        final Path sourceFile = tempDir.resolve("empty-catch-finally.ts");
        Files.writeString(
                sourceFile,
                """
                function withFinally(): string {
                  try {
                    return "try";
                  } finally {
                  }
                }

                try {
                  throw "boom";
                } catch {
                }

                console.log("finally=" + withFinally());
                console.log("emptyCatch=ok");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8a"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("finally=try\nemptyCatch=ok\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsErrorBuiltinClassExtensionAndThrowableInstanceofChecks() throws Exception {
        final Path sourceFile = tempDir.resolve("error-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                class AppError extends Error {
                  code: number;
                  constructor(message: string, code: number) {
                    super(message);
                    this.code = code;
                  }
                }

                try {
                  throw new Error("inner");
                } catch (e: any) {
                  console.log("message=" + e.message);
                }

                try {
                  throw new AppError("not found", 404);
                } catch (e: any) {
                  console.log("custom=" + (e.code === 404 && e.message === "not found"));
                }

                try {
                  (null as any).x;
                } catch (e: any) {
                  console.log("instanceof=" + (e instanceof Error));
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("message=inner\ncustom=true\ninstanceof=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsErrorCauseAndAggregateErrorConstructorInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("error-cause-aggregate.ts");
        Files.writeString(
                sourceFile,
                """
                try {
                  try {
                    throw new Error("root");
                  } catch (inner) {
                    throw new Error("wrapper", { cause: inner });
                  }
                } catch (e: any) {
                  console.log("cause=" + (e.message === "wrapper" && e.cause.message === "root"));
                }

                const errors = [new Error("e1"), new Error("e2")];
                const agg = new AggregateError(errors, "many");
                console.log("agg=" + (agg.message === "many" && agg.errors.length === 2 && agg.errors[0].message === "e1"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("cause=true\nagg=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsJsonBuiltinParseStringifyAndReplacerInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("json-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                const encoded = JSON.stringify({ a: 1, b: 2 }, (key: string, value: any) => {
                  if (key === "b") {
                    return undefined;
                  }
                  return value;
                });
                console.log("encoded=" + encoded.includes("\\"a\\":1") + ":" + !encoded.includes("\\"b\\""));

                const parsed = JSON.parse("{\\"x\\":3,\\"arr\\":[1,2]}");
                console.log("parsed=" + (parsed.x === 3 && parsed.arr[1] === 2));

                const roundtrip = JSON.parse(JSON.stringify({ nested: { name: "ok" } }));
                console.log("roundtrip=" + (roundtrip.nested.name === "ok"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("encoded=true:true\nparsed=true\nroundtrip=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsMathNumberAndGlobalNumericBuiltinsInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("math-number-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                const mathOk =
                  Math.floor(3.7) === 3
                  && Math.ceil(3.2) === 4
                  && Math.round(3.5) === 4
                  && Math.PI > 3.14
                  && Math.log10(1000) === 3;
                console.log("math=" + mathOk);

                const numberOk =
                  Number("42") === 42
                  && Number.isInteger(5) === true
                  && Number.isFinite(Infinity) === false
                  && Number.isNaN(NaN) === true;
                console.log("number=" + numberOk);

                const parseOk =
                  parseInt("42") === 42
                  && parseFloat("3.14") === 3.14;
                console.log("parse=" + parseOk);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8d"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("math=true\nnumber=true\nparse=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsObjectDateAndErrorSubtypeBuiltinsInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("object-date-error-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                const keys = Object.keys({ a: 1, b: 2 });
                console.log("keys=" + (keys.length === 2 && keys[0] === "a"));

                const now = Date.now();
                const epoch = new Date(0);
                console.log("date=" + (now > 0 && epoch.getTime() === 0 && epoch.toISOString().includes("1970")));

                try {
                  throw new TypeError("bad");
                } catch (e: any) {
                  console.log("type=" + (e instanceof TypeError && e instanceof Error && e.name === "TypeError"));
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8e"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("keys=true\ndate=true\ntype=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsArrayBuiltinMethodsAndHelpersInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("array-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                const mapped = [1, 2, 3].map((n: number) => n * 2);
                const reduced = [1, 2, 3].reduce((acc: number, value: number) => acc + value, 0);
                const from = Array.from("ab");
                const filled = new Array(3).fill(0);
                console.log("array=" + (
                  mapped[2] === 6
                  && reduced === 6
                  && from[1] === "b"
                  && Array.isArray(mapped)
                  && filled[0] === 0
                  && filled.length === 3
                ));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8f"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("array=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsMapAndSetBuiltinsInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("map-set-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                const map = new Map<string, number>();
                map.set("a", 1);
                map.set("b", 2);
                const mapOk = map.get("a") === 1 && map.size === 2;

                const set = new Set<number>();
                set.add(1);
                set.add(1);
                set.add(2);
                const setOk = set.size === 2 && set.has(2) === true;

                console.log("mapset=" + (mapOk && setOk));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8g"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("mapset=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsWeakCollectionsAndWeakRefBuiltinsInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("weak-builtins.ts");
        Files.writeString(
                sourceFile,
                """
                const wm = new WeakMap<object, string>();
                const k = { id: 1 };
                wm.set(k, "one");
                const wmOk = wm.get(k) === "one" && wm.has(k) === true;
                wm.delete(k);
                const wmDelOk = wm.has(k) === false;

                const ws = new WeakSet<object>();
                const o = { x: 1 };
                ws.add(o);
                const wsOk = ws.has(o) === true;
                ws.delete(o);
                const wsDelOk = ws.has(o) === false;

                const target = { value: 42 };
                const ref = new WeakRef(target);
                const deref = ref.deref();
                const wrOk = deref !== undefined && deref.value === 42;

                console.log("weak=" + (wmOk && wmDelOk && wsOk && wsDelOk && wrOk));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8ga"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("weak=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsRegexpLiteralAndConstructorBuiltinsInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("regexp-builtin.ts");
        Files.writeString(
                sourceFile,
                """
                const re = /hello/i;
                const testOk = re.test("HELLO");

                const dynamic = new RegExp("test", "i");
                const ctorOk = dynamic.test("TEST");

                const all: string[] = [];
                const digits = /\\d+/g;
                let m;
                while ((m = digits.exec("a1b22")) !== null) {
                  all.push(m[0]);
                }

                const matchOk = "ab3cd".search(/\\d/) === 2;
                const replaceOk = "aabaa".replace(/a/g, "x") === "xxbxx";
                console.log("regex=" + (testOk && ctorOk && all.length === 2 && all[1] === "22" && matchOk && replaceOk));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22k8h"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("regex=true\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncFunctionExpressionWithAwaitInBinaryExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("async-fn-expression.ts");
        Files.writeString(
                sourceFile,
                """
                const compute = async function(seed: number) {
                  return (await Promise.resolve(seed + 1)) + 2;
                };

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                compute(4).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22l"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncArrowFunctionWithAwaitExpressionBody() throws Exception {
        final Path sourceFile = tempDir.resolve("async-arrow.ts");
        Files.writeString(
                sourceFile,
                """
                const inc = async (value: number) => (await Promise.resolve(value)) + 1;

                function onDone(result: number) {
                  console.log("done=" + result);
                  return result;
                }

                inc(5).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22m"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncClassMethodWithAwaitInReturnExpression() throws Exception {
        final Path sourceFile = tempDir.resolve("async-class-method.ts");
        Files.writeString(
                sourceFile,
                """
                class Worker {
                  async compute(seed: number) {
                    return (await Promise.resolve(seed + 1)) * 2;
                  }
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                const worker = new Worker();
                worker.compute(3).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22n"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=8\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsAsyncObjectMethodWithAwaitInInitializerAndReturn() throws Exception {
        final Path sourceFile = tempDir.resolve("async-object-method.ts");
        Files.writeString(
                sourceFile,
                """
                const ops = {
                  async compute(seed: number) {
                    const value = await Promise.resolve(seed + 2);
                    return value * 3;
                  }
                };

                function onDone(result: number) {
                  console.log("done=" + result);
                  return result;
                }

                ops.compute(2).then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ndone=12\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAsyncClassGeneratorMethodWithTargetedDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("async-class-generator.ts");
        Files.writeString(
                sourceFile,
                """
                class Worker {
                  async *build() {
                    return 1;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o0"))
        );

        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Async generator methods are unsupported"));
        assertTrue(exception.getMessage().contains("TSJ-13b"));
    }

    @Test
    void rejectsAsyncClassGetterMethodVariantWithTargetedDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("async-class-getter.ts");
        Files.writeString(
                sourceFile,
                """
                class Worker {
                  async get value() {
                    return 1;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o1"))
        );

        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Async get methods are unsupported"));
        assertTrue(exception.getMessage().contains("TSJ-13b"));
    }

    @Test
    void rejectsAsyncClassSetterMethodVariantWithTargetedDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("async-class-setter.ts");
        Files.writeString(
                sourceFile,
                """
                class Worker {
                  async set value(next: number) {
                    console.log(next);
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o2"))
        );

        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Async set methods are unsupported"));
        assertTrue(exception.getMessage().contains("TSJ-13b"));
    }

    @Test
    void rejectsAsyncObjectGeneratorMethodWithTargetedDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("async-object-generator.ts");
        Files.writeString(
                sourceFile,
                """
                const ops = {
                  async *build() {
                    return 1;
                  }
                };
                console.log(ops);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o3"))
        );

        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Async generator methods are unsupported"));
        assertTrue(exception.getMessage().contains("TSJ-13b"));
    }

    @Test
    void rejectsAsyncObjectGetterMethodVariantWithTargetedDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("async-object-getter.ts");
        Files.writeString(
                sourceFile,
                """
                const ops = {
                  async get value() {
                    return 1;
                  }
                };
                console.log(ops);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o4"))
        );

        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Async get methods are unsupported"));
        assertTrue(exception.getMessage().contains("TSJ-13b"));
    }

    @Test
    void rejectsAsyncObjectSetterMethodVariantWithTargetedDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("async-object-setter.ts");
        Files.writeString(
                sourceFile,
                """
                const ops = {
                  async set value(next: number) {
                    console.log(next);
                  }
                };
                console.log(ops);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22o5"))
        );

        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("Async set methods are unsupported"));
        assertTrue(exception.getMessage().contains("TSJ-13b"));
    }

    @Test
    void supportsAwaitInsideCallArgumentsAndObjectLiteralValues() throws Exception {
        final Path sourceFile = tempDir.resolve("await-expression-positions.ts");
        Files.writeString(
                sourceFile,
                """
                function add(a: number, b: number) {
                  return a + b;
                }

                async function run() {
                  const payload = {
                    left: await Promise.resolve(2),
                    right: await Promise.resolve(3)
                  };
                  const total = add(await Promise.resolve(payload.left), await Promise.resolve(payload.right));
                  console.log("total=" + total);
                  return total;
                }

                function onDone(value: number) {
                  console.log("done=" + value);
                  return value;
                }

                run().then(onDone);
                console.log("sync");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22p"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("sync\ntotal=5\ndone=5\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsAwaitInsideNonAsyncArrowFunction() throws Exception {
        final Path sourceFile = tempDir.resolve("await-non-async-arrow.ts");
        Files.writeString(
                sourceFile,
                """
                const read = (seed: number) => await Promise.resolve(seed + 1);
                console.log(read(1));
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out22q"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("await"));
    }

    @Test
    void supportsNamedImportAcrossFilesWithDependencyInitializationOrder() throws Exception {
        final Path mathModule = tempDir.resolve("math.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                mathModule,
                """
                console.log("math-init");
                export function double(n: number) {
                  return n * 2;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { double } from "./math.ts";
                console.log("result=" + double(3));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out23"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("math-init\nresult=6\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsMultilineNamedImportAcrossFiles() throws Exception {
        final Path mathModule = tempDir.resolve("math-multiline.ts");
        final Path entry = tempDir.resolve("main-multiline.ts");
        Files.writeString(
                mathModule,
                """
                export function double(n: number) {
                  return n * 2;
                }
                export function triple(n: number) {
                  return n * 3;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import {
                  double,
                  triple as thrice
                } from "./math-multiline.ts";

                console.log("value=" + (double(2) + thrice(2)));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out23b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("value=10\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsMultilineJavaInteropNamedImportInTsj26() throws Exception {
        final Path sourceFile = tempDir.resolve("interop-multiline-import.ts");
        Files.writeString(
                sourceFile,
                """
                import {
                  max,
                  min as minimum
                } from "java:java.lang.Math";

                console.log("range=" + minimum(3, 9) + ":" + max(3, 9));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out23c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("range=3:9\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTransitiveNamedImportsAcrossMultipleFiles() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                export function base(n: number) {
                  return n;
                }
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                import { base } from "./a.ts";
                export function plusOne(n: number) {
                  return base(n) + 1;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { plusOne } from "./b.ts";
                console.log("trans=" + plusOne(4));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out24"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("trans=5\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSideEffectImportAcrossFiles() throws Exception {
        final Path setup = tempDir.resolve("setup.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                setup,
                """
                console.log("setup-init");
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import "./setup.ts";
                console.log("ready");
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out24b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("setup-init\nready\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLiveBindingBehaviorForImportedMutableValueThroughExportedReaders() throws Exception {
        final Path counterModule = tempDir.resolve("counter.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                counterModule,
                """
                export let count = 0;
                export function inc() {
                  count = count + 1;
                }
                export function read() {
                  return count;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { inc, read } from "./counter.ts";
                console.log("v=" + read());
                inc();
                console.log("v=" + read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out25"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("v=0\nv=1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTopLevelAwaitInSingleFileProgram() throws Exception {
        final Path sourceFile = tempDir.resolve("tla-single.ts");
        Files.writeString(
                sourceFile,
                """
                console.log("before");
                const value = await Promise.resolve(1);
                console.log("after=" + value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out25a"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("before\nafter=1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTopLevelAwaitAcrossImportedDependencyInitializationOrder() throws Exception {
        final Path dep = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                dep,
                """
                export let status = "init";
                status = await Promise.resolve("ready");
                console.log("dep=" + status);
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { status } from "./dep.ts";
                console.log("main=" + status);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out25b"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("dep=ready\nmain=ready\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTopLevelAwaitAcrossTransitiveModuleDependencies() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                export let value = 0;
                value = await Promise.resolve(5);
                console.log("a=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                import { value } from "./a.ts";
                export function read() {
                  return value;
                }
                console.log("b=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { read } from "./b.ts";
                console.log("main=" + read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out25c"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("a=5\nb=5\nmain=5\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsTopLevelAwaitInWhileConditionForNow() throws Exception {
        final Path sourceFile = tempDir.resolve("tla-while-condition.ts");
        Files.writeString(
                sourceFile,
                """
                let i = 0;
                while (await Promise.resolve(i < 1)) {
                  i = i + 1;
                }
                console.log(i);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out25d"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("while condition"));
    }

    @Test
    void isolatesModuleScopesAcrossFilesWithSameLocalBindings() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                const local = 1;
                export function readA() {
                  return local;
                }
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                const local = 2;
                export function readB() {
                  return local;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { readA } from "./a.ts";
                import { readB } from "./b.ts";
                console.log("scope=" + readA() + ":" + readB());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26scope"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("scope=1:2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsImportAliasInTsj22() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(module, "export const value = 2;\n", UTF_8);
        Files.writeString(
                entry,
                """
                import { value as renamed } from "./dep.ts";
                console.log("alias=" + renamed);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26alias"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("alias=2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSafeCircularImportsWithoutUnsafeTopLevelReads() throws Exception {
        final Path moduleA = tempDir.resolve("a.ts");
        final Path moduleB = tempDir.resolve("b.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                moduleA,
                """
                import { pingB } from "./b.ts";
                export function pingA() {
                  return "A";
                }
                export function callB() {
                  return pingB();
                }
                """,
                UTF_8
        );
        Files.writeString(
                moduleB,
                """
                import { pingA } from "./a.ts";
                export function pingB() {
                  return "B";
                }
                export function unused() {
                  return pingA;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { callB, pingA } from "./a.ts";
                console.log("cycle=" + pingA() + ":" + callB());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26cycle"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("cycle=A:B\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsDefaultImportWithOptionalNamedBindingsInTsj22() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                module,
                """
                const defaultValue = 41;
                export default defaultValue;
                export const value = 2;
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import renamedDefault, { value as named } from "./dep.ts";
                console.log("default=" + renamedDefault + ",named=" + named);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26a"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("default=41,named=2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNamespaceImportInTsj22() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                module,
                """
                const defaultValue = 41;
                export default defaultValue;
                export const value = 7;
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import * as ns from "./dep.ts";
                console.log("ns=" + ns.default + ":" + ns.value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("ns=41:7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsReExportStarAndNamedFromInTsj65Subset() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path barrel = tempDir.resolve("barrel.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                module,
                """
                export const alpha = 1;
                export const beta = 2;
                let count = 0;
                export function inc() {
                  count = count + 1;
                }
                export function read() {
                  return count;
                }
                """,
                UTF_8
        );
        Files.writeString(
                barrel,
                """
                export * from "./dep.ts";
                export { beta as renamedBeta } from "./dep.ts";
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { alpha, renamedBeta, inc, read } from "./barrel.ts";
                console.log("alpha=" + alpha);
                console.log("beta=" + renamedBeta);
                console.log("count0=" + read());
                inc();
                console.log("count1=" + read());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out65-reexport"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("alpha=1\nbeta=2\ncount0=0\ncount1=1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsRelativeDynamicImportWithModuleNamespaceObjectInTsj65Subset() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                module,
                """
                const defaultValue = 41;
                export default defaultValue;
                export const value = 7;
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                const loaded = await import("./dep.ts");
                console.log("default=" + loaded.default);
                console.log("value=" + loaded.value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out65-dynamic-import"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("default=41\nvalue=7\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsNamedImportLiveBindingForMutableExportInTsj65Subset() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                module,
                """
                export let count = 1;
                export function inc() {
                  count = count + 1;
                }
                """,
                UTF_8
        );
        Files.writeString(
                entry,
                """
                import { count, inc } from "./dep.ts";
                console.log("before=" + count);
                inc();
                console.log("after=" + count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out65-live-binding"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("before=1\nafter=2\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsNonRelativeImportInTsj12Bootstrap() throws Exception {
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(
                entry,
                """
                import { readFileSync } from "fs";
                console.log(readFileSync);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out27"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("relative imports"));
    }

    @Test
    void supportsJavaInteropNamedImportBindingsInTsj26() throws Exception {
        final Path entry = tempDir.resolve("interop-main.ts");
        Files.writeString(
                entry,
                """
                import { max, min as minimum } from "java:java.lang.Math";
                console.log("max=" + max(3, 7));
                console.log("min=" + minimum(3, 7));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26interop"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("max=7\nmin=3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsJavaInteropBareStaticFieldValueImportsInTsj26() throws Exception {
        dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType.GLOBAL = 100;
        final Path entry = tempDir.resolve("interop-static-field-value.ts");
        Files.writeString(
                entry,
                """
                import { GLOBAL } from "java:dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType";
                console.log("global=" + GLOBAL);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26interop-static-field"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("global=100\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsTsj29InteropConstructorInstanceFieldsAndVarArgs() throws Exception {
        dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType.GLOBAL = 100;
        final Path entry = tempDir.resolve("interop-tsj29.ts");
        Files.writeString(
                entry,
                """
                import { $new as makeFixture, $instance$add as add, $instance$get$value as getValue, $instance$set$value as setValue, $static$get$GLOBAL as getGlobal, $static$set$GLOBAL as setGlobal, pick, join } from "java:dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType";

                const fixture = makeFixture(5);
                console.log("add=" + add(fixture, 4));
                console.log("value=" + getValue(fixture));
                console.log("set=" + setValue(fixture, 21));
                console.log("value2=" + getValue(fixture));
                console.log("global=" + getGlobal());
                setGlobal(9);
                console.log("global2=" + getGlobal());
                console.log("pickInt=" + pick(3));
                console.log("pickDouble=" + pick(3.5));
                console.log("join=" + join("p", "a", "b"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out29interop"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "add=9\n"
                        + "value=9\n"
                        + "set=21\n"
                        + "value2=21\n"
                        + "global=100\n"
                        + "global2=9\n"
                        + "pickInt=int\n"
                        + "pickDouble=double\n"
                        + "join=p:a:b\n",
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsTsj30InteropCallbacksAndCompletableFutureAwait() throws Exception {
        final Path entry = tempDir.resolve("interop-tsj30.ts");
        Files.writeString(
                entry,
                """
                import { applyOperator, upperAsync } from "java:dev.tsj.compiler.backend.jvm.fixtures.InteropFixtureType";

                const callbackResult = applyOperator((value: number) => value + 3, 4);
                console.log("callback=" + callbackResult);

                async function run() {
                  const upper = await upperAsync("tsj");
                  console.log("upper=" + upper);
                }

                run();
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out30interop"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("callback=7\nupper=TSJ\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsJavaInteropDefaultImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path entry = tempDir.resolve("interop-default.ts");
        Files.writeString(
                entry,
                """
                import Math from "java:java.lang.Math";
                console.log(Math);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26interop2"))
        );
        assertUnsupportedFeature(
                exception,
                "TSJ26-INTEROP-SYNTAX",
                "Use named imports"
        );
    }

    @Test
    void rejectsJavaInteropNamespaceImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path entry = tempDir.resolve("interop-namespace.ts");
        Files.writeString(
                entry,
                """
                import * as math from "java:java.lang.Math";
                console.log(math);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26interop3"))
        );
        assertUnsupportedFeature(
                exception,
                "TSJ26-INTEROP-SYNTAX",
                "Use named imports"
        );
    }

    @Test
    void rejectsInvalidJavaInteropModuleSpecifierWithFeatureDiagnosticMetadata() throws Exception {
        final Path entry = tempDir.resolve("interop-invalid-specifier.ts");
        Files.writeString(
                entry,
                """
                import { max } from "java:java.lang.Math#max";
                console.log(max(1, 2));
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out26interop4"))
        );
        assertUnsupportedFeature(
                exception,
                "TSJ26-INTEROP-MODULE-SPECIFIER",
                "java:<fully.qualified.ClassName>"
        );
    }

    @Test
    void rejectsSuperCallOutsideConstructor() throws Exception {
        final Path sourceFile = tempDir.resolve("invalid-super.ts");
        Files.writeString(
                sourceFile,
                """
                function bad() {
                  super(1);
                }

                bad();
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out19"))
        );
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertTrue(exception.getMessage().contains("super(...)"));
    }

    @Test
    void supportsForLoopInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("for-loop.ts");
        Files.writeString(
                sourceFile,
                """
                for (let i = 0; i < 3; i = i + 1) {
                  console.log(i);
                }
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out7"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("0\n1\n2\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSwitchStatementInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("switch-statement.ts");
        Files.writeString(
                sourceFile,
                """
                let bucket = "init";
                const value = 2;
                switch (value) {
                  case 1:
                    bucket = "one";
                    break;
                  case 2:
                    bucket = "two";
                    break;
                  default:
                    bucket = "other";
                    break;
                }
                console.log(bucket);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out59a-switch"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("two\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsSwitchFallthroughAndDefaultInMiddleInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("switch-fallthrough.ts");
        Files.writeString(
                sourceFile,
                """
                function fallthrough(n: number): string {
                  let r = "";
                  switch (n) {
                    case 1: r += "one";
                    case 2: r += "two"; break;
                    case 3: r += "three"; break;
                  }
                  return r;
                }

                function midDefault(n: number): string {
                  switch (n) {
                    case 1: return "one";
                    default: return "other";
                    case 3: return "three";
                  }
                }

                console.log("fall1=" + fallthrough(1));
                console.log("fall2=" + fallthrough(2));
                console.log("mid1=" + midDefault(1));
                console.log("mid3=" + midDefault(3));
                console.log("mid99=" + midDefault(99));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out59a-switch-fallthrough"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                "fall1=onetwo\nfall2=two\nmid1=one\nmid3=three\nmid99=other\n",
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsLabeledBreakAndContinueInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("labeled-loop-control-flow.ts");
        Files.writeString(
                sourceFile,
                """
                let result1 = 0;
                outer: for (let i = 0; i < 5; i++) {
                  for (let j = 0; j < 5; j++) {
                    if (j === 2) break outer;
                    result1 = result1 + 1;
                  }
                }

                let result2 = 0;
                loop: for (let i = 0; i < 3; i++) {
                  for (let j = 0; j < 3; j++) {
                    if (j === 1) continue loop;
                    result2 = result2 + 1;
                  }
                }

                console.log("labeled_break=" + result1);
                console.log("labeled_continue=" + result2);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out59a-labeled"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("labeled_break=2\nlabeled_continue=3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLabeledContinueTargetingOuterForOfLoopInTsj59bSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("labeled-continue-for-of.ts");
        Files.writeString(
                sourceFile,
                """
                const groups = [[1, 2, 3], [4, 5]];
                let total = 0;
                outer: for (const group of groups) {
                  for (const value of group) {
                    if (value === 5) {
                      break outer;
                    }
                    switch (value) {
                      case 1:
                        total += value;
                        break;
                      case 2:
                        total += value;
                      case 3:
                        total += value;
                        break;
                      default:
                        total += value;
                        break;
                    }
                    if (value === 1) {
                      continue;
                    }
                    if (value === 3) {
                      continue outer;
                    }
                    total += 100;
                  }
                }
                console.log("total=" + total);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out59b-labeled-for-of"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("total=212\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsCommaOperatorEvaluationOrderAndFinalValueInTsj59aSubset() throws Exception {
        final Path sourceFile = tempDir.resolve("comma-operator.ts");
        Files.writeString(
                sourceFile,
                """
                let a = 0;
                const result = (a = a + 1, a = a + 2, a);
                console.log("a=" + a);
                console.log("result=" + result);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out59a-comma"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("a=3\nresult=3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsForLetLoopClosureCapturePerIteration() throws Exception {
        final Path sourceFile = tempDir.resolve("for-let-loop-closure.ts");
        Files.writeString(
                sourceFile,
                """
                const fns: (() => number)[] = [];
                for (let i = 0; i < 3; i++) {
                  fns.push(() => i);
                }
                console.log("v0=" + fns[0]());
                console.log("v1=" + fns[1]());
                console.log("v2=" + fns[2]());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out59a-for-let-loop"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("v0=0\nv1=1\nv2=2\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsDynamicImportWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("dynamic-import.ts");
        Files.writeString(
                sourceFile,
                """
                const specifier = "./dep.ts";
                const loader = import(specifier);
                console.log(loader);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out28"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-DYNAMIC-IMPORT",
                "Use static relative imports"
        );
        assertEquals(2, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsEvalWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("eval.ts");
        Files.writeString(
                sourceFile,
                """
                const value = eval("1 + 2");
                console.log(value);
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out29"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-EVAL",
                "runtime code evaluation"
        );
        assertEquals(1, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsFunctionConstructorWithFeatureDiagnosticMetadata() throws Exception {
        final Path sourceFile = tempDir.resolve("function-constructor.ts");
        Files.writeString(
                sourceFile,
                """
                const factory = new Function("return 7;");
                console.log(factory());
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out30"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-FUNCTION-CONSTRUCTOR",
                "runtime code evaluation"
        );
        assertEquals(1, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void supportsProxyConstructorReflectApiAndRevocableProxy() throws Exception {
        final Path sourceFile = tempDir.resolve("proxy.ts");
        Files.writeString(
                sourceFile,
                """
                const target = { x: 1 };
                const proxy = new Proxy(target, {
                  get(obj: any, prop: string) {
                    return prop in obj ? obj[prop] : -1;
                  },
                  set(obj: any, prop: string, value: any) {
                    obj[prop] = value;
                    return true;
                  }
                });
                proxy.y = 7;
                console.log("proxy_get=" + (proxy.x === 1 && proxy.missing === -1));
                console.log("proxy_set=" + (target.y === 7));
                console.log("reflect_get=" + (Reflect.get(proxy, "x") === 1));
                Reflect.set(proxy, "x", 2);
                console.log("reflect_set=" + (target.x === 2));
                console.log("reflect_has=" + (Reflect.has(proxy, "x") === true));
                console.log("reflect_keys=" + (Reflect.ownKeys({ a: 1, b: 2 }).length === 2));
                const revocable = Proxy.revocable({ data: 42 }, {});
                console.log("revocable=" + (revocable.proxy.data === 42));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out31"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                proxy_get=true
                proxy_set=true
                reflect_get=true
                reflect_set=true
                reflect_has=true
                reflect_keys=true
                revocable=true
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void reportsUnsupportedFeatureLocationFromImportedModule() throws Exception {
        final Path module = tempDir.resolve("dep.ts");
        final Path entry = tempDir.resolve("main.ts");
        Files.writeString(module, "const v = eval(\"2 + 3\");\nconsole.log(v);\n", UTF_8);
        Files.writeString(entry, "import \"./dep.ts\";\nconsole.log(\"main\");\n", UTF_8);

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entry, tempDir.resolve("out32"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ15-EVAL",
                "runtime code evaluation"
        );
        assertEquals(1, exception.line());
        assertEquals(module.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void supportsTsj34ControllerDecoratorLinesInBackendParser() throws Exception {
        final Path sourceFile = tempDir.resolve("decorated-controller.ts");
        Files.writeString(
                sourceFile,
                """
                @RestController
                class EchoController {
                  @GetMapping("/echo")
                  echo(value: string) {
                    return "echo:" + value;
                  }
                }

                const controller = new EchoController();
                console.log(controller.echo("ok"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out33"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("echo:ok\n", stdout.toString(UTF_8));
    }

    @Test
    void stripsSupportedParameterDecoratorsBeforeBackendParsing() throws Exception {
        final Path sourceFile = tempDir.resolve("decorated-controller-params.ts");
        Files.writeString(
                sourceFile,
                """
                @RestController
                class EchoController {
                  @GetMapping("/echo")
                  echo(@RequestParam("value") value: string) {
                    return "echo:" + value;
                  }
                }

                const controller = new EchoController();
                console.log(controller.echo("ok"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out34"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("echo:ok\n", stdout.toString(UTF_8));
    }

    @Test
    void stripsUnknownDecoratorLinesInsteadOfFailingCompilation() throws Exception {
        final Path sourceFile = tempDir.resolve("unsupported-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                @UnknownDecorator
                class Demo {
                  value() {
                    return 1;
                  }
                }
                console.log(new Demo().value());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out34"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("1\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsLegacyDecoratorsForClassMethodPropertyAndStaticMembers() throws Exception {
        final Path sourceFile = tempDir.resolve("legacy-decorators.ts");
        Files.writeString(
                sourceFile,
                """
                function sealed(target: any) {
                  Object.seal(target);
                  Object.seal(target.prototype);
                }

                @sealed
                class Frozen {
                  x = 1;
                  method() { return this.x; }
                }
                const f = new Frozen();
                console.log("class_dec:" + (f.method() === 1));

                function log(_target: any, _key: string, descriptor: PropertyDescriptor) {
                  const orig = descriptor.value;
                  descriptor.value = function (...args: any[]) {
                    return "logged:" + orig.apply(this, args);
                  };
                  return descriptor;
                }

                class Svc {
                  @log
                  hello(name: string) { return name; }
                }
                const svc = new Svc();
                console.log("method_dec:" + (svc.hello("world") === "logged:world"));

                function defaultVal(val: any) {
                  return function (_target: any, key: string) {
                    const symbol = Symbol(key);
                    Object.defineProperty(_target, key, {
                      get() { return this[symbol] ?? val; },
                      set(v: any) { this[symbol] = v; }
                    });
                  };
                }

                class Config {
                  @defaultVal(3000)
                  port!: number;
                  @defaultVal("localhost")
                  host!: string;
                }
                const cfg = new Config();
                console.log("prop_dec:" + (cfg.port === 3000 && cfg.host === "localhost"));

                function addTag(tag: string) {
                  return function (target: any) {
                    target.prototype.tags = (target.prototype.tags || []).concat(tag);
                  };
                }

                @addTag("a")
                @addTag("b")
                class Tagged {}
                const t = new Tagged() as any;
                console.log("multi_dec:" + (t.tags.length === 2));

                class StaticSvc {
                  @log
                  static greet() { return "hello"; }
                }
                console.log("static_dec:" + (StaticSvc.greet() === "logged:hello"));
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out35"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                class_dec:true
                method_dec:true
                prop_dec:true
                multi_dec:true
                static_dec:true
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void supportsStage3ClassDecoratorContextAndReplacementInTsj66Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-class-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function tagged(value: any, context: any) {
                  if (!context || context.kind !== "class") {
                    throw new Error("missing-class-context");
                  }
                  return class extends value {
                    static marker = "stage3";
                  };
                }

                @tagged
                class Example {}

                console.log((Example as any).marker);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-class"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("stage3\n", stdout.toString(UTF_8));
    }

    @Test
    void supportsStage3MethodDecoratorContextAndReplacementInTsj66Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-method-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function wrap(value: any, context: any) {
                  if (!context || context.kind !== "method") {
                    throw new Error("missing-method-context");
                  }
                  if (typeof value !== "function") {
                    throw new Error("missing-method-value");
                  }
                  return function (..._args: any[]) {
                    return String(context.name) + ":decorated";
                  };
                }

                class Example {
                  @wrap
                  work() {
                    return 7;
                  }
                }

                console.log(new Example().work());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-method"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("work:decorated\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsDecoratedPrivateClassElementWithTsj66FeatureDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("decorated-private-member.ts");
        Files.writeString(
                sourceFile,
                """
                function mark(value: any, _context: any) {
                  return value;
                }

                class Demo {
                  @mark
                  #secret() {
                    return 1;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-private"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ66-DECORATOR-PRIVATE-ELEMENT",
                "non-private class elements"
        );
        assertEquals(7, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void supportsStage3FieldDecoratorInitializerTransformInTsj66Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-field-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function addOne(_value: any, context: any) {
                  if (!context || context.kind !== "field" || context.name !== "count" || context.static !== false || context.private !== false) {
                    throw new Error("bad-field-context");
                  }
                  return function (initial: any) {
                    return initial + 1;
                  };
                }

                class Counter {
                  @addOne
                  count = 2;
                }

                console.log(new Counter().count);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-field"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("3\n", stdout.toString(UTF_8));
    }

    @Test
    void rejectsStage3AccessorDecoratorWithTsj66FeatureDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-accessor-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function mark(value: any, _context: any) {
                  return value;
                }

                class Demo {
                  @mark
                  accessor value = 1;
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-accessor"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ66-DECORATOR-STAGE3-ACCESSOR",
                "Use field or method decorators"
        );
        assertEquals(7, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsStage3GetterDecoratorWithTsj66FeatureDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-getter-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function mark(value: any, _context: any) {
                  return value;
                }

                class Demo {
                  @mark
                  get value() {
                    return 1;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-getter"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ66-DECORATOR-STAGE3-ACCESSOR",
                "Use field or method decorators"
        );
        assertEquals(7, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsStage3SetterDecoratorWithTsj66FeatureDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-setter-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function mark(value: any, _context: any) {
                  return value;
                }

                class Demo {
                  @mark
                  set value(next: number) {
                    console.log(next);
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-setter"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ66-DECORATOR-STAGE3-ACCESSOR",
                "Use field or method decorators"
        );
        assertEquals(7, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void rejectsStage3ParameterDecoratorWithTsj66FeatureDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("stage3-parameter-decorator.ts");
        Files.writeString(
                sourceFile,
                """
                function mark(value: any, _context: any) {
                  return value;
                }

                class Demo {
                  method(@mark value: number) {
                    return value;
                  }
                }
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-stage3-parameter"))
        );

        assertUnsupportedFeature(
                exception,
                "TSJ66-DECORATOR-STAGE3-PARAMETER",
                "legacy parameter decorator factories"
        );
        assertEquals(6, exception.line());
        assertEquals(sourceFile.toAbsolutePath().normalize().toString(), exception.sourceFile());
    }

    @Test
    void supportsLegacyMethodDecoratorFactoryCallExpressionInTsj66Subset() throws Exception {
        final Path sourceFile = tempDir.resolve("legacy-method-decorator-factory.ts");
        Files.writeString(
                sourceFile,
                """
                function wrap(prefix: string) {
                  return function (_target: any, _key: string, descriptor: PropertyDescriptor) {
                    const original = descriptor.value;
                    descriptor.value = function (...args: any[]) {
                      return prefix + ":" + original.apply(this, args);
                    };
                    return descriptor;
                  };
                }

                class Demo {
                  @wrap("p")
                  value() {
                    return "ok";
                  }
                }

                console.log(new Demo().value());
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-legacy-method-factory"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals("p:ok\n", stdout.toString(UTF_8));
    }

    @Test
    void compilesDeclarationFilesAsNoOpPrograms() throws Exception {
        final Path declarationFile = tempDir.resolve("ambient.d.ts");
        Files.writeString(
                declarationFile,
                """
                declare namespace Ambient {
                  const value: number;
                }

                export as namespace AmbientGlobal;
                export {};
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(declarationFile, tempDir.resolve("out-dts"));
        assertTrue(Files.exists(artifact.classFile()));
    }

    @Test
    void mapsBridgeParseDiagnosticsBackToOriginalModuleFile() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        final Path brokenDependency = tempDir.resolve("broken.ts");
        Files.writeString(
                entryFile,
                """
                import { value } from "./broken";
                console.log(value);
                """,
                UTF_8
        );
        Files.writeString(
                brokenDependency,
                """
                export const value = ;
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(entryFile, tempDir.resolve("out58a"))
        );

        assertTrue(exception.code().startsWith("TS"));
        assertTrue(exception.sourceFile() != null && exception.sourceFile().endsWith("broken.ts"));
        assertEquals(1, exception.line());
        assertTrue(exception.column() != null && exception.column() >= 1);
    }

    @Test
    void canFallbackToLegacyTokenizerWhenTokenBridgeIsBroken() throws Exception {
        final Path sourceFile = tempDir.resolve("legacy-tokenizer.ts");
        final Path failingBridgeScript = tempDir.resolve("bridge-fails.cjs");
        Files.writeString(
                sourceFile,
                """
                const value = 41 + 1;
                console.log("legacy=" + value);
                """,
                UTF_8
        );
        Files.writeString(
                failingBridgeScript,
                """
                process.stderr.write("bridge intentionally failed");
                process.exit(1);
                """,
                UTF_8
        );

        final String previousBridgeScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, failingBridgeScript.toString());
            System.setProperty(LEGACY_TOKENIZER_PROPERTY, "true");

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58b"));
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

            assertEquals("legacy=42\n", stdout.toString(UTF_8));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousBridgeScript);
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
        }
    }

    @Test
    void reportsBridgeSchemaErrorsWhenConfiguredBridgeIsInvalid() throws Exception {
        final Path sourceFile = tempDir.resolve("bridge-schema.ts");
        final Path invalidBridgeScript = tempDir.resolve("bridge-invalid-schema.cjs");
        Files.writeString(
                sourceFile,
                """
                console.log("schema");
                """,
                UTF_8
        );
        Files.writeString(
                invalidBridgeScript,
                """
                process.stdout.write(JSON.stringify({
                  schemaVersion: "tsj-backend-token-v0",
                  diagnostics: [],
                  tokens: []
                }));
                """,
                UTF_8
        );

        final String previousBridgeScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, invalidBridgeScript.toString());
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);

            final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    JvmCompilationException.class,
                    () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58c"))
            );

            assertEquals("TSJ-BACKEND-AST-SCHEMA", exception.code());
            assertTrue(exception.getMessage().contains("tsj-backend-token-v0"));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousBridgeScript);
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
        }
    }

    @Test
    void reportsBridgeSchemaErrorsWhenAstPayloadIsMissing() throws Exception {
        final Path sourceFile = tempDir.resolve("bridge-missing-ast.ts");
        final Path invalidBridgeScript = tempDir.resolve("bridge-missing-ast.cjs");
        Files.writeString(
                sourceFile,
                """
                console.log("schema");
                """,
                UTF_8
        );
        Files.writeString(
                invalidBridgeScript,
                """
                process.stdout.write(JSON.stringify({
                  schemaVersion: "tsj-backend-token-v1",
                  diagnostics: [],
                  tokens: [{ type: "KEYWORD", text: "const", line: 1, column: 1 }]
                }));
                """,
                UTF_8
        );

        final String previousBridgeScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, invalidBridgeScript.toString());
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);

            final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    JvmCompilationException.class,
                    () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58d"))
            );

            assertEquals("TSJ-BACKEND-AST-SCHEMA", exception.code());
            assertTrue(exception.getMessage().contains("astNodes"));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousBridgeScript);
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
        }
    }

    @Test
    void defaultsToAstNoFallbackWhenBridgeOmitsNormalizedProgram() throws Exception {
        final Path sourceFile = tempDir.resolve("bridge-normalized-null.ts");
        final Path bridgeScript = tempDir.resolve("bridge-normalized-null.cjs");
        Files.writeString(
                sourceFile,
                """
                console.log("ok");
                """,
                UTF_8
        );
        Files.writeString(
                bridgeScript,
                """
                process.stdout.write(JSON.stringify({
                  schemaVersion: "tsj-backend-token-v1",
                  diagnostics: [],
                  tokens: [{ type: "EOF", text: "", line: 1, column: 1 }],
                  astNodes: [{ kind: "SourceFile", line: 1, column: 1, endLine: 1, endColumn: 1 }],
                  normalizedProgram: null,
                  normalizationDiagnostics: []
                }));
                """,
                UTF_8
        );

        final String previousBridgeScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        final String previousAstNoFallback = System.getProperty(AST_NO_FALLBACK_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, bridgeScript.toString());
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);
            System.clearProperty(AST_NO_FALLBACK_PROPERTY);

            final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    JvmCompilationException.class,
                    () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58h"))
            );
            assertEquals("TSJ-BACKEND-AST-LOWERING", exception.code());
            assertTrue(exception.getMessage().contains("parser fallback is disabled"));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousBridgeScript);
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
            restoreSystemProperty(AST_NO_FALLBACK_PROPERTY, previousAstNoFallback);
        }
    }

    @Test
    void allowsDebugParserFallbackWhenAstNoFallbackExplicitlyDisabled() throws Exception {
        final Path sourceFile = tempDir.resolve("bridge-normalized-null-debug-fallback.ts");
        final Path bridgeScript = tempDir.resolve("bridge-normalized-null-debug-fallback.cjs");
        Files.writeString(
                sourceFile,
                """
                console.log("ok");
                """,
                UTF_8
        );
        Files.writeString(
                bridgeScript,
                """
                process.stdout.write(JSON.stringify({
                  schemaVersion: "tsj-backend-token-v1",
                  diagnostics: [],
                  tokens: [{ type: "EOF", text: "", line: 1, column: 1 }],
                  astNodes: [{ kind: "SourceFile", line: 1, column: 1, endLine: 1, endColumn: 1 }],
                  normalizedProgram: null,
                  normalizationDiagnostics: []
                }));
                """,
                UTF_8
        );

        final String previousBridgeScript = System.getProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY);
        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        final String previousAstNoFallback = System.getProperty(AST_NO_FALLBACK_PROPERTY);
        try {
            System.setProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, bridgeScript.toString());
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);
            System.setProperty(AST_NO_FALLBACK_PROPERTY, "false");

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58i"));
            assertTrue(Files.exists(artifact.classFile()));
        } finally {
            restoreSystemProperty(TOKEN_BRIDGE_SCRIPT_PROPERTY, previousBridgeScript);
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
            restoreSystemProperty(AST_NO_FALLBACK_PROPERTY, previousAstNoFallback);
        }
    }

    @Test
    void canCompileSimpleProgramWithAstOnlyPathWhenParserFallbackDisabled() throws Exception {
        final Path sourceFile = tempDir.resolve("ast-only-simple.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 40 + 2;
                console.log("ast=" + value);
                """,
                UTF_8
        );

        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        final String previousAstNoFallback = System.getProperty(AST_NO_FALLBACK_PROPERTY);
        try {
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);
            System.setProperty(AST_NO_FALLBACK_PROPERTY, "true");

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58e"));
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

            assertEquals("ast=42\n", stdout.toString(UTF_8));
        } finally {
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
            restoreSystemProperty(AST_NO_FALLBACK_PROPERTY, previousAstNoFallback);
        }
    }

    @Test
    void canCompileClassProgramWithAstOnlyPathWhenParserFallbackDisabled() throws Exception {
        final Path sourceFile = tempDir.resolve("ast-only-class.ts");
        Files.writeString(
                sourceFile,
                """
                class Counter {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }

                  inc(delta: number) {
                    this.value = this.value + delta;
                    return this.value;
                  }
                }

                const counter = new Counter(10);
                console.log("ast-class=" + counter.inc(5));
                """,
                UTF_8
        );

        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        final String previousAstNoFallback = System.getProperty(AST_NO_FALLBACK_PROPERTY);
        try {
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);
            System.setProperty(AST_NO_FALLBACK_PROPERTY, "true");

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58f"));
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

            assertEquals("ast-class=15\n", stdout.toString(UTF_8));
        } finally {
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
            restoreSystemProperty(AST_NO_FALLBACK_PROPERTY, previousAstNoFallback);
        }
    }

    @Test
    void canCompileInheritedClassProgramWithAstOnlyPathWhenParserFallbackDisabled() throws Exception {
        final Path sourceFile = tempDir.resolve("ast-only-class-inheritance.ts");
        Files.writeString(
                sourceFile,
                """
                class Base {
                  value: number;
                  constructor(seed: number) {
                    this.value = seed;
                  }
                }

                class Derived extends Base {
                  constructor(seed: number) {
                    super(seed);
                  }

                  plus(delta: number) {
                    return this.value + delta;
                  }
                }

                const derived = new Derived(4);
                console.log("ast-inherit=" + derived.plus(3));
                """,
                UTF_8
        );

        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        final String previousAstNoFallback = System.getProperty(AST_NO_FALLBACK_PROPERTY);
        try {
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);
            System.setProperty(AST_NO_FALLBACK_PROPERTY, "true");

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58g"));
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

            assertEquals("ast-inherit=7\n", stdout.toString(UTF_8));
        } finally {
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
            restoreSystemProperty(AST_NO_FALLBACK_PROPERTY, previousAstNoFallback);
        }
    }

    @Test
    void supportsAstOnlyPathForForAwaitOfWithAsyncGeneratorAndPromiseArray() throws Exception {
        final Path sourceFile = tempDir.resolve("ast-only-unsupported.ts");
        Files.writeString(
                sourceFile,
                """
                async function* range(n: number) {
                  for (let i = 0; i < n; i++) {
                    yield i;
                  }
                }

                async function run() {
                  const values: number[] = [];
                  for await (const item of range(3)) {
                    values.push(item);
                  }
                  const promised = [Promise.resolve(4), Promise.resolve(5)];
                  let sum = 0;
                  for await (const value of promised) {
                    sum = sum + value;
                  }
                  console.log("for-await=" + (values.length === 3 && values[2] === 2 && sum === 9));
                }
                run();
                """,
                UTF_8
        );

        final String previousLegacyTokenizer = System.getProperty(LEGACY_TOKENIZER_PROPERTY);
        final String previousAstNoFallback = System.getProperty(AST_NO_FALLBACK_PROPERTY);
        try {
            System.clearProperty(LEGACY_TOKENIZER_PROPERTY);
            System.setProperty(AST_NO_FALLBACK_PROPERTY, "true");

            final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out58f"));
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

            assertEquals("for-await=true\n", stdout.toString(UTF_8));
        } finally {
            restoreSystemProperty(LEGACY_TOKENIZER_PROPERTY, previousLegacyTokenizer);
            restoreSystemProperty(AST_NO_FALLBACK_PROPERTY, previousAstNoFallback);
        }
    }

    @Test
    void rejectsTsxInputWithTsj67FeatureDiagnostic() throws Exception {
        final Path sourceFile = tempDir.resolve("tsx-out-of-scope.tsx");
        Files.writeString(
                sourceFile,
                """
                export const View = <div>tsx</div>;
                """,
                UTF_8
        );

        final JvmCompilationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                JvmCompilationException.class,
                () -> new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out-tsx"))
        );
        assertUnsupportedFeature(exception, "TSJ67-TSX-OUT-OF-SCOPE", "TSX/JSX is not in scope");
    }

    @Test
    void reportsIncrementalCacheMissHitAndInvalidationAcrossModuleGraphChanges() throws Exception {
        final String previousIncrementalCache = System.getProperty("tsj.backend.incrementalCache");
        try {
            System.setProperty("tsj.backend.incrementalCache", "true");

            final Path dependency = tempDir.resolve("dep.ts");
            Files.writeString(dependency, "export let value = 1;\n", UTF_8);
            final Path sourceFile = tempDir.resolve("incremental-main.ts");
            Files.writeString(
                    sourceFile,
                    """
                    import { value } from "./dep.ts";
                    console.log("value=" + value);
                    """,
                    UTF_8
            );

            final JvmBytecodeCompiler compiler = new JvmBytecodeCompiler();
            compiler.compile(sourceFile, tempDir.resolve("incremental-out-1"));
            final JvmBytecodeCompiler.IncrementalCompilationReport first = compiler.lastIncrementalCompilationReport();
            assertTrue(first.cacheEnabled());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.MISS, first.frontend());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.MISS, first.lowering());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.MISS, first.backend());
            assertTrue(!first.sourceGraphFingerprint().isBlank());

            compiler.compile(sourceFile, tempDir.resolve("incremental-out-2"));
            final JvmBytecodeCompiler.IncrementalCompilationReport second = compiler.lastIncrementalCompilationReport();
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.HIT, second.frontend());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.HIT, second.lowering());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.MISS, second.backend());
            assertEquals(first.sourceGraphFingerprint(), second.sourceGraphFingerprint());

            Files.writeString(dependency, "export let value = 2;\n", UTF_8);
            compiler.compile(sourceFile, tempDir.resolve("incremental-out-3"));
            final JvmBytecodeCompiler.IncrementalCompilationReport third = compiler.lastIncrementalCompilationReport();
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.INVALIDATED, third.frontend());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.INVALIDATED, third.lowering());
            assertEquals(JvmBytecodeCompiler.IncrementalStageState.MISS, third.backend());
            assertFalse(first.sourceGraphFingerprint().equals(third.sourceGraphFingerprint()));
        } finally {
            restoreSystemProperty("tsj.backend.incrementalCache", previousIncrementalCache);
        }
    }

    @Test
    void emitsVerifierSafeClassThatCanBeLoadedReflectively() throws Exception {
        final Path sourceFile = tempDir.resolve("loadable.ts");
        Files.writeString(
                sourceFile,
                """
                const value = 4 * 5;
                console.log("value=" + value);
                """,
                UTF_8
        );

        final JvmCompiledArtifact artifact = new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("out8"));
        final Class<?> mainClass = new JvmBytecodeRunner().loadMainClass(artifact);

        assertEquals(artifact.className(), mainClass.getName());
        assertTrue(Files.exists(artifact.classFile()));
    }

    @Test
    void grammarProofExampleAppCompilesAndRunsWithExpectedOutput() throws Exception {
        final Path repoRoot = locateRepositoryRoot();
        final Path sourceFile = repoRoot.resolve("examples/grammar-proof-app/src/main.ts");

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("grammar-proof-example"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                --- grammar-proof ---
                logical:false|true|fallback
                assign:7|0|filled|alt|next
                conditional:LE2
                optional:4|undefined|ok|undefined
                template:hello tsj #3
                """,
                stdout.toString(UTF_8)
        );
    }

    @Test
    void grammarProofNextExampleAppCompilesAndRunsWithExpectedOutput() throws Exception {
        final Path repoRoot = locateRepositoryRoot();
        final Path sourceFile = repoRoot.resolve("examples/grammar-proof-next-app/src/main.ts");

        final JvmCompiledArtifact artifact =
                new JvmBytecodeCompiler().compile(sourceFile, tempDir.resolve("grammar-proof-next-example"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        new JvmBytecodeRunner().run(artifact, new PrintStream(stdout));

        assertEquals(
                """
                --- grammar-proof-next ---
                spread:1|2|3|4|10|1|2|3|4
                params:10|11|0|none|none|3|4|1|9|none|P-x-2|5|2
                loops:4|ab|b|10
                class:2|5|3|4
                ts-only:14|ok
                """,
                stdout.toString(UTF_8)
        );
    }

    private static Object invokeStrictInstanceMethod(
            final URLClassLoader classLoader,
            final String simpleName,
            final String methodName,
            final Object[] constructorArgs,
            final Object[] methodArgs
    ) throws Exception {
        final Class<?> strictClass = Class.forName(
                "dev.tsj.generated." + simpleName + "__TsjStrictNative",
                true,
                classLoader
        );
        final Object instance = resolveCompatibleConstructor(strictClass, constructorArgs).newInstance(constructorArgs);
        final Method method = resolveCompatibleMethod(strictClass, methodName, methodArgs);
        return method.invoke(instance, methodArgs);
    }

    private static Constructor<?> resolveCompatibleConstructor(final Class<?> type, final Object[] args)
            throws NoSuchMethodException {
        for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isCompatibleExecutable(constructor.getParameterTypes(), args)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#<init>(" + args.length + ")");
    }

    private static Method resolveCompatibleMethod(final Class<?> type, final String methodName, final Object[] args)
            throws NoSuchMethodException {
        Class<?> cursor = type;
        while (cursor != null) {
            for (final Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && isCompatibleExecutable(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "#" + methodName + "(" + args.length + ")");
    }

    private static boolean isCompatibleExecutable(final Class<?>[] parameterTypes, final Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!isCompatibleParameter(parameterTypes[index], args[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompatibleParameter(final Class<?> parameterType, final Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isInstance(arg);
        }
        return (parameterType == boolean.class && arg instanceof Boolean)
                || (parameterType == byte.class && arg instanceof Byte)
                || (parameterType == short.class && arg instanceof Short)
                || (parameterType == int.class && arg instanceof Integer)
                || (parameterType == long.class && arg instanceof Long)
                || (parameterType == float.class && arg instanceof Float)
                || (parameterType == double.class && arg instanceof Double)
                || (parameterType == char.class && arg instanceof Character);
    }

    private static void restoreSystemProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static Path locateRepositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            final Path marker = cursor.resolve("examples/grammar-proof-app/src/main.ts");
            if (Files.exists(marker)) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not locate repository root for grammar-proof example.");
    }

    private static boolean hasParameterAnnotation(
            final JavaClassfileReader.RawMethodInfo method,
            final int index,
            final String descriptor
    ) {
        if (index < method.runtimeVisibleParameterAnnotations().size()
                && hasAnnotation(method.runtimeVisibleParameterAnnotations().get(index), List.of(), descriptor)) {
            return true;
        }
        return index < method.runtimeInvisibleParameterAnnotations().size()
                && hasAnnotation(method.runtimeInvisibleParameterAnnotations().get(index), List.of(), descriptor);
    }

    private static boolean hasAnnotation(
            final List<JavaClassfileReader.RawAnnotationInfo> visible,
            final List<JavaClassfileReader.RawAnnotationInfo> invisible,
            final String descriptor
    ) {
        return visible.stream().anyMatch(annotation -> descriptor.equals(annotation.descriptor()))
                || invisible.stream().anyMatch(annotation -> descriptor.equals(annotation.descriptor()));
    }

    private static void assertUnsupportedFeature(
            final JvmCompilationException exception,
            final String featureId,
            final String guidanceSnippet
    ) {
        assertEquals("TSJ-BACKEND-UNSUPPORTED", exception.code());
        assertEquals(featureId, exception.featureId());
        assertTrue(exception.getMessage().contains(featureId));
        assertTrue(exception.getMessage().contains(guidanceSnippet));
        assertTrue(exception.guidance() != null && exception.guidance().contains(guidanceSnippet));
        assertTrue(exception.sourceFile() != null && !exception.sourceFile().isBlank());
    }

    private static String generatedJavaSource(final Path outDir, final JvmCompiledArtifact artifact) throws Exception {
        final String simpleName = artifact.className().substring(artifact.className().lastIndexOf('.') + 1);
        final Path generatedSource = outDir.resolve("generated-src/dev/tsj/generated/" + simpleName + ".java");
        return Files.readString(generatedSource, UTF_8);
    }

    private static int runtimeOperationCount(final String javaSource) {
        final String marker = "dev.tsj.runtime.TsjRuntime.";
        int count = 0;
        int index = javaSource.indexOf(marker);
        while (index >= 0) {
            count++;
            index = javaSource.indexOf(marker, index + marker.length());
        }
        return count;
    }
}
