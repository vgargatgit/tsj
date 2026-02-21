package dev.tsj.compiler.backend.jvm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates TSJ-37d security baseline subset from extracted decorators.
 */
final class TsjSecuritySubsetEvaluator {
    static final String FEATURE_ID = "TSJ37D-SECURITY";
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("^\\s*(['\"])(.*)\\1\\s*$");
    private static final Pattern HAS_ROLE_PATTERN = Pattern.compile("^\\s*hasRole\\((.*)\\)\\s*$");
    private static final Pattern HAS_ANY_ROLE_PATTERN = Pattern.compile("^\\s*hasAnyRole\\((.*)\\)\\s*$");

    List<SecuredRoute> collectSecuredRoutes(final TsDecoratorModel decoratorModel) {
        Objects.requireNonNull(decoratorModel, "decoratorModel");
        final List<SecuredRoute> routes = new ArrayList<>();
        for (TsDecoratedClass decoratedClass : decoratorModel.classes()) {
            final String basePath = readPathDecorator(decoratedClass.decorators(), "RequestMapping");
            for (TsDecoratedMethod method : decoratedClass.methods()) {
                final String methodPath = readPathDecorator(method.decorators(), "GetMapping");
                if (methodPath == null) {
                    continue;
                }
                final SecurityExpression expression = readSecurityExpression(method.decorators());
                routes.add(new SecuredRoute(
                        decoratedClass.className(),
                        method.methodName(),
                        joinPath(basePath, methodPath),
                        expression
                ));
            }
        }
        return List.copyOf(routes);
    }

    int evaluateStatus(final SecuredRoute route, final Set<String> roles) {
        Objects.requireNonNull(route, "route");
        final Set<String> normalizedRoles = roles == null ? Set.of() : Set.copyOf(roles);
        if (!route.secured()) {
            return 200;
        }
        if (normalizedRoles.isEmpty()) {
            return 401;
        }
        return route.expression().matches(normalizedRoles) ? 200 : 403;
    }

    private SecurityExpression readSecurityExpression(final List<TsDecoratorUse> decorators) {
        for (TsDecoratorUse decorator : decorators) {
            if (!"PreAuthorize".equals(decorator.name())) {
                continue;
            }
            final String expression = unquoteRequired(decorator.rawArgs(), "@PreAuthorize");
            final Matcher hasRole = HAS_ROLE_PATTERN.matcher(expression);
            if (hasRole.matches()) {
                final String role = unquoteRequired(hasRole.group(1), "hasRole");
                return new SecurityExpression(List.of(role), false, expression);
            }
            final Matcher hasAnyRole = HAS_ANY_ROLE_PATTERN.matcher(expression);
            if (hasAnyRole.matches()) {
                final List<String> roles = parseRoleList(hasAnyRole.group(1));
                if (roles.isEmpty()) {
                    throw unsupportedExpression(expression);
                }
                return new SecurityExpression(roles, true, expression);
            }
            throw unsupportedExpression(expression);
        }
        return null;
    }

    private static List<String> parseRoleList(final String rawRoles) {
        final String trimmed = rawRoles == null ? "" : rawRoles.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        final String[] parts = trimmed.split(",");
        final List<String> roles = new ArrayList<>();
        for (String part : parts) {
            final String role = unquoteRequired(part, "hasAnyRole");
            if (!role.isBlank()) {
                roles.add(role);
            }
        }
        return List.copyOf(roles);
    }

    private static JvmCompilationException unsupportedExpression(final String expression) {
        return new JvmCompilationException(
                "TSJ-DECORATOR-UNSUPPORTED",
                "Unsupported security expression in TSJ-37d subset: " + expression,
                null,
                null,
                null,
                FEATURE_ID,
                "Use @PreAuthorize with hasRole('ROLE') or hasAnyRole('ROLE1','ROLE2') in TSJ-37d subset."
        );
    }

    private static String readPathDecorator(final List<TsDecoratorUse> decorators, final String decoratorName) {
        for (TsDecoratorUse decorator : decorators) {
            if (!decoratorName.equals(decorator.name())) {
                continue;
            }
            if (decorator.rawArgs() == null || decorator.rawArgs().isBlank()) {
                return "";
            }
            return unquoteRequired(decorator.rawArgs(), "@" + decoratorName);
        }
        return null;
    }

    private static String unquoteRequired(final String rawValue, final String context) {
        final String value = rawValue == null ? "" : rawValue.trim();
        final Matcher stringLiteral = STRING_LITERAL_PATTERN.matcher(value);
        if (stringLiteral.matches()) {
            return stringLiteral.group(2);
        }
        throw new JvmCompilationException(
                "TSJ-DECORATOR-UNSUPPORTED",
                "Unsupported string argument for " + context + " in TSJ-37d subset: " + value,
                null,
                null,
                null,
                FEATURE_ID,
                "Use string literal arguments for security decorator expressions and route paths."
        );
    }

    private static String joinPath(final String basePath, final String methodPath) {
        final String base = normalizePath(basePath == null ? "" : basePath);
        final String method = normalizePath(methodPath == null ? "" : methodPath);
        if (base.isEmpty()) {
            return method.isEmpty() ? "/" : method;
        }
        if (method.isEmpty() || "/".equals(method)) {
            return base;
        }
        return base.endsWith("/") ? base + method.substring(1) : base + method;
    }

    private static String normalizePath(final String rawPath) {
        final String trimmed = rawPath == null ? "" : rawPath.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "/";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    record SecuredRoute(
            String className,
            String methodName,
            String endpointPath,
            SecurityExpression expression
    ) {
        SecuredRoute {
            className = Objects.requireNonNull(className, "className");
            methodName = Objects.requireNonNull(methodName, "methodName");
            endpointPath = Objects.requireNonNull(endpointPath, "endpointPath");
        }

        boolean secured() {
            return expression != null;
        }
    }

    record SecurityExpression(List<String> roles, boolean any, String rawExpression) {
        SecurityExpression {
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            rawExpression = Objects.requireNonNull(rawExpression, "rawExpression");
        }

        boolean matches(final Set<String> actorRoles) {
            final Set<String> normalized = new LinkedHashSet<>(Objects.requireNonNull(actorRoles, "actorRoles"));
            if (any) {
                return roles.stream().anyMatch(normalized::contains);
            }
            return roles.stream().allMatch(normalized::contains);
        }
    }
}
