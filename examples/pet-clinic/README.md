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

In another shell:

```bash
curl 'http://127.0.0.1:8080/api/petclinic/owners?lastName=Frank'
curl 'http://127.0.0.1:8080/api/petclinic/owners/1/pets'
```

## Notes

- `run-http.sh` uses `tsj spring-package ... --mode jvm-strict` and runs the packaged jar.
- H2 is configured through `examples/pet-clinic/resources/application.properties`.
- schema/data initialization is in:
  - `examples/pet-clinic/resources/schema.sql`
  - `examples/pet-clinic/resources/data.sql`
