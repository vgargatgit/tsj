# TSJ Pet Clinic (TS-Only, JVM Strict, JPA/H2)

This example is TS-only application code:

- entities, repository, service, and controller are all under `src/` in TypeScript
- strict compilation mode is used: `--mode jvm-strict`
- Spring/JPA/H2 are consumed from jars via `java:` imports
- no custom Java fixture application code is required

## Dependencies

`examples/pet-clinic/pom.xml` resolves:

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `h2`
- `springdoc-openapi-starter-webmvc-ui`

## Quick Verification (compile + run)

```bash
bash examples/pet-clinic/scripts/run.sh
```

Expected:

- `PET-CLINIC RESULT: PASS (strict compile + run)`
- boot marker `tsj-pet-clinic-boot`

## Run HTTP Server

```bash
bash examples/pet-clinic/scripts/run-http.sh
```

Alternative runner with native Linux temp staging and phase timers:

```bash
bash examples/pet-clinic/scripts/run-http-native.sh
```

In another shell:

```bash
curl 'http://127.0.0.1:8080/api/petclinic/owners?lastName=Frank'
curl 'http://127.0.0.1:8080/api/petclinic/owners/1/pets'
curl 'http://127.0.0.1:8080/v3/api-docs'
open 'http://127.0.0.1:8080/swagger-ui/index.html'
```

## Notes

- `run-http.sh` uses `tsj package ... --mode jvm-strict` and runs the packaged jar.
- `run-http-native.sh` stages dependency jars and packaged output under `/tmp/tsj-pet-clinic-http`,
  prints phase timers, and was measured at about:
  - `30s` dependency copy
  - `90s` reactor install
  - `30s` package
  - `4-5s` Spring Boot HTTP readiness
- H2 is configured through `examples/pet-clinic/resources/application.properties`.
- schema/data initialization is in:
  - `examples/pet-clinic/resources/schema.sql`
  - `examples/pet-clinic/resources/data.sql`
