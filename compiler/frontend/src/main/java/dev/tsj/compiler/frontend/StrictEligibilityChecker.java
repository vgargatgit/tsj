package dev.tsj.compiler.frontend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static strict-mode eligibility checker used by CLI `--mode jvm-strict`.
 *
 * <p>This checker intentionally uses deterministic source-text scans for the
 * current strict subset and traverses the relative import module graph.
 */
public final class StrictEligibilityChecker {
    private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile(
            "(?s)\\bimport\\s+(?:(type\\s+)?(.+?)\\s+from\\s*[\"']([^\"']+)[\"']"
                    + "(?:\\s+(?:with|assert)\\s*\\{[^;]*})?|[\"']([^\"']+)[\"']"
                    + "(?:\\s+(?:with|assert)\\s*\\{[^;]*})?)\\s*;"
    );
    private static final Pattern STRICT_ANY_ANNOTATION_PATTERN = Pattern.compile(
            "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\??\\s*:\\s*any\\b"
    );
    private static final Pattern STRICT_COMPUTED_ASSIGNMENT_PATTERN = Pattern.compile("\\[([^\\]]+)]\\s*=");
    private static final Pattern STRICT_NUMERIC_LITERAL_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private static final List<Rule> REGEX_RULES = List.of(
            regexRule(StrictFeature.DYNAMIC_IMPORT, Pattern.compile("\\bimport\\s*\\(")),
            regexRule(StrictFeature.EVAL, Pattern.compile("\\beval\\s*\\(")),
            regexRule(StrictFeature.FUNCTION_CONSTRUCTOR, Pattern.compile("\\b(?:new\\s+)?Function\\s*\\(")),
            regexRule(StrictFeature.PROXY, Pattern.compile("\\bnew\\s+Proxy\\s*\\(")),
            regexRule(StrictFeature.DELETE, Pattern.compile("\\bdelete\\s+")),
            regexRule(StrictFeature.PROTOTYPE_MUTATION, Pattern.compile("\\.prototype(?:\\s*=|\\s*\\.)")),
            regexRule(
                    StrictFeature.PROTOTYPE_MUTATION,
                    Pattern.compile("\\b(?:Object|Reflect)\\s*\\.\\s*setPrototypeOf\\s*\\(")
            )
    );

    private static final Rule DYNAMIC_PROPERTY_ADD_RULE = featureRule(StrictFeature.DYNAMIC_PROPERTY_ADD);
    private static final Rule UNCHECKED_ANY_MEMBER_RULE = featureRule(StrictFeature.UNCHECKED_ANY_MEMBER_INVOKE);

    public static Set<String> supportedFeatureIds() {
        final Set<String> featureIds = new LinkedHashSet<>();
        for (StrictFeature feature : StrictFeature.values()) {
            featureIds.add(feature.featureId());
        }
        return Set.copyOf(featureIds);
    }

    public StrictEligibilityAnalysis analyze(final Path entryPath) throws IOException {
        final Path normalizedEntry = Objects.requireNonNull(entryPath, "entryPath")
                .toAbsolutePath()
                .normalize();
        final List<SourceUnit> sources = collectSources(normalizedEntry);
        for (SourceUnit source : sources) {
            final Match unsupported = firstUnsupportedMatch(source.sourceText());
            if (unsupported == null) {
                continue;
            }
            final SourcePosition position = toSourcePosition(source.sourceText(), unsupported.offset());
            return new StrictEligibilityAnalysis(
                    source.sourcePath(),
                    new StrictEligibilityViolation(
                            unsupported.rule().feature().featureId(),
                            unsupported.rule().feature().description(),
                            source.sourcePath(),
                            position.line(),
                            position.column(),
                            unsupported.rule().feature().guidance()
                    ),
                    sources.size()
            );
        }
        return new StrictEligibilityAnalysis(normalizedEntry, null, sources.size());
    }

    private List<SourceUnit> collectSources(final Path entryPath) throws IOException {
        final List<SourceUnit> sources = new ArrayList<>();
        collectSourcesRecursive(entryPath, new LinkedHashSet<>(), sources);
        return List.copyOf(sources);
    }

