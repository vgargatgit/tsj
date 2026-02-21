package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjValidationSubsetEvaluatorTest {
    @TempDir
    Path tempDir;

    @Test
    void collectsValidatedMethodsAndConstraintsFromSupportedSubset() throws Exception {
        final Path entryFile = writeValidationFixture(
                """
                @Service
                @Validated
                class ValidationService {
                  validate(@NotBlank({ message: "username.required" }) username: string, @Size({ min: 3, max: 8, message: "alias.length" }) alias: string, @Min({ value: 18, message: "age.min" }) age: number, @Max({ value: 65, message: "age.max" }) score: number, @NotNull({ message: "email.required" }) email: any) {
                    return username + ":" + alias + ":" + age + ":" + score + ":" + email;
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final List<TsjValidationSubsetEvaluator.ValidatedMethod> methods =
                new TsjValidationSubsetEvaluator().collectValidatedMethods(model);

        assertEquals(1, methods.size());
        final TsjValidationSubsetEvaluator.ValidatedMethod method = methods.getFirst();
        assertEquals("ValidationService", method.className());
        assertEquals("validate", method.methodName());
        assertEquals(5, method.parameters().size());
        assertEquals("username", method.parameters().get(0).name());
        assertEquals("NotBlank", method.parameters().get(0).constraints().getFirst().name());
        assertEquals("username.required", method.parameters().get(0).constraints().getFirst().message());
        assertEquals("Size", method.parameters().get(1).constraints().getFirst().name());
        assertEquals("Min", method.parameters().get(2).constraints().getFirst().name());
        assertEquals("Max", method.parameters().get(3).constraints().getFirst().name());
        assertEquals("NotNull", method.parameters().get(4).constraints().getFirst().name());
    }

    @Test
    void evaluatesViolationsWithDeterministicFieldAndMessageMapping() throws Exception {
        final Path entryFile = writeValidationFixture(
                """
                @Service
                @Validated
                class ValidationService {
                  validate(@NotBlank({ message: "username.required" }) username: string, @Size({ min: 3, max: 8, message: "alias.length" }) alias: string, @Min({ value: 18, message: "age.min" }) age: number, @Max({ value: 65, message: "age.max" }) score: number, @NotNull({ message: "email.required" }) email: any) {
                    return username + ":" + alias + ":" + age + ":" + score + ":" + email;
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final TsjValidationSubsetEvaluator evaluator = new TsjValidationSubsetEvaluator();
        final TsjValidationSubsetEvaluator.ValidatedMethod method = evaluator
                .collectValidatedMethods(model)
                .getFirst();

        assertTrue(evaluator.validate(method, new Object[]{"alice", "agent", 21, 40, "a@b.com"}).isEmpty());

        final List<TsjValidationSubsetEvaluator.ConstraintViolation> notBlankViolation =
                evaluator.validate(method, new Object[]{"  ", "agent", 21, 40, "a@b.com"});
        assertEquals(1, notBlankViolation.size());
        assertEquals("username", notBlankViolation.getFirst().field());
        assertEquals("username.required", notBlankViolation.getFirst().message());

        final List<TsjValidationSubsetEvaluator.ConstraintViolation> sizeViolation =
                evaluator.validate(method, new Object[]{"alice", "ab", 21, 40, "a@b.com"});
        assertEquals(1, sizeViolation.size());
        assertEquals("alias", sizeViolation.getFirst().field());
        assertEquals("alias.length", sizeViolation.getFirst().message());

        final List<TsjValidationSubsetEvaluator.ConstraintViolation> minViolation =
                evaluator.validate(method, new Object[]{"alice", "agent", 10, 40, "a@b.com"});
        assertEquals(1, minViolation.size());
        assertEquals("age", minViolation.getFirst().field());
        assertEquals("age.min", minViolation.getFirst().message());

        final List<TsjValidationSubsetEvaluator.ConstraintViolation> maxViolation =
                evaluator.validate(method, new Object[]{"alice", "agent", 21, 99, "a@b.com"});
        assertEquals(1, maxViolation.size());
        assertEquals("score", maxViolation.getFirst().field());
        assertEquals("age.max", maxViolation.getFirst().message());

        final List<TsjValidationSubsetEvaluator.ConstraintViolation> notNullViolation =
                evaluator.validate(method, new Object[]{"alice", "agent", 21, 40, null});
        assertEquals(1, notNullViolation.size());
        assertEquals("email", notNullViolation.getFirst().field());
        assertEquals("email.required", notNullViolation.getFirst().message());
    }

    @Test
    void rejectsInvalidConstraintAttributesWithTargetedDiagnostic() throws Exception {
        final Path entryFile = writeValidationFixture(
                """
                @Service
                @Validated
                class ValidationService {
                  validate(@Size({ min: -1, message: "bad" }) value: string) {
                    return value;
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjValidationSubsetEvaluator().collectValidatedMethods(model)
        );

        assertEquals("TSJ-VALIDATION-CONSTRAINT", exception.code());
        assertEquals("TSJ37A-VALIDATION", exception.featureId());
        assertTrue(exception.getMessage().contains("min"));
    }

    private Path writeValidationFixture(final String source) throws Exception {
        final Path entryFile = tempDir.resolve("validation.ts");
        Files.writeString(entryFile, source, UTF_8);
        return entryFile;
    }
}
