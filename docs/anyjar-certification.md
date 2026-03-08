# TSJ Any-JAR Certification Guide (TSJ-44 through TSJ-44d)

This guide is the single source for TSJ any-jar compatibility certification.

## Certification Stack

1. `TSJ-44`: baseline matrix (curated compatibility fixtures)
2. `TSJ-44a`: real third-party library matrix
3. `TSJ-44b`: certified version-range drift checks
4. `TSJ-44c`: real-app workload gate
5. `TSJ-44d`: governance/signoff gate
6. `TSJ-75`: annotation-survival certification gate (framework-agnostic metadata path)

## TSJ-75 Annotation Survival Certification

Gate suite:

1. `TSJ-75-anyjar-annotation-survival-certification`
2. report artifact: `compiler/backend-jvm/target/tsj75-anyjar-annotation-survival-certification.json`

Required dimensions:

1. `annotation-resolution`: `java:` imported decorators resolve via classpath and unresolved imports emit stable `TSJ-DECORATOR-RESOLUTION`.
2. `annotation-emission`: runtime-visible annotations and supported attribute values survive on generated TS metadata carriers (class/field/constructor/method/parameter).
3. `reflection-consumer-parity`: external jar reflection consumers (DI-style + metadata scanner) read annotations deterministically from generated carriers.

Supported subset (TSJ-75 gate scope):

1. Decorator imports from `java:<fully.qualified.AnnotationType>`.
2. Runtime-retained Java annotation types with supported target usage.
3. Metadata-carrier reflection path for TS-authored classes in the current carrier subset.
4. Generic annotation path shared across libraries (no framework-specific mapping in default `compile`/`run`).

Non-goals (outside TSJ-75 certification scope):

1. Stage-3/legacy decorator forms outside current extractor/backend supported subset.
2. JVM metadata on runtime `TsjObject` instances directly (metadata is exposed via generated carrier classes).
3. Framework-internal behavior not represented by generic reflection-consumer fixtures.
4. Automatic Spring adapter generation in default `compile`/`run`.

Migration from legacy Spring-specific paths:

1. Use default `tsj compile`/`tsj run` for generic any-jar annotation/reflection flow.
2. Use `--legacy-spring-adapters` only for legacy compatibility scenarios that still require generated Spring adapters.
3. Use `tsj spring-package` when packaging legacy Spring adapter outputs into runnable jars.

## TSJ-44 Baseline Matrix

| Category | Library/Class | Version | Check ID |
|---|---|---|---|
| ORM | `sample.orm.JpaLite` | `1.0` | `orm-jpalite` |
| HTTP client | `java.net.URI` | `JDK21` | `http-uri` |
| Serialization | `sample.serialization.JsonCodec` | `1.0` | `serialization-jsoncodec` |
| Validation | `sample.validation.EmailValidator` | `1.0` | `validation-email` |
| Caching | `sample.cache.MemoryCache` | `1.0` | `cache-memory` |
| Messaging | `sample.messaging.Bus` | `1.0` | `messaging-bus` |
| Utility | `java.time.Duration` | `JDK21` | `utility-duration` |

Readiness thresholds (`subsetReady=true` only when all pass):

1. `coveragePercent >= 85.0`
2. `averageDurationMs <= 8000`
3. `maxDurationMs <= 20000`

## TSJ-44a Real Library Matrix

| Category | Library/Class | Version | Check ID |
|---|---|---|---|
| ORM | `org.flywaydb.core.api.MigrationVersion` | `10.17.3` | `orm-flyway-version` |
| JDBC driver | `org.postgresql.Driver` | `42.7.4` | `jdbc-postgresql-driver` |
| Serialization | `com.fasterxml.jackson.databind.ObjectMapper` | `2.17.2` | `serialization-jackson` |
| Configuration | `org.yaml.snakeyaml.Yaml` | `2.2` | `config-snakeyaml` |
| Pooling | `com.zaxxer.hikari.HikariConfig` | `5.1.0` | `pool-hikaricp` |
| Messaging | `com.google.common.eventbus.EventBus` | `33.3.0-jre` | `messaging-guava-eventbus` |
| Utility | `org.apache.commons.lang3.StringUtils` | `3.1` | `utility-commons-lang3` |

## TSJ-44b Version-Range Certification

| Category | Library/Class | Certified Range | Verified Versions | Drift Metadata |
|---|---|---|---|---|
| Utility | `sample.range.RangeApi` | `[1.0.0,2.0.0)` | `1.0.0`, `1.1.0` | `firstFailingVersion` |
| Serialization | `sample.range.TextApi` | `[3.0.0,4.0.0)` | `3.0.0`, `3.1.0` | `firstFailingVersion` |

If a certified version fails:

1. `driftDetected=true`
2. `gatePassed=false`
3. earliest failing version is recorded as `firstFailingVersion`

## TSJ-44c Real-App Gate

Workloads:

1. `orders-batch`
2. `analytics-pipeline`

Gate requires:

1. reliability: all workloads pass
2. performance: average and max duration within configured budgets

Report includes per-workload:

1. `traceFile`
2. `bottleneckHint`
3. duration + stdout/stderr notes

## TSJ-44d Governance Gate

Governance passes only when all signoff criteria pass:

1. `matrix-gate`
2. `version-range-gate`
3. `real-app-gate`

`matrix-gate` is an executable TSJ interop matrix (not classpath-only checks):

1. runs `tsj run` scenarios for Flyway, PostgreSQL, Jackson, SnakeYAML, HikariCP, Guava EventBus, and Apache Commons Lang
2. records deterministic `scenarios/passed/failed` notes and failure diagnostics in signoff criteria
3. emits `certified-subset` manifest entries from executed scenario results

Governance manifest fields:

1. `library`
2. `version`
3. `supportTier`
4. `sourceGate`

Support tiers:

1. `certified-subset`
2. `certified-range`
3. `certified-real-app`

## Artifacts

Generated reports:

1. `cli/target/tsj44-anyjar-certification-report.json`
2. `cli/target/tsj44a-real-library-matrix-report.json`
3. `cli/target/tsj44b-version-range-certification.json`
4. `cli/target/tsj44c-real-app-certification.json`
5. `cli/target/tsj44d-anyjar-governance.json`

## Local Run

```bash
mvn -B -ntp -pl cli -am \
  -Dtest=TsjAnyJarCertificationTest,TsjVersionRangeCertificationTest,TsjRealAppCertificationTest,TsjAnyJarGovernanceCertificationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Certified vs Best-Effort

Certified:

1. Scenarios and versions represented by TSJ-44/44a/44b/44c/44d reports.

Best-effort:

1. Libraries not listed in certification matrices
2. Versions outside certified ranges
3. Framework internals not represented in gate fixtures
