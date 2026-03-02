# TSJ Spring Pet Store API

This sample is a TypeScript-authored Spring-style Pet Store API for TSJ.

## Project Structure

- `main.ts`: entrypoint (loads controllers and prints boot marker)
- `src/domain/pet.ts`: domain/request models
- `src/repository/in-memory-pet-repository.ts`: in-memory persistence
- `src/service/pet-service.ts`: validation + business logic
- `src/web/health-controller.ts`: health endpoint
- `src/web/pet-controller.ts`: CRUD endpoints
- `src/main/resources/application.properties`: Spring app defaults

## Endpoints

- `GET /api/health`
- `GET /api/pets/list`
- `GET /api/pets/get?id=<pet-id>`
- `POST /api/pets/create?name=<name>&species=<species>&age=<age>&vaccinated=<true|false>`
- `PUT /api/pets/update?id=<pet-id>&name=<name>&species=<species>&age=<age>&vaccinated=<true|false>`
- `DELETE /api/pets/delete?id=<pet-id>`

## Compile with TSJ

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile examples/pet-store-api/main.ts --out examples/pet-store-api/.tsj-build"
```

## Run generated TSJ program

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/pet-store-api/main.ts --out examples/pet-store-api/.tsj-build"
```

Expected stdout marker:

```text
tsj-pet-store-boot
```

## Package as Spring-style jar

`spring-package` compiles generated Spring adapter sources, so you must provide Spring jars
on classpath (or equivalent stubs).

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="spring-package examples/pet-store-api/main.ts --out examples/pet-store-api/.tsj-package --classpath /path/to/spring-web.jar:/path/to/spring-context.jar --smoke-run --smoke-endpoint-url stdout://tsj-pet-store-boot"
```
