package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsFrontendDecoratorModelExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsImportedRuntimeDecoratorsFromDefiniteFieldAndMultilineCtorMethodSignatures() throws Exception {
        final Path sourceFile = tempDir.resolve("frontend-decorator-model.ts");
        Files.writeString(
                sourceFile,
                """
                import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
                import { CtorMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark";
                import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                @TypeMark
                class Demo {
                  @FieldMark
                  value!: number;

                  @CtorMark
                  constructor(
                    @ParamMark name: string,
                  ) {
                    this.value = 1;
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

        final Path testClasses = Path.of("target/test-classes").toAbsolutePath().normalize();
        final TsFrontendDecoratorModelExtractor extractor = new TsFrontendDecoratorModelExtractor(
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(testClasses), testClasses.toString())),
                false
        );

        final TsFrontendDecoratorModelExtractor.ExtractionResult result =
                extractor.extractWithImportedDecoratorBindings(sourceFile);
        final TsDecoratedClass decoratedClass = result.model().classes().stream()
                .filter(value -> "Demo".equals(value.className()))
                .findFirst()
                .orElseThrow();

        assertTrue(decoratedClass.decorators().stream().anyMatch(value -> "TypeMark".equals(value.name())));
        assertEquals(1, decoratedClass.fields().size());
        assertTrue(decoratedClass.fields().getFirst().decorators().stream()
                .anyMatch(value -> "FieldMark".equals(value.name())));

        final TsDecoratedMethod constructor = decoratedClass.methods().stream()
                .filter(TsDecoratedMethod::constructor)
                .findFirst()
                .orElseThrow();
        assertTrue(constructor.decorators().stream().anyMatch(value -> "CtorMark".equals(value.name())));
        assertEquals("string", constructor.parameters().getFirst().typeAnnotation());
        assertTrue(constructor.parameters().getFirst().decorators().stream()
                .anyMatch(value -> "ParamMark".equals(value.name())));

        final TsDecoratedMethod greet = decoratedClass.methods().stream()
                .filter(value -> "greet".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertTrue(greet.decorators().stream().anyMatch(value -> "MethodMark".equals(value.name())));
        assertEquals("string", greet.parameters().getFirst().typeAnnotation());
        assertTrue(greet.parameters().getFirst().decorators().stream()
                .anyMatch(value -> "ParamMark".equals(value.name())));

        final Map<String, String> importedBindings = result.importedDecoratorBindingsByFile().get(sourceFile.toAbsolutePath().normalize());
        assertEquals(
                Map.of(
                        "TypeMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark",
                        "FieldMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark",
                        "CtorMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.CtorMark",
                        "MethodMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark",
                        "ParamMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark"
                ),
                importedBindings
        );
    }

    @Test
    void extractsImportedRuntimeDecoratorsAcrossRelativeImportGraphForAliasedGenericClassShapes() throws Exception {
        final Path entryFile = tempDir.resolve("main.ts");
        final Path dependencyFile = tempDir.resolve("repo.ts");
        Files.writeString(
                entryFile,
                """
                import "./repo.ts";
                import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                @TypeMark
                class Controller {
                  @MethodMark
                  handle(
                    @ParamMark id: string,
                  ) {
                    return id;
                  }
                }
                """,
                UTF_8
        );
        Files.writeString(
                dependencyFile,
                """
                import { TypeMark as ClassMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { MethodMark as QueryMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark as InputMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                @ClassMark
                class Repo<TValue extends string> {
                  @QueryMark
                  load(
                    @InputMark id: TValue,
                  ) {
                    return id;
                  }
                }
                """,
                UTF_8
        );

        final Path testClasses = Path.of("target/test-classes").toAbsolutePath().normalize();
        final TsFrontendDecoratorModelExtractor extractor = new TsFrontendDecoratorModelExtractor(
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(testClasses), testClasses.toString())),
                false
        );

        final TsFrontendDecoratorModelExtractor.ExtractionResult result =
                extractor.extractWithImportedDecoratorBindings(entryFile);
        assertEquals(2, result.model().classes().size());

        final TsDecoratedClass controller = result.model().classes().stream()
                .filter(value -> "Controller".equals(value.className()))
                .findFirst()
                .orElseThrow();
        assertTrue(controller.decorators().stream().anyMatch(value -> "TypeMark".equals(value.name())));
        assertTrue(controller.methods().getFirst().decorators().stream()
                .anyMatch(value -> "MethodMark".equals(value.name())));

        final TsDecoratedClass repo = result.model().classes().stream()
                .filter(value -> "Repo".equals(value.className()))
                .findFirst()
                .orElseThrow();
        assertTrue(repo.decorators().stream().anyMatch(value -> "ClassMark".equals(value.name())));
        final TsDecoratedMethod load = repo.methods().stream()
                .filter(value -> "load".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertTrue(load.decorators().stream().anyMatch(value -> "QueryMark".equals(value.name())));
        assertEquals("TValue", load.parameters().getFirst().typeAnnotation());
        assertTrue(load.parameters().getFirst().decorators().stream()
                .anyMatch(value -> "InputMark".equals(value.name())));

        assertEquals(
                Map.of(
                        "ClassMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark",
                        "QueryMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark",
                        "InputMark", "dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark"
                ),
                result.importedDecoratorBindingsByFile().get(dependencyFile.toAbsolutePath().normalize())
        );
    }

    @Test
    void extractsDeclarationModelDetailsForGenericHeritageVisibilityAndSpans() throws Exception {
        final Path sourceFile = tempDir.resolve("declaration-details.ts");
        Files.writeString(
                sourceFile,
                """
                import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
                import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
                import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
                import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

                interface Runner {
                  run(): string;
                }

                abstract class BaseRepo<TBase> {}

                @TypeMark
                class Repo<TValue extends string> extends BaseRepo<TValue> implements Runner {
                  @FieldMark
                  private readonly store!: Map<string, TValue>;

                  constructor(@ParamMark private readonly source: string) {}

                  @MethodMark
                  protected load<TQuery extends TValue>(@ParamMark id: TQuery): TValue {
                    return id;
                  }

                  run(): string {
                    return this.source;
                  }
                }
                """,
                UTF_8
        );

        final Path testClasses = Path.of("target/test-classes").toAbsolutePath().normalize();
        final TsFrontendDecoratorModelExtractor extractor = new TsFrontendDecoratorModelExtractor(
                new TsDecoratorClasspathResolver(new JavaSymbolTable(List.of(testClasses), testClasses.toString())),
                false
        );

        final TsFrontendDecoratorModelExtractor.ExtractionResult result =
                extractor.extractWithImportedDecoratorBindings(sourceFile);
        final TsDecoratedClass repo = result.model().classes().stream()
                .filter(value -> "Repo".equals(value.className()))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("TValue extends string"), repo.genericParameters());
        assertEquals("BaseRepo<TValue>", repo.extendsType());
        assertEquals(List.of("Runner"), repo.implementsTypes());
        assertEquals(new TsSourceSpan(12, 1, 27, 2), repo.span());

        final TsDecoratedField store = repo.fields().getFirst();
        assertEquals(TsVisibility.PRIVATE, store.visibility());
        assertEquals("Map<string, TValue>", store.typeAnnotation());
        assertEquals(new TsSourceSpan(14, 3, 15, 48), store.span());

        final TsDecoratedMethod constructor = repo.methods().stream()
                .filter(TsDecoratedMethod::constructor)
                .findFirst()
                .orElseThrow();
        assertEquals(TsVisibility.PUBLIC, constructor.visibility());
        assertEquals(new TsSourceSpan(17, 3, 17, 61), constructor.span());
        assertEquals(TsVisibility.PRIVATE, constructor.parameters().getFirst().visibility());

        final TsDecoratedMethod load = repo.methods().stream()
                .filter(value -> "load".equals(value.methodName()))
                .findFirst()
                .orElseThrow();
        assertEquals(TsVisibility.PROTECTED, load.visibility());
        assertEquals(List.of("TQuery extends TValue"), load.genericParameters());
        assertEquals("TValue", load.returnTypeAnnotation());
        assertEquals(new TsSourceSpan(19, 3, 22, 4), load.span());
        assertEquals(new TsSourceSpan(20, 41, 20, 62), load.parameters().getFirst().span());
    }
}
