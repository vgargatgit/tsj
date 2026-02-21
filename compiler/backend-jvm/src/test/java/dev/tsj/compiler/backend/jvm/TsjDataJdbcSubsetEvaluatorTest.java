package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TsjDataJdbcSubsetEvaluatorTest {
    @TempDir
    Path tempDir;

    @Test
    void collectsRepositoryQueriesAndTransactionalServiceMethodsFromSupportedSubset() throws Exception {
        final Path entryFile = writeFixture(
                """
                @Repository
                class OrderRepository {
                  countByStatus(status: string) {
                    return status == "OPEN" ? 2 : 0;
                  }

                  findById(id: number) {
                    return id == 101 ? "OPEN" : undefined;
                  }
                }

                @Service
                class OrderService {
                  @Transactional
                  reportOpenCount() {
                    return new OrderRepository().countByStatus("OPEN");
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final TsjDataJdbcSubsetEvaluator.DataJdbcSubset subset = new TsjDataJdbcSubsetEvaluator().analyze(model, entryFile);

        assertEquals("OrderRepository", subset.repositoryClassName());
        assertEquals("OrderService", subset.serviceClassName());
        assertEquals(2, subset.repositoryQueries().size());
        assertEquals("countByStatus", subset.repositoryQueries().get(0).methodName());
        assertEquals(1, subset.repositoryQueries().get(0).parameterCount());
        assertEquals("findById", subset.repositoryQueries().get(1).methodName());
        assertEquals(1, subset.transactionalServiceMethods().size());
        assertEquals("reportOpenCount", subset.transactionalServiceMethods().get(0));
    }

    @Test
    void failsWithWiringDiagnosticWhenRepositoryIsMissing() throws Exception {
        final Path entryFile = writeFixture(
                """
                @Service
                class OrderService {
                  @Transactional
                  reportOpenCount() {
                    return 0;
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjDataJdbcSubsetEvaluator().analyze(model, entryFile)
        );

        assertEquals("TSJ-SPRING-DATA-WIRING", exception.code());
        assertEquals("TSJ37B-DATA-JDBC", exception.featureId());
    }

    @Test
    void failsWithTransactionDiagnosticWhenTransactionalServiceMethodIsMissing() throws Exception {
        final Path entryFile = writeFixture(
                """
                @Repository
                class OrderRepository {
                  countByStatus(status: string) {
                    return status == "OPEN" ? 2 : 0;
                  }
                }

                @Service
                class OrderService {
                  reportOpenCount() {
                    return new OrderRepository().countByStatus("OPEN");
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjDataJdbcSubsetEvaluator().analyze(model, entryFile)
        );

        assertEquals("TSJ-SPRING-DATA-TRANSACTION", exception.code());
        assertEquals("TSJ37B-DATA-JDBC", exception.featureId());
    }

    @Test
    void failsWithQueryDiagnosticWhenRepositoryHasNoSupportedQueryMethodNames() throws Exception {
        final Path entryFile = writeFixture(
                """
                @Repository
                class OrderRepository {
                  listAll() {
                    return 3;
                  }
                }

                @Service
                class OrderService {
                  @Transactional
                  reportOpenCount() {
                    return new OrderRepository().listAll();
                  }
                }
                """
        );

        final TsDecoratorModel model = new TsDecoratorModelExtractor().extract(entryFile);
        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsjDataJdbcSubsetEvaluator().analyze(model, entryFile)
        );

        assertEquals("TSJ-SPRING-DATA-QUERY", exception.code());
        assertEquals("TSJ37B-DATA-JDBC", exception.featureId());
    }

    private Path writeFixture(final String source) throws Exception {
        final Path entryFile = tempDir.resolve("data-jdbc.ts");
        Files.writeString(entryFile, source, UTF_8);
        return entryFile;
    }
}
