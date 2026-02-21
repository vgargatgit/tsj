package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjSecuritySubsetEvaluatorTest {
    @Test
    void collectsSecuredRoutesAndParsesSupportedExpressions() {
        final TsjSecuritySubsetEvaluator evaluator = new TsjSecuritySubsetEvaluator();
        final TsDecoratorModel model = new TsDecoratorModel(List.of(
                new TsDecoratedClass(
                        Path.of("fixtures/security.ts"),
                        "SecurityController",
                        1,
                        List.of(
                                new TsDecoratorUse("RestController", null, 1),
                                new TsDecoratorUse("RequestMapping", "\"/secure\"", 2)
                        ),
                        List.of(),
                        List.of(
                                new TsDecoratedMethod(
                                        "publicEndpoint",
                                        4,
                                        List.of(),
                                        false,
                                        List.of(new TsDecoratorUse("GetMapping", "\"/public\"", 4))
                                ),
                                new TsDecoratedMethod(
                                        "adminEndpoint",
                                        8,
                                        List.of(),
                                        false,
                                        List.of(
                                                new TsDecoratorUse("PreAuthorize", "\"hasRole('ADMIN')\"", 8),
                                                new TsDecoratorUse("GetMapping", "\"/admin\"", 9)
                                        )
                                ),
                                new TsDecoratedMethod(
                                        "supportEndpoint",
                                        13,
                                        List.of(),
                                        false,
                                        List.of(
                                                new TsDecoratorUse(
                                                        "PreAuthorize",
                                                        "\"hasAnyRole('ADMIN','SUPPORT')\"",
                                                        13
                                                ),
                                                new TsDecoratorUse("GetMapping", "\"/support\"", 14)
                                        )
                                )
                        )
                )
        ));

        final List<TsjSecuritySubsetEvaluator.SecuredRoute> routes = evaluator.collectSecuredRoutes(model);

        assertEquals(3, routes.size());
        final TsjSecuritySubsetEvaluator.SecuredRoute publicRoute = routes.stream()
                .filter(route -> "/secure/public".equals(route.endpointPath()))
                .findFirst()
                .orElseThrow();
        final TsjSecuritySubsetEvaluator.SecuredRoute adminRoute = routes.stream()
                .filter(route -> "/secure/admin".equals(route.endpointPath()))
                .findFirst()
                .orElseThrow();
        final TsjSecuritySubsetEvaluator.SecuredRoute supportRoute = routes.stream()
                .filter(route -> "/secure/support".equals(route.endpointPath()))
                .findFirst()
                .orElseThrow();

        assertFalse(publicRoute.secured());
        assertTrue(adminRoute.secured());
        assertEquals(List.of("ADMIN"), adminRoute.expression().roles());
        assertFalse(adminRoute.expression().any());
        assertEquals(List.of("ADMIN", "SUPPORT"), supportRoute.expression().roles());
        assertTrue(supportRoute.expression().any());
    }

    @Test
    void evaluatesBaselineStatusesForPublicAndSecuredRoutes() {
        final TsjSecuritySubsetEvaluator evaluator = new TsjSecuritySubsetEvaluator();
        final TsjSecuritySubsetEvaluator.SecuredRoute publicRoute = new TsjSecuritySubsetEvaluator.SecuredRoute(
                "SecurityController",
                "publicEndpoint",
                "/secure/public",
                null
        );
        final TsjSecuritySubsetEvaluator.SecuredRoute adminRoute = new TsjSecuritySubsetEvaluator.SecuredRoute(
                "SecurityController",
                "adminEndpoint",
                "/secure/admin",
                new TsjSecuritySubsetEvaluator.SecurityExpression(List.of("ADMIN"), false, "hasRole('ADMIN')")
        );

        assertEquals(200, evaluator.evaluateStatus(publicRoute, Set.of()));
        assertEquals(200, evaluator.evaluateStatus(publicRoute, Set.of("USER")));
        assertEquals(401, evaluator.evaluateStatus(adminRoute, Set.of()));
        assertEquals(403, evaluator.evaluateStatus(adminRoute, Set.of("USER")));
        assertEquals(200, evaluator.evaluateStatus(adminRoute, Set.of("ADMIN")));
    }

    @Test
    void rejectsUnsupportedSecurityExpressionsWithStableDiagnostic() {
        final TsjSecuritySubsetEvaluator evaluator = new TsjSecuritySubsetEvaluator();
        final TsDecoratorModel model = new TsDecoratorModel(List.of(
                new TsDecoratedClass(
                        Path.of("fixtures/security-unsupported.ts"),
                        "SecurityController",
                        1,
                        List.of(new TsDecoratorUse("RequestMapping", "\"/secure\"", 1)),
                        List.of(),
                        List.of(
                                new TsDecoratedMethod(
                                        "authorityEndpoint",
                                        3,
                                        List.of(),
                                        false,
                                        List.of(
                                                new TsDecoratorUse("PreAuthorize", "\"hasAuthority('SCOPE_read')\"", 3),
                                                new TsDecoratorUse("GetMapping", "\"/authority\"", 4)
                                        )
                                )
                        )
                )
        ));

        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> evaluator.collectSecuredRoutes(model)
        );

        assertEquals("TSJ-DECORATOR-UNSUPPORTED", exception.code());
        assertEquals("TSJ37D-SECURITY", exception.featureId());
        assertTrue(exception.getMessage().contains("Unsupported security expression"));
    }
}
