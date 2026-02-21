package dev.tsj.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TSJ-43d guardrail parity certification harness.
 */
final class TsjGuardrailCertificationHarness {
    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"");
    private static final String REPORT_FILE = "tsj43d-guardrail-certification.json";

    TsjGuardrailCertificationReport run(final Path reportPath) throws Exception {
        final Path normalizedReport = reportPath.toAbsolutePath().normalize();
        final Path workRoot = normalizedReport.getParent().resolve("tsj43d-certification-work");
        Files.createDirectories(workRoot);

        final List<TsjGuardrailCertificationReport.ScenarioResult> scenarios = List.of(
                runFleetPolicyProjectSuccess(workRoot.resolve("fleet-policy-success")),
                runFleetPolicyConflictDiagnostic(workRoot.resolve("fleet-policy-conflict")),
                runAggregateAuditSuccess(workRoot.resolve("audit-aggregate-success")),
                runAggregateFallbackDiagnostic(workRoot.resolve("audit-aggregate-fallback")),
                runRbacRequiredRoleDiagnostic(workRoot.resolve("rbac-required-role")),
                runApprovalRequiredDiagnostic(workRoot.resolve("approval-required")),
                runApprovalSuccess(workRoot.resolve("approval-success"))
        );

        final List<TsjGuardrailCertificationReport.FamilySummary> families = summarizeFamilies(scenarios);
        final boolean gatePassed = families.stream().allMatch(TsjGuardrailCertificationReport.FamilySummary::passed);

        final TsjGuardrailCertificationReport report = new TsjGuardrailCertificationReport(
                gatePassed,
                families,
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

    private static List<TsjGuardrailCertificationReport.FamilySummary> summarizeFamilies(
            final List<TsjGuardrailCertificationReport.ScenarioResult> scenarios
    ) {
        final Map<String, List<TsjGuardrailCertificationReport.ScenarioResult>> byFamily = new LinkedHashMap<>();
        byFamily.put("fleet-policy", new ArrayList<>());
        byFamily.put("centralized-audit", new ArrayList<>());
        byFamily.put("rbac-approval", new ArrayList<>());

        for (TsjGuardrailCertificationReport.ScenarioResult scenario : scenarios) {
            byFamily.computeIfAbsent(scenario.family(), ignored -> new ArrayList<>()).add(scenario);
        }

        final List<TsjGuardrailCertificationReport.FamilySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<TsjGuardrailCertificationReport.ScenarioResult>> entry : byFamily.entrySet()) {
            final int scenarioCount = entry.getValue().size();
            final int passedCount = (int) entry.getValue().stream()
                    .filter(TsjGuardrailCertificationReport.ScenarioResult::passed)
                    .count();
            summaries.add(new TsjGuardrailCertificationReport.FamilySummary(
                    entry.getKey(),
                    scenarioCount > 0 && scenarioCount == passedCount,
                    scenarioCount,
                    passedCount
            ));
        }
        return List.copyOf(summaries);
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runFleetPolicyProjectSuccess(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("fleet-project-policy.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { max } from "java:java.lang.Math";
                    console.log("fleet=" + max(3, 7));
                    """,
                    UTF_8
            );
            writeProjectPolicy(
                    workDir,
                    """
                    interop.policy=broad
                    interop.ackRisk=true
                    """
            );
            final Path missingGlobal = workDir.resolve("missing-global-policy.properties");

            final CommandResult command = withSystemProperty(
                    "tsj.interop.globalPolicy",
                    missingGlobal.toString(),
                    () -> executeCli(
                            "run",
                            entryFile.toString(),
                            "--out",
                            workDir.resolve("out").toString()
                    )
            );
            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("fleet=7")
                    && command.stdout().contains("\"code\":\"TSJ-RUN-SUCCESS\"")
                    && command.stderr().isBlank();
            return scenarioResult(
                    "fleet-policy",
                    "fleet-policy-project-success",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure("fleet-policy", "fleet-policy-project-success", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runFleetPolicyConflictDiagnostic(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("fleet-policy-conflict.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { max } from "java:java.lang.Math";
                    console.log(max(1, 2));
                    """,
                    UTF_8
            );
            writeProjectPolicy(
                    workDir,
                    """
                    interop.policy=broad
                    interop.ackRisk=true
                    """
            );
            final Path globalPolicy = workDir.resolve("global-policy.properties");
            Files.writeString(globalPolicy, "interop.policy=strict\n", UTF_8);

            final CommandResult command = withSystemProperty(
                    "tsj.interop.globalPolicy",
                    globalPolicy.toString(),
                    () -> executeCli(
                            "run",
                            entryFile.toString(),
                            "--out",
                            workDir.resolve("out").toString()
                    )
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-INTEROP-POLICY-CONFLICT".equals(diagnosticCode)
                    && command.stderr().contains("Resolve")
                    && command.stderr().contains("--interop-policy")
                    && command.stdout().isBlank();
            return scenarioResult(
                    "fleet-policy",
                    "fleet-policy-conflict-diagnostic",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure("fleet-policy", "fleet-policy-conflict-diagnostic", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runAggregateAuditSuccess(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("audit-aggregate-interop.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { max } from "java:java.lang.Math";
                    console.log("max=" + max(1, 9));
                    """,
                    UTF_8
            );
            final Path auditLog = workDir.resolve("audit/interop-local.jsonl");
            final Path aggregateLog = workDir.resolve("audit/interop-aggregate.jsonl");

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk",
                    "--interop-audit-log",
                    auditLog.toString(),
                    "--interop-audit-aggregate",
                    aggregateLog.toString()
            );
            final String aggregateText = Files.exists(aggregateLog) ? Files.readString(aggregateLog, UTF_8) : "";
            final boolean passed = command.exitCode() == 0
                    && Files.exists(auditLog)
                    && Files.exists(aggregateLog)
                    && aggregateText.contains("\"schema\":\"tsj.interop.audit.v1\"")
                    && aggregateText.contains("\"command\":\"run\"")
                    && aggregateText.contains("\"decision\":\"allow\"")
                    && aggregateText.contains("\"outcome\":\"success\"")
                    && aggregateText.contains("\"target\":\"java.lang.Math#max\"")
                    && command.stderr().isBlank();
            return scenarioResult(
                    "centralized-audit",
                    "aggregate-success",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure("centralized-audit", "aggregate-success", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runAggregateFallbackDiagnostic(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("audit-aggregate-fallback.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { max } from "java:java.lang.Math";
                    console.log("max=" + max(5, 8));
                    """,
                    UTF_8
            );
            final Path auditLog = workDir.resolve("audit/interop-fallback.jsonl");
            final Path aggregateSink = workDir.resolve("audit/aggregate-as-directory");
            Files.createDirectories(aggregateSink);

            final CommandResult command = executeCli(
                    "run",
                    entryFile.toString(),
                    "--out",
                    workDir.resolve("out").toString(),
                    "--interop-policy",
                    "broad",
                    "--ack-interop-risk",
                    "--interop-audit-log",
                    auditLog.toString(),
                    "--interop-audit-aggregate",
                    aggregateSink.toString()
            );
            final String auditText = Files.exists(auditLog) ? Files.readString(auditLog, UTF_8) : "";
            final boolean passed = command.exitCode() == 0
                    && Files.exists(auditLog)
                    && auditText.contains("\"decision\":\"allow\"")
                    && auditText.contains("java.lang.Math#max")
                    && auditText.contains("\"code\":\"TSJ-INTEROP-AUDIT-AGGREGATE\"")
                    && command.stderr().isBlank();
            final String diagnosticCode = auditText.contains("TSJ-INTEROP-AUDIT-AGGREGATE")
                    ? "TSJ-INTEROP-AUDIT-AGGREGATE"
                    : "";
            return scenarioResult(
                    "centralized-audit",
                    "aggregate-fallback-diagnostic",
                    passed,
                    diagnosticCode,
                    buildNotes(command) + " audit=" + auditText
            );
        } catch (final Exception exception) {
            return scenarioFailure("centralized-audit", "aggregate-fallback-diagnostic", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runRbacRequiredRoleDiagnostic(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj43c-rbac-deny.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { max } from "java:java.lang.Math";
                    console.log("rbac=" + max(2, 3));
                    """,
                    UTF_8
            );
            writeProjectPolicy(
                    workDir,
                    """
                    interop.policy=broad
                    interop.ackRisk=true
                    interop.rbac.requiredRoles=interop.operator
                    """
            );
            final Path missingGlobal = workDir.resolve("missing-global-policy.properties");

            final CommandResult command = withSystemProperty(
                    "tsj.interop.globalPolicy",
                    missingGlobal.toString(),
                    () -> executeCli(
                            "run",
                            entryFile.toString(),
                            "--out",
                            workDir.resolve("out").toString()
                    )
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-INTEROP-RBAC".equals(diagnosticCode)
                    && command.stderr().contains("interop.operator")
                    && command.stderr().contains("\"scope\":\"general\"")
                    && command.stdout().isBlank();
            return scenarioResult(
                    "rbac-approval",
                    "rbac-required-role-diagnostic",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure("rbac-approval", "rbac-required-role-diagnostic", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runApprovalRequiredDiagnostic(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj43c-approval-deny.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { getProperty } from "java:java.lang.System";
                    console.log("java=" + getProperty("java.version"));
                    """,
                    UTF_8
            );
            writeProjectPolicy(
                    workDir,
                    """
                    interop.policy=broad
                    interop.ackRisk=true
                    interop.rbac.requiredRoles=interop.operator
                    interop.rbac.sensitiveTargets=java.lang.System
                    interop.rbac.sensitiveRequiredRoles=interop.admin
                    interop.approval.required=true
                    interop.approval.targets=java.lang.System
                    interop.approval.token=ticket-123
                    """
            );
            final Path missingGlobal = workDir.resolve("missing-global-policy.properties");

            final CommandResult command = withSystemProperty(
                    "tsj.interop.globalPolicy",
                    missingGlobal.toString(),
                    () -> executeCli(
                            "run",
                            entryFile.toString(),
                            "--out",
                            workDir.resolve("out").toString(),
                            "--interop-role",
                            "interop.admin"
                    )
            );
            final String diagnosticCode = extractDiagnosticCode(command.stderr());
            final boolean passed = command.exitCode() == 1
                    && "TSJ-INTEROP-APPROVAL".equals(diagnosticCode)
                    && command.stderr().contains("java.lang.System")
                    && command.stderr().contains("ticket-123")
                    && command.stdout().isBlank();
            return scenarioResult(
                    "rbac-approval",
                    "approval-required-diagnostic",
                    passed,
                    diagnosticCode,
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure("rbac-approval", "approval-required-diagnostic", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult runApprovalSuccess(final Path workDir) {
        try {
            Files.createDirectories(workDir);
            final Path entryFile = workDir.resolve("tsj43c-approval-allow.ts");
            Files.writeString(
                    entryFile,
                    """
                    import { getProperty } from "java:java.lang.System";
                    console.log("java=" + getProperty("java.version"));
                    """,
                    UTF_8
            );
            writeProjectPolicy(
                    workDir,
                    """
                    interop.policy=broad
                    interop.ackRisk=true
                    interop.rbac.requiredRoles=interop.operator
                    interop.rbac.sensitiveTargets=java.lang.System
                    interop.rbac.sensitiveRequiredRoles=interop.admin
                    interop.approval.required=true
                    interop.approval.targets=java.lang.System
                    interop.approval.token=ticket-123
                    """
            );
            final Path missingGlobal = workDir.resolve("missing-global-policy.properties");

            final CommandResult command = withSystemProperty(
                    "tsj.interop.globalPolicy",
                    missingGlobal.toString(),
                    () -> executeCli(
                            "run",
                            entryFile.toString(),
                            "--out",
                            workDir.resolve("out").toString(),
                            "--interop-role",
                            "interop.admin",
                            "--interop-approval",
                            "ticket-123"
                    )
            );
            final boolean passed = command.exitCode() == 0
                    && command.stdout().contains("java=")
                    && command.stdout().contains("\"code\":\"TSJ-RUN-SUCCESS\"")
                    && command.stderr().isBlank();
            return scenarioResult(
                    "rbac-approval",
                    "approval-success",
                    passed,
                    extractDiagnosticCode(command.stderr()),
                    buildNotes(command)
            );
        } catch (final Exception exception) {
            return scenarioFailure("rbac-approval", "approval-success", exception);
        }
    }

    private static TsjGuardrailCertificationReport.ScenarioResult scenarioResult(
            final String family,
            final String scenario,
            final boolean passed,
            final String diagnosticCode,
            final String notes
    ) {
        return new TsjGuardrailCertificationReport.ScenarioResult(
                family,
                scenario,
                passed,
                diagnosticCode,
                notes
        );
    }

    private static TsjGuardrailCertificationReport.ScenarioResult scenarioFailure(
            final String family,
            final String scenario,
            final Exception exception
    ) {
        final String notes = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return scenarioResult(family, scenario, false, "", notes);
    }

    private static void writeProjectPolicy(final Path workDir, final String content) throws IOException {
        final Path policy = workDir.resolve(".tsj/interop-policy.properties");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, content, UTF_8);
    }

    private static String buildNotes(final CommandResult command) {
        final String stderr = command.stderr().isBlank() ? "" : " stderr=" + command.stderr().trim();
        return "exit=" + command.exitCode() + stderr;
    }

    private static CommandResult executeCli(final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = TsjCli.execute(args, new PrintStream(stdout), new PrintStream(stderr));
        return new CommandResult(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8));
    }

    private static String extractDiagnosticCode(final String stderrText) {
        final Matcher matcher = DIAGNOSTIC_CODE_PATTERN.matcher(stderrText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static CommandResult withSystemProperty(
            final String key,
            final String value,
            final ThrowingSupplier<CommandResult> supplier
    ) throws Exception {
        final String previous = System.getProperty(key);
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static Path resolveModuleReportPath() {
        try {
            final Path testClassesDir = Path.of(
                    TsjGuardrailCertificationHarness.class
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
            final TsjGuardrailCertificationReport report
    ) throws IOException {
        final Path normalizedReportPath = reportPath.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalizedReportPath.getParent(), "Report path parent is required.");
        Files.createDirectories(parent);
        Files.writeString(normalizedReportPath, report.toJson() + "\n", UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
