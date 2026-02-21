# TSJ Any-JAR Certification (TSJ-44 through TSJ-44d)

## Purpose
This document defines the full TSJ-44 certification stack for broad interop claims.

It distinguishes:
1. Certified compatibility (explicitly tested and reported).
2. Best-effort compatibility (outside the certified matrix).

## Certified Baseline Matrix (TSJ-44)

| Category | Library/Class | Version | Certification Check ID |
|---|---|---|---|
| ORM | `sample.orm.JpaLite` | `1.0` | `orm-jpalite` |
| HTTP client | `java.net.URI` | `JDK21` | `http-uri` |
| Serialization | `sample.serialization.JsonCodec` | `1.0` | `serialization-jsoncodec` |
| Validation | `sample.validation.EmailValidator` | `1.0` | `validation-email` |
| Caching | `sample.cache.MemoryCache` | `1.0` | `cache-memory` |
| Messaging | `sample.messaging.Bus` | `1.0` | `messaging-bus` |
| Utility | `java.time.Duration` | `JDK21` | `utility-duration` |

Source of truth for execution is:
`cli/src/test/java/dev/tsj/cli/TsjAnyJarCertificationTest.java`.

## Certified Real-Library Matrix (TSJ-44a)

| Category | Library/Class | Version | Certification Check ID |
|---|---|---|---|
| ORM | `org.flywaydb.core.api.MigrationVersion` | `10.17.3` | `orm-flyway-version` |
| JDBC driver | `org.postgresql.Driver` | `42.7.4` | `jdbc-postgresql-driver` |
| Serialization | `com.fasterxml.jackson.databind.ObjectMapper` | `2.17.2` | `serialization-jackson` |
| Configuration | `org.yaml.snakeyaml.Yaml` | `2.2` | `config-snakeyaml` |
| Pooling | `com.zaxxer.hikari.HikariConfig` | `5.1.0` | `pool-hikaricp` |
| Messaging | `com.google.common.eventbus.EventBus` | `33.3.0-jre` | `messaging-guava-eventbus` |
| Utility | `org.apache.commons.lang3.StringUtils` | `3.1` | `utility-commons-lang3` |

The TSJ-44a matrix extends baseline TSJ-44 certification with real third-party jar checks.
Each check records pass/fail with library, version, runtime duration, and diagnostic detail.

## Readiness Gate Thresholds

The TSJ-44 baseline report marks `subsetReady=true` only when all thresholds pass:
1. `coveragePercent >= 85.0`
2. `averageDurationMs <= 8000`
3. `maxDurationMs <= 20000`

## Certified Version Ranges (TSJ-44b)

| Category | Library/Class | Certified Range | Verified Versions | Drift Metadata |
|---|---|---|---|---|
| Utility | `sample.range.RangeApi` | `[1.0.0,2.0.0)` | `1.0.0`, `1.1.0` | `firstFailingVersion` |
| Serialization | `sample.range.TextApi` | `[3.0.0,4.0.0)` | `3.0.0`, `3.1.0` | `firstFailingVersion` |

Source of truth for TSJ-44b execution is:
`cli/src/test/java/dev/tsj/cli/TsjVersionRangeCertificationHarness.java`
and
`cli/src/test/java/dev/tsj/cli/TsjVersionRangeCertificationTest.java`.

TSJ-44b marks `gatePassed=true` only when all certified versions pass.
If any certified version fails, the report sets:
1. `driftDetected=true`
2. `gatePassed=false`
3. `firstFailingVersion` to the earliest failing version per library

## Report Artifact

Certification emits JSON reports:
1. `cli/target/tsj44-anyjar-certification-report.json`
2. `cli/target/tsj44a-real-library-matrix-report.json`
3. `cli/target/tsj44b-version-range-certification.json`
4. `cli/target/tsj44c-real-app-certification.json`
5. `cli/target/tsj44d-anyjar-governance.json`

Report includes:
1. Per-check pass/fail status.
2. Library + version metadata.
3. Coverage and duration threshold summary.

CI uploads these reports as artifacts:
1. `tsj44-anyjar-certification-report`
2. `tsj44a-real-library-matrix-report`
3. `tsj44b-version-range-certification-report`
4. `tsj44c-real-app-certification-report`
5. `tsj44d-anyjar-governance-report`

## How To Run Locally

```bash
mvn -B -ntp -pl cli -am -Dtest=TsjAnyJarCertificationTest,TsjVersionRangeCertificationTest,TsjRealAppCertificationTest,TsjAnyJarGovernanceCertificationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Support Boundary

Certified:
1. The TSJ-44 baseline matrix above, under documented CLI/runtime constraints.
2. The TSJ-44a real-library matrix above, for listed library/version baselines.
3. The TSJ-44b version-range checks listed above for certified ranges.
4. The TSJ-44c real-app workload gate for listed workloads/budgets.
5. The TSJ-44d governance signoff criteria and manifest tiers.

Best-effort:
1. Libraries not listed in this matrix.
2. Versions not listed in this matrix.
3. Deep framework internals not exercised by current certified checks (for example, full ORM proxy/lazy-load internals).

Do not interpret successful execution outside the matrix as certified compatibility.

## Upgrade Guidance

When upgrading a certified library:
1. Add target versions to the TSJ-44b harness range list.
2. Re-run `TsjVersionRangeCertificationTest`.
3. Treat any `firstFailingVersion` as a drift regression until either:
   - Interop/runtime changes restore compatibility, or
   - The certified range is narrowed and release notes are updated.
