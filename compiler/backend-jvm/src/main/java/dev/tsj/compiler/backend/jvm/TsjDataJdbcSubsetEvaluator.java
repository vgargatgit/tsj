package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates TSJ-37b Spring Data JDBC baseline subset from extracted decorators.
 */
public final class TsjDataJdbcSubsetEvaluator {
    public static final String FEATURE_ID = "TSJ37B-DATA-JDBC";
    public static final String WIRING_CODE = "TSJ-SPRING-DATA-WIRING";
    public static final String TRANSACTION_CODE = "TSJ-SPRING-DATA-TRANSACTION";
    public static final String QUERY_CODE = "TSJ-SPRING-DATA-QUERY";
    private static final String GUIDANCE =
            "Use TSJ-37b subset: @Repository query-method naming (countBy*/findBy*/existsBy*) "
                    + "plus @Service methods annotated with @Transactional.";
    private static final Set<String> QUERY_PREFIXES = Set.of("countBy", "findBy", "existsBy");

    public DataJdbcSubset analyze(final TsDecoratorModel model, final Path sourceFile) {
        Objects.requireNonNull(model, "model");
        final Path normalizedSource = Objects.requireNonNull(sourceFile, "sourceFile")
                .toAbsolutePath()
                .normalize();

        final TsDecoratedClass repositoryClass = model.classes().stream()
                .filter(candidate -> hasDecorator(candidate.decorators(), "Repository"))
                .findFirst()
                .orElseThrow(() -> diagnostic(
                        WIRING_CODE,
                        "TSJ-37b subset requires at least one @Repository class.",
                        normalizedSource
                ));

        final List<RepositoryQueryMethod> repositoryQueries = collectRepositoryQueries(repositoryClass, normalizedSource);
        if (repositoryQueries.isEmpty()) {
            throw diagnostic(
                    QUERY_CODE,
                    "Repository `" + repositoryClass.className()
                            + "` must expose query methods using countBy*/findBy*/existsBy* naming in TSJ-37b subset.",
                    normalizedSource
            );
        }

        final TsDecoratedClass serviceClass = model.classes().stream()
                .filter(candidate -> hasDecorator(candidate.decorators(), "Service")
                        || hasDecorator(candidate.decorators(), "Component"))
                .findFirst()
                .orElseThrow(() -> diagnostic(
                        WIRING_CODE,
                        "TSJ-37b subset requires at least one @Service/@Component class for repository wiring.",
                        normalizedSource
                ));

        final boolean classTransactional = hasDecorator(serviceClass.decorators(), "Transactional");
        final List<String> transactionalMethods = new ArrayList<>();
        for (TsDecoratedMethod method : serviceClass.methods()) {
            if (method.constructor()) {
                continue;
            }
            if (classTransactional || hasDecorator(method.decorators(), "Transactional")) {
                transactionalMethods.add(method.methodName());
            }
        }
        if (transactionalMethods.isEmpty()) {
            throw diagnostic(
                    TRANSACTION_CODE,
                    "Service `" + serviceClass.className()
                            + "` must expose at least one @Transactional method in TSJ-37b subset.",
                    normalizedSource
            );
        }

        return new DataJdbcSubset(
                repositoryClass.className(),
                serviceClass.className(),
                List.copyOf(repositoryQueries),
                List.copyOf(transactionalMethods)
        );
    }

    private static List<RepositoryQueryMethod> collectRepositoryQueries(
            final TsDecoratedClass repositoryClass,
            final Path sourceFile
    ) {
        final List<RepositoryQueryMethod> queries = new ArrayList<>();
        for (TsDecoratedMethod method : repositoryClass.methods()) {
            if (method.constructor()) {
                continue;
            }
            final String methodName = method.methodName();
            final String matchingPrefix = QUERY_PREFIXES.stream()
                    .filter(methodName::startsWith)
                    .findFirst()
                    .orElse(null);
            if (matchingPrefix == null) {
                continue;
            }
            if (method.parameters().size() != 1) {
                throw diagnostic(
                        QUERY_CODE,
                        "Repository query method `" + repositoryClass.className() + "." + methodName
                                + "` must declare exactly one parameter in TSJ-37b subset.",
                        sourceFile
                );
            }
            queries.add(new RepositoryQueryMethod(
                    repositoryClass.className(),
                    methodName,
                    matchingPrefix,
                    method.parameters().size()
            ));
        }
        return List.copyOf(queries);
    }

    private static boolean hasDecorator(final List<TsDecoratorUse> decorators, final String decoratorName) {
        for (TsDecoratorUse decorator : decorators) {
            if (decoratorName.equals(decorator.name())) {
                return true;
            }
        }
        return false;
    }

    private static JvmCompilationException diagnostic(
            final String code,
            final String message,
            final Path sourceFile
    ) {
        return new JvmCompilationException(
                code,
                message,
                null,
                null,
                sourceFile.toString(),
                FEATURE_ID,
                GUIDANCE
        );
    }

    public record DataJdbcSubset(
            String repositoryClassName,
            String serviceClassName,
            List<RepositoryQueryMethod> repositoryQueries,
            List<String> transactionalServiceMethods
    ) {
        public DataJdbcSubset {
            repositoryClassName = Objects.requireNonNull(repositoryClassName, "repositoryClassName");
            serviceClassName = Objects.requireNonNull(serviceClassName, "serviceClassName");
            repositoryQueries = List.copyOf(Objects.requireNonNull(repositoryQueries, "repositoryQueries"));
            transactionalServiceMethods = List.copyOf(
                    Objects.requireNonNull(transactionalServiceMethods, "transactionalServiceMethods")
            );
        }
    }

    public record RepositoryQueryMethod(
            String className,
            String methodName,
            String queryPrefix,
            int parameterCount
    ) {
        public RepositoryQueryMethod {
            className = Objects.requireNonNull(className, "className");
            methodName = Objects.requireNonNull(methodName, "methodName");
            queryPrefix = Objects.requireNonNull(queryPrefix, "queryPrefix");
            if (parameterCount < 0) {
                throw new IllegalArgumentException("parameterCount must be >= 0");
            }
        }
    }
}
