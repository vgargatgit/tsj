# TSJ Quickstart

Compile and run TypeScript on the JVM, with optional external JAR dependencies.

## Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 20+
- TypeScript package available (`npm i -D typescript` in repo root)

## Compile TypeScript

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build"
```

## Compile with additional JARs

Use one or both:

- repeated `--jar` flags
- `--classpath` for multiple entries

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build --jar libs/a.jar --jar libs/b.jar"
```

```bash
# Linux/macOS
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build --classpath libs/a.jar:libs/b.jar"
```

```bash
# Windows
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build --classpath libs/a.jar;libs/b.jar"
```

## Run TypeScript

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build"
```

## Run with additional JARs

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --jar libs/a.jar --jar libs/b.jar"
```

```bash
# Linux/macOS
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --classpath libs/a.jar:libs/b.jar"
```

```bash
# Windows
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build --classpath libs/a.jar;libs/b.jar"
```
