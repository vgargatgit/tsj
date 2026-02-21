package dev.tsj.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * TSJ-44b version-range compatibility certification report.
 */
record TsjVersionRangeCertificationReport(
        boolean gatePassed,
        boolean driftDetected,
        List<LibraryRangeResult> libraries,
        Path reportPath,
        Path moduleReportPath
) {
    TsjVersionRangeCertificationReport {
        libraries = List.copyOf(Objects.requireNonNull(libraries, "libraries"));
        reportPath = Objects.requireNonNull(reportPath, "reportPath");
        moduleReportPath = Objects.requireNonNull(moduleReportPath, "moduleReportPath");
    }

    String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\"suite\":\"TSJ-44b-version-range-certification\",");
        builder.append("\"gatePassed\":").append(gatePassed).append(",");
        builder.append("\"driftDetected\":").append(driftDetected).append(",");
        builder.append("\"libraries\":[");
        for (int libraryIndex = 0; libraryIndex < libraries.size(); libraryIndex++) {
            if (libraryIndex > 0) {
                builder.append(",");
            }
            final LibraryRangeResult library = libraries.get(libraryIndex);
            builder.append("{\"category\":\"")
                    .append(escapeJson(library.category()))
                    .append("\",\"library\":\"")
                    .append(escapeJson(library.library()))
                    .append("\",\"certifiedRange\":\"")
                    .append(escapeJson(library.certifiedRange()))
                    .append("\",\"passed\":")
                    .append(library.passed())
                    .append(",\"firstFailingVersion\":\"")
                    .append(escapeJson(library.firstFailingVersion()))
                    .append("\",\"versions\":[");
            for (int versionIndex = 0; versionIndex < library.versions().size(); versionIndex++) {
                if (versionIndex > 0) {
                    builder.append(",");
                }
                final VersionCheckResult version = library.versions().get(versionIndex);
                builder.append("{\"version\":\"")
                        .append(escapeJson(version.version()))
                        .append("\",\"passed\":")
                        .append(version.passed())
                        .append(",\"durationMs\":")
                        .append(version.durationMs())
                        .append(",\"diagnosticCode\":\"")
                        .append(escapeJson(version.diagnosticCode()))
                        .append("\",\"notes\":\"")
                        .append(escapeJson(version.notes()))
                        .append("\"}");
            }
            builder.append("]}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    record LibraryRangeResult(
            String category,
            String library,
            String certifiedRange,
            boolean passed,
            String firstFailingVersion,
            List<VersionCheckResult> versions
    ) {
        LibraryRangeResult {
            category = Objects.requireNonNull(category, "category");
            library = Objects.requireNonNull(library, "library");
            certifiedRange = Objects.requireNonNull(certifiedRange, "certifiedRange");
            firstFailingVersion = Objects.requireNonNull(firstFailingVersion, "firstFailingVersion");
            versions = List.copyOf(Objects.requireNonNull(versions, "versions"));
        }
    }

    record VersionCheckResult(
            String version,
            boolean passed,
            long durationMs,
            String diagnosticCode,
            String notes
    ) {
        VersionCheckResult {
            version = Objects.requireNonNull(version, "version");
            diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            notes = Objects.requireNonNull(notes, "notes");
        }
    }
}
