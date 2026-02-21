# Kotlin/Java to TSJ Migration Guide (TSJ-38 Subset)

This guide describes the current migration path from Kotlin/Java Spring code to TSJ-authored TypeScript.

## Current Supported Path

1. Web/controller decorator subset:
   - `@RestController`
   - `@RequestMapping`
   - Route mappings (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`)
2. TS-authored component subset:
   - `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`
   - `@Bean`, `@PostConstruct`, `@PreDestroy`
3. Transactional subset:
   - `@Transactional` on class/method declarations
   - interface-based proxy compatibility path
4. Packaging/startup subset:
   - `tsj spring-package <entry.ts> --out <dir> [--smoke-run]`

## Recommended Migration Sequence

1. Port pure domain and service logic first (no framework coupling).
2. Port controller boundaries to supported decorator subset.
3. Keep DB/security/validation/actuator concerns in Java/Kotlin bridge modules while TSJ subset matures.
4. Validate parity with fixture and Spring matrix reports before widening surface area.

## Known Gaps (as of TSJ-38 subset)

1. Validation constraints (`@Validated`, bean validation annotations).
2. Data JDBC/JPA repository integration.
3. Actuator endpoints and operational probes.
4. Spring Security method/filter-chain integration.
5. End-to-end production parity with full Kotlin/Spring stacks.
