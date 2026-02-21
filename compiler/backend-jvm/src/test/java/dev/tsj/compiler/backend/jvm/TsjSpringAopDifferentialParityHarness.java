package dev.tsj.compiler.backend.jvm;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-35c differential parity harness for transactional/AOP subset behavior.
 */
final class TsjSpringAopDifferentialParityHarness {
    private static final String REPORT_FILE = "tsj35c-aop-differential-parity-report.json";
    private static final String DIFF_DIAGNOSTIC_CODE = "TSJ35C-DIFF-MISMATCH";
    private static final String ERROR_DIAGNOSTIC_CODE = "TSJ35C-SCENARIO-ERROR";

    TsjSpringAopDifferentialParityReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj35c-differential-work");
        Files.createDirectories(workRoot);

        final TsjSpringAopRuntimeConformanceReport tsRuntimeReport =
                new TsjSpringAopRuntimeConformanceHarness().run(
                        workRoot.resolve("tsj35b-runtime-conformance.json")
                );
        final Map<String, TsjSpringAopRuntimeConformanceReport.ScenarioResult> tsByScenario = new LinkedHashMap<>();
        for (TsjSpringAopRuntimeConformanceReport.ScenarioResult scenario : tsRuntimeReport.scenarios()) {
            tsByScenario.put(scenario.scenario(), scenario);
        }

        final List<TsjSpringAopDifferentialParityReport.ScenarioResult> scenarios = List.of(
                compareScenario(
                        "commit-chain",
                        tsByScenario,
                        this::runJavaCommitChainReference,
                        this::runKotlinCommitChainReference
                ),
                compareScenario(
                        "rollback-chain",
                        tsByScenario,
                        this::runJavaRollbackChainReference,
                        this::runKotlinRollbackChainReference
                ),
                compareScenario(
                        "missing-transaction-manager",
                        tsByScenario,
                        this::runJavaMissingTxManagerReference,
                        this::runKotlinMissingTxManagerReference
                ),
                compareScenario(
                        "application-invocation-failure",
                        tsByScenario,
                        this::runJavaApplicationFailureReference,
                        this::runKotlinApplicationFailureReference
                )
        );