    private void collectSourcesRecursive(
            final Path sourcePath,
            final Set<Path> visited,
            final List<SourceUnit> sources
    ) throws IOException {
        final Path normalizedSource = sourcePath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedSource) || !Files.isRegularFile(normalizedSource)) {
            return;
        }
        if (!visited.add(normalizedSource)) {
            return;
        }
        final String sourceText = Files.readString(normalizedSource, StandardCharsets.UTF_8);
        sources.add(new SourceUnit(normalizedSource, sourceText));
        for (ImportStatement importStatement : parseImportStatements(sourceText)) {
            final String importPath = importStatement.moduleSpecifier();
            if (importStatement.typeOnly() || importPath == null || !importPath.startsWith(".")) {
                continue;
            }
            final Path dependency = resolveRelativeModule(normalizedSource, importPath);
            if (dependency != null) {
                collectSourcesRecursive(dependency, visited, sources);
            }
        }
    }

    private Match firstUnsupportedMatch(final String sourceText) {
        final String scanText = sanitizeSourceForStaticScan(sourceText);
        Match earliest = null;
        for (Rule rule : REGEX_RULES) {
            final java.util.regex.Matcher matcher = rule.pattern().matcher(scanText);
            if (!matcher.find()) {
                continue;
            }
            final Match current = new Match(rule, matcher.start());
            if (earliest == null || current.offset() < earliest.offset()) {
                earliest = current;
            }
        }
        final Match dynamicPropertyWrite = findDynamicPropertyWriteMatch(scanText, sourceText);
        if (dynamicPropertyWrite != null
                && (earliest == null || dynamicPropertyWrite.offset() < earliest.offset())) {
            earliest = dynamicPropertyWrite;
        }
        final Match uncheckedAnyInvocation = findUncheckedAnyMemberInvocationMatch(scanText);
        if (uncheckedAnyInvocation != null
                && (earliest == null || uncheckedAnyInvocation.offset() < earliest.offset())) {
            earliest = uncheckedAnyInvocation;
        }
        return earliest;
    }

    private static Rule regexRule(final StrictFeature feature, final Pattern pattern) {
        return new Rule(feature, pattern);
    }

    private static Rule featureRule(final StrictFeature feature) {
        return new Rule(feature, Pattern.compile("$^"));
    }

    private Match findDynamicPropertyWriteMatch(final String scanText, final String sourceText) {
        final java.util.regex.Matcher matcher = STRICT_COMPUTED_ASSIGNMENT_PATTERN.matcher(scanText);
        while (matcher.find()) {
            final int keyStart = matcher.start(1);
            final int keyEnd = matcher.end(1);
            if (keyStart < 0 || keyEnd < keyStart || keyEnd > sourceText.length()) {
                continue;
            }
            final String keyExpression = sourceText.substring(keyStart, keyEnd);
            if (isLiteralComputedKey(keyExpression)) {
                continue;
            }
            return new Match(DYNAMIC_PROPERTY_ADD_RULE, matcher.start());
        }
        return null;
    }

    private static boolean isLiteralComputedKey(final String keyExpression) {
        if (keyExpression == null) {
            return false;
        }
        final String trimmed = keyExpression.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (STRICT_NUMERIC_LITERAL_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return true;
        }
        return trimmed.startsWith("`") && trimmed.endsWith("`") && !trimmed.contains("${");
    }

    private Match findUncheckedAnyMemberInvocationMatch(final String scanText) {
        final java.util.regex.Matcher declarationMatcher = STRICT_ANY_ANNOTATION_PATTERN.matcher(scanText);
        Match earliest = null;
        while (declarationMatcher.find()) {
            final String bindingName = declarationMatcher.group(1);
            if (bindingName == null || bindingName.isBlank()) {
                continue;
            }
            final Pattern invocationPattern = Pattern.compile(
                    "\\b"
                            + Pattern.quote(bindingName)
                            + "\\s*\\.\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*\\("
            );
            final java.util.regex.Matcher invocationMatcher = invocationPattern.matcher(scanText);
            while (invocationMatcher.find()) {
                if (invocationMatcher.start() <= declarationMatcher.end()) {
                    continue;
                }
                final Match candidate = new Match(UNCHECKED_ANY_MEMBER_RULE, invocationMatcher.start());
                if (earliest == null || candidate.offset() < earliest.offset()) {
                    earliest = candidate;
                }
                break;
            }
        }
        return earliest;
    }

    private static String sanitizeSourceForStaticScan(final String sourceText) {
        final char[] source = sourceText.toCharArray();
        final char[] sanitized = sourceText.toCharArray();
        int index = 0;
        while (index < source.length) {
            final char current = source[index];
            if (current == '/' && index + 1 < source.length) {
                final char next = source[index + 1];
                if (next == '/') {
                    index = maskLineComment(source, sanitized, index);
                    continue;
                }
                if (next == '*') {
                    index = maskBlockComment(source, sanitized, index);
                    continue;
                }
            }
            if (current == '"' || current == '\'') {
                index = maskQuotedLiteral(source, sanitized, index, current);
                continue;
            }
            if (current == '`') {
                index = maskTemplateLiteral(source, sanitized, index);
                continue;
            }
            index++;
        }
        return new String(sanitized);
    }

    private static int maskLineComment(
            final char[] source,
            final char[] sanitized,
            final int start
    ) {
        int index = start;
        maskChar(sanitized, index);
        if (index + 1 < source.length) {
            maskChar(sanitized, index + 1);
        }
        index += 2;
        while (index < source.length && source[index] != '\n' && source[index] != '\r') {
            maskChar(sanitized, index);
            index++;
        }
        return index;
    }

    private static int maskBlockComment(
            final char[] source,
            final char[] sanitized,
            final int start
    ) {
        int index = start;
        maskChar(sanitized, index);
        if (index + 1 < source.length) {
            maskChar(sanitized, index + 1);
        }
        index += 2;
        while (index < source.length) {
            if (index + 1 < source.length && source[index] == '*' && source[index + 1] == '/') {
                maskChar(sanitized, index);
                maskChar(sanitized, index + 1);
                index += 2;
                break;
            }
            maskChar(sanitized, index);
            index++;
        }
        return index;
    }

    private static int maskQuotedLiteral(
            final char[] source,
            final char[] sanitized,
            final int start,
            final char quote
    ) {
        int index = start;
        maskChar(sanitized, index);
        index++;
        while (index < source.length) {
            final char current = source[index];
            maskChar(sanitized, index);
            if (current == '\\') {
                if (index + 1 < source.length) {
                    maskChar(sanitized, index + 1);
                    index += 2;
                    continue;
                }
                index++;
                break;
            }
            index++;
            if (current == quote) {
                break;
            }
        }
        return index;
    }

    private static int maskTemplateLiteral(
            final char[] source,
            final char[] sanitized,
            final int start
    ) {
        int index = start;
        maskChar(sanitized, index);
        index++;
        while (index < source.length) {
            final char current = source[index];
            if (current == '$' && index + 1 < source.length && source[index + 1] == '{') {
                maskChar(sanitized, index);
                maskChar(sanitized, index + 1);
                index += 2;
                index = scanTemplateExpression(source, sanitized, index);
                continue;
            }
            maskChar(sanitized, index);
            if (current == '\\') {
                if (index + 1 < source.length) {
                    maskChar(sanitized, index + 1);
                    index += 2;
                    continue;
                }
                index++;
                break;
            }
            index++;
            if (current == '`') {
                break;
            }
        }
        return index;
    }

    private static int scanTemplateExpression(
            final char[] source,
            final char[] sanitized,
            final int start
    ) {
        int depth = 1;
        int index = start;
        while (index < source.length && depth > 0) {
            final char current = source[index];
            if (current == '/' && index + 1 < source.length) {
                final char next = source[index + 1];
                if (next == '/') {
                    index = maskLineComment(source, sanitized, index);
                    continue;
                }
                if (next == '*') {
                    index = maskBlockComment(source, sanitized, index);
                    continue;
                }
            }
            if (current == '"' || current == '\'') {
                index = maskQuotedLiteral(source, sanitized, index, current);
                continue;
            }
            if (current == '`') {
                index = maskTemplateLiteral(source, sanitized, index);
                continue;
            }
            if (current == '{') {
                depth++;
                index++;
                continue;
            }
            if (current == '}') {
                depth--;
                if (depth == 0) {
                    maskChar(sanitized, index);
                }
                index++;
                continue;
            }
            index++;
        }
        return index;
    }

    private static void maskChar(final char[] sanitized, final int index) {
        if (sanitized[index] == '\n' || sanitized[index] == '\r') {
            return;
        }
        sanitized[index] = ' ';
    }

    private List<ImportStatement> parseImportStatements(final String sourceText) {
        final List<ImportStatement> statements = new ArrayList<>();
        final java.util.regex.Matcher importMatcher = IMPORT_STATEMENT_PATTERN.matcher(sourceText);
        while (importMatcher.find()) {
            final String sideEffectModule = importMatcher.group(4);
            if (sideEffectModule != null) {
                statements.add(new ImportStatement(sideEffectModule, false));
                continue;
            }
            final String importModule = importMatcher.group(3);
            final boolean typeOnly = importMatcher.group(1) != null;
            statements.add(new ImportStatement(importModule, typeOnly));
        }
        return List.copyOf(statements);
    }

    private Path resolveRelativeModule(final Path sourceFile, final String importPath) {
        final Path parent = sourceFile.getParent();
        final Path base = parent == null ? Path.of(importPath) : parent.resolve(importPath);
        final Path normalizedBase = base.normalize();
        final List<Path> candidates = new ArrayList<>();
        final String baseText = normalizedBase.toString();
        if (baseText.endsWith(".ts") || baseText.endsWith(".tsx")) {
            candidates.add(normalizedBase);
        } else {
            candidates.add(Path.of(baseText + ".ts"));
            candidates.add(Path.of(baseText + ".tsx"));
            candidates.add(normalizedBase.resolve("index.ts"));
        }
        for (Path candidate : candidates) {
            final Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalizedCandidate) && Files.isRegularFile(normalizedCandidate)) {
                return normalizedCandidate;
            }
        }
        return null;
    }

    private static SourcePosition toSourcePosition(final String sourceText, final int offset) {
        final int safeOffset = Math.max(0, Math.min(offset, sourceText.length()));
        int line = 1;
        int column = 1;
        for (int index = 0; index < safeOffset; index++) {
            if (sourceText.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SourcePosition(line, column);
    }

    public record StrictEligibilityAnalysis(
            Path entryPath,
            StrictEligibilityViolation violation,
            int analyzedFileCount
    ) {
        public boolean eligible() {
            return violation == null;
        }
    }

    public record StrictEligibilityViolation(
            String featureId,
            String feature,
            Path filePath,
            int line,
            int column,
            String guidance
    ) {
    }

    private record Rule(StrictFeature feature, Pattern pattern) {
    }

    private record Match(
            Rule rule,
            int offset
    ) {
    }

    private record SourceUnit(
            Path sourcePath,
            String sourceText
    ) {
    }

    private record ImportStatement(
            String moduleSpecifier,
            boolean typeOnly
    ) {
    }

    private record SourcePosition(
            int line,
            int column
    ) {
    }

    public enum StrictFeature {
        DYNAMIC_IMPORT(
                "TSJ-STRICT-DYNAMIC-IMPORT",
                "dynamic import expression",
                "Use static relative imports in `jvm-strict` mode."
        ),
        EVAL(
                "TSJ-STRICT-EVAL",
                "`eval` usage",
                "Remove runtime code evaluation and replace with static code paths."
        ),
        FUNCTION_CONSTRUCTOR(
                "TSJ-STRICT-FUNCTION-CONSTRUCTOR",
                "`Function` constructor usage",
                "Declare functions statically in source instead of constructing them at runtime."
        ),
        PROXY(
                "TSJ-STRICT-PROXY",
                "`Proxy` constructor usage",
                "Replace `Proxy`-based behavior with statically declared class or interface implementations."
        ),
        DELETE(
                "TSJ-STRICT-DELETE",
                "`delete` operator usage",
                "Prefer explicit nullable fields or immutable object shapes in `jvm-strict` mode."
        ),
        PROTOTYPE_MUTATION(
                "TSJ-STRICT-PROTOTYPE-MUTATION",
                "prototype mutation",
                "Do not mutate prototypes at runtime; model behavior with classes and inheritance."
        ),
        DYNAMIC_PROPERTY_ADD(
                "TSJ-STRICT-DYNAMIC-PROPERTY-ADD",
                "dynamic computed property write",
                "Use statically declared properties or literal indices in `jvm-strict` mode."
        ),
        UNCHECKED_ANY_MEMBER_INVOKE(
                "TSJ-STRICT-UNCHECKED-ANY-MEMBER-INVOKE",
                "unchecked `any` member invocation",
                "Replace `any` with concrete types before member invocation in `jvm-strict` mode."
        );

        private final String featureId;
        private final String description;
        private final String guidance;

        StrictFeature(final String featureId, final String description, final String guidance) {
            this.featureId = featureId;
            this.description = description;
            this.guidance = guidance;
        }

        public String featureId() {
            return featureId;
        }

        public String description() {
            return description;
        }

        public String guidance() {
            return guidance;
        }
    }
}