        final TsjSpringAopDifferentialParityReport report = new TsjSpringAopDifferentialParityReport(
                scenarios,
                normalizedReport,
                resolveModuleReportPath()
        );
        writeReport(report.reportPath(), report);
        if (!report.moduleReportPath().equals(report.reportPath())) {
            writeReport(report.moduleReportPath(), report);
        }
        return report;
    }

    private TsjSpringAopDifferentialParityReport.ScenarioResult compareScenario(
            final String scenarioName,
            final Map<String, TsjSpringAopRuntimeConformanceReport.ScenarioResult> tsByScenario,
            final ScenarioSupplier javaSupplier,
            final ScenarioSupplier kotlinSupplier
    ) {
        final TsjSpringAopRuntimeConformanceReport.ScenarioResult tsScenario = tsByScenario.get(scenarioName);
        if (tsScenario == null) {
            return scenarioError(
                    scenarioName,
                    "spring-matrix/unknown",
                    new IllegalStateException("TSJ-35b report missing scenario: " + scenarioName)
            );
        }
        final String fixture = tsScenario.fixture();
        try {
            final ScenarioSnapshot tsSnapshot = ScenarioSnapshot.fromTs(tsScenario);
            final ScenarioSnapshot javaSnapshot = javaSupplier.run();
            final ScenarioSnapshot kotlinSnapshot = kotlinSupplier.run();
            final boolean passed = tsSnapshot.expectationMet()
                    && javaSnapshot.expectationMet()
                    && kotlinSnapshot.expectationMet()
                    && tsSnapshot.equivalentTo(javaSnapshot)
                    && tsSnapshot.equivalentTo(kotlinSnapshot);
            final String diagnosticCode = passed
                    ? tsSnapshot.diagnosticCode()
                    : DIFF_DIAGNOSTIC_CODE;
            final String notes = passed
                    ? "TS transactional/AOP scenario matches Java and Kotlin-reference behavior."
                    : "TS transactional/AOP scenario diverged from Java/Kotlin-reference behavior.";
            return new TsjSpringAopDifferentialParityReport.ScenarioResult(
                    scenarioName,
                    fixture,
                    passed,
                    diagnosticCode,
                    tsSnapshot.render(),
                    javaSnapshot.render(),
                    kotlinSnapshot.render(),
                    notes
            );
        } catch (final Exception exception) {
            return scenarioError(scenarioName, fixture, exception);
        }
    }

    private static TsjSpringAopDifferentialParityReport.ScenarioResult scenarioError(
            final String scenarioName,
            final String fixture,
            final Exception exception
    ) {
        final String rendered = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return new TsjSpringAopDifferentialParityReport.ScenarioResult(
                scenarioName,
                fixture,
                false,
                ERROR_DIAGNOSTIC_CODE,
                rendered,
                rendered,
                rendered,
                rendered
        );
    }

    private ScenarioSnapshot runJavaCommitChainReference() {
        return runCommitChainReference(JavaCommitOrderService.class, JavaCommitLedgerService.class);
    }

    private ScenarioSnapshot runKotlinCommitChainReference() {
        return runCommitChainReference(KotlinCommitOrderService.class, KotlinCommitLedgerService.class);
    }

    private static ScenarioSnapshot runCommitChainReference(
            final Class<? extends ReferenceOrderApi> orderServiceClass,
            final Class<?> ledgerServiceClass
    ) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ParityTransactionManagerConfig.class);
            context.register(ledgerServiceClass);
            context.register(orderServiceClass);
            context.refresh();

            final Object result;
            ReferenceContextHolder.set(context);
            try {
                result = context.getBean(ReferenceOrderApi.class).place(7);
            } finally {
                ReferenceContextHolder.clear();
            }
            final RecordingTransactionManager transactionManager =
                    (RecordingTransactionManager) context.getBean(PlatformTransactionManager.class);
            final boolean expectationMet = "ledger:7".equals(String.valueOf(result))
                    && transactionManager.beginCount() == 2
                    && transactionManager.commitCount() == 2
                    && transactionManager.rollbackCount() == 0;
            return new ScenarioSnapshot(
                    "",
                    transactionManager.beginCount(),
                    transactionManager.commitCount(),
                    transactionManager.rollbackCount(),
                    expectationMet
            );
        }
    }

    private ScenarioSnapshot runJavaRollbackChainReference() {
        return runRollbackChainReference(JavaRollbackOrderService.class, JavaRollbackLedgerService.class);
    }

    private ScenarioSnapshot runKotlinRollbackChainReference() {
        return runRollbackChainReference(KotlinRollbackOrderService.class, KotlinRollbackLedgerService.class);
    }

    private static ScenarioSnapshot runRollbackChainReference(
            final Class<? extends ReferenceOrderApi> orderServiceClass,
            final Class<?> ledgerServiceClass
    ) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ParityTransactionManagerConfig.class);
            context.register(ledgerServiceClass);
            context.register(orderServiceClass);
            context.refresh();

            final Throwable failure;
            ReferenceContextHolder.set(context);
            try {
                failure = captureFailure(context.getBean(ReferenceOrderApi.class), 9);
            } finally {
                ReferenceContextHolder.clear();
            }
            final RecordingTransactionManager transactionManager =
                    (RecordingTransactionManager) context.getBean(PlatformTransactionManager.class);
            final boolean expectationMet = failure != null
                    && String.valueOf(failure).contains("chain-failure")
                    && transactionManager.beginCount() == 2
                    && transactionManager.commitCount() == 0
                    && transactionManager.rollbackCount() == 2;
            return new ScenarioSnapshot(
                    "",
                    transactionManager.beginCount(),
                    transactionManager.commitCount(),
                    transactionManager.rollbackCount(),
                    expectationMet
            );
        }
    }

    private ScenarioSnapshot runJavaMissingTxManagerReference() {
        return runMissingTxManagerReference(JavaCommitOrderService.class, JavaCommitLedgerService.class);
    }

    private ScenarioSnapshot runKotlinMissingTxManagerReference() {
        return runMissingTxManagerReference(KotlinCommitOrderService.class, KotlinCommitLedgerService.class);
    }

    private static ScenarioSnapshot runMissingTxManagerReference(
            final Class<? extends ReferenceOrderApi> orderServiceClass,
            final Class<?> ledgerServiceClass
    ) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ledgerServiceClass);
            context.register(orderServiceClass);
            try {
                context.refresh();
                return new ScenarioSnapshot("", 0, 0, 0, false);
            } catch (final IllegalStateException illegalStateException) {
                final boolean expectationMet = String.valueOf(illegalStateException.getMessage())
                        .contains("TSJ-SPRING-AOP");
                return new ScenarioSnapshot(
                        expectationMet ? "TSJ-SPRING-AOP" : "",
                        0,
                        0,
                        0,
                        expectationMet
                );
            }
        }
    }

    private ScenarioSnapshot runJavaApplicationFailureReference() {
        return runApplicationFailureReference(JavaRollbackOrderService.class, JavaRollbackLedgerService.class);
    }

    private ScenarioSnapshot runKotlinApplicationFailureReference() {
        return runApplicationFailureReference(KotlinRollbackOrderService.class, KotlinRollbackLedgerService.class);
    }

    private static ScenarioSnapshot runApplicationFailureReference(
            final Class<? extends ReferenceOrderApi> orderServiceClass,
            final Class<?> ledgerServiceClass
    ) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ParityTransactionManagerConfig.class);
            context.register(ledgerServiceClass);
            context.register(orderServiceClass);
            context.refresh();

            final Throwable failure;
            ReferenceContextHolder.set(context);
            try {
                failure = captureFailure(context.getBean(ReferenceOrderApi.class), 5);
            } finally {
                ReferenceContextHolder.clear();
            }
            final RecordingTransactionManager transactionManager =
                    (RecordingTransactionManager) context.getBean(PlatformTransactionManager.class);
            final boolean expectationMet = failure != null
                    && String.valueOf(failure).contains("chain-failure")
                    && !String.valueOf(failure).contains("TSJ-SPRING-AOP")
                    && transactionManager.rollbackCount() > 0;
            return new ScenarioSnapshot(
                    "",
                    transactionManager.beginCount(),
                    transactionManager.commitCount(),
                    transactionManager.rollbackCount(),
                    expectationMet
            );
        }
    }

    private static Throwable captureFailure(final ReferenceOrderApi orderApi, final Object amount) {
        try {
            orderApi.place(amount);
            return null;
        } catch (final Throwable throwable) {
            return throwable;
        }
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjSpringAopDifferentialParityHarness.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            final Path targetDir = testClassesDir.getParent();
            if (targetDir != null) {
                return targetDir.resolve(REPORT_FILE);
            }
        } catch (final Exception ignored) {
            // Fall through to relative fallback.
        }
        return Path.of("target", REPORT_FILE).toAbsolutePath().normalize();
    }

    private static void writeReport(
            final Path reportPath,
            final TsjSpringAopDifferentialParityReport report
    ) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toJson() + "\n", UTF_8);
    }

    private interface ScenarioSupplier {
        ScenarioSnapshot run() throws Exception;
    }

    private record ScenarioSnapshot(
            String diagnosticCode,
            int beginCount,
            int commitCount,
            int rollbackCount,
            boolean expectationMet
    ) {
        private static ScenarioSnapshot fromTs(final TsjSpringAopRuntimeConformanceReport.ScenarioResult scenario) {
            return new ScenarioSnapshot(
                    scenario.diagnosticCode(),
                    scenario.beginCount(),
                    scenario.commitCount(),
                    scenario.rollbackCount(),
                    scenario.passed()
            );
        }

        private boolean equivalentTo(final ScenarioSnapshot other) {
            return diagnosticCode.equals(other.diagnosticCode)
                    && beginCount == other.beginCount
                    && commitCount == other.commitCount
                    && rollbackCount == other.rollbackCount;
        }

        private String render() {
            return "diag=" + diagnosticCode
                    + ",begin=" + beginCount
                    + ",commit=" + commitCount
                    + ",rollback=" + rollbackCount
                    + ",expectationMet=" + expectationMet;
        }
    }

    @Configuration
    public static class ParityTransactionManagerConfig {
        @Bean
        public PlatformTransactionManager platformTransactionManager() {
            return new RecordingTransactionManager();
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        private int beginCount;
        private int commitCount;
        private int rollbackCount;

        @Override
        public TransactionStatus begin(final String beanName, final String methodName) {
            beginCount++;
            return new TransactionStatus(beanName, methodName);
        }

        @Override
        public void commit(final TransactionStatus status) {
            commitCount++;
        }

        @Override
        public void rollback(final TransactionStatus status) {
            rollbackCount++;
        }

        private int beginCount() {
            return beginCount;
        }

        private int commitCount() {
            return commitCount;
        }

        private int rollbackCount() {
            return rollbackCount;
        }
    }

    public interface ReferenceOrderApi {
        Object place(Object amount);
    }

    public interface ReferenceCommitLedgerApi {
        Object record(Object amount);
    }

    public interface ReferenceRollbackLedgerApi {
        Object failRecord(Object amount);
    }

    @Service
    public static class JavaCommitLedgerService implements ReferenceCommitLedgerApi {
        @Override
        @Transactional
        public Object record(final Object amount) {
            return "ledger:" + amount;
        }
    }

    @Service
    public static class JavaCommitOrderService implements ReferenceOrderApi {
        @Override
        @Transactional
        public Object place(final Object amount) {
            return ReferenceContextHolder.require().getBean(ReferenceCommitLedgerApi.class).record(amount);
        }
    }

    @Service
    public static class JavaRollbackLedgerService implements ReferenceRollbackLedgerApi {
        @Override
        @Transactional
        public Object failRecord(final Object amount) {
            throw new IllegalStateException("chain-failure:" + amount);
        }
    }

    @Service
    public static class JavaRollbackOrderService implements ReferenceOrderApi {
        @Override
        @Transactional
        public Object place(final Object amount) {
            return ReferenceContextHolder.require().getBean(ReferenceRollbackLedgerApi.class).failRecord(amount);
        }
    }

    @Service
    public static class KotlinCommitLedgerService implements ReferenceCommitLedgerApi {
        @Override
        @Transactional
        public Object record(final Object amount) {
            return "ledger:" + amount;
        }
    }

    @Service
    public static class KotlinCommitOrderService implements ReferenceOrderApi {
        @Override
        @Transactional
        public Object place(final Object amount) {
            return ReferenceContextHolder.require().getBean(ReferenceCommitLedgerApi.class).record(amount);
        }
    }

    @Service
    public static class KotlinRollbackLedgerService implements ReferenceRollbackLedgerApi {
        @Override
        @Transactional
        public Object failRecord(final Object amount) {
            throw new IllegalStateException("chain-failure:" + amount);
        }
    }

    @Service
    public static class KotlinRollbackOrderService implements ReferenceOrderApi {
        @Override
        @Transactional
        public Object place(final Object amount) {
            return ReferenceContextHolder.require().getBean(ReferenceRollbackLedgerApi.class).failRecord(amount);
        }
    }

    private static final class ReferenceContextHolder {
        private static final ThreadLocal<AnnotationConfigApplicationContext> ACTIVE_CONTEXT = new ThreadLocal<>();

        private static void set(final AnnotationConfigApplicationContext context) {
            ACTIVE_CONTEXT.set(context);
        }

        private static AnnotationConfigApplicationContext require() {
            final AnnotationConfigApplicationContext context = ACTIVE_CONTEXT.get();
            if (context == null) {
                throw new IllegalStateException("Reference context is unavailable for TSJ-35c scenario.");
            }
            return context;
        }

        private static void clear() {
            ACTIVE_CONTEXT.remove();
        }
    }
}
