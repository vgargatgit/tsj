# TSJ Developer Guide

For a simpler docs map, start at `docs/README.md`.

## What TSJ Is (Today)

TSJ compiles a **TypeScript subset** to JVM bytecode and executes on the JVM.

- TS grammar support is not complete yet.
- Interop with Java (`java:` imports) is implemented in staged subsets.
- Feature/story status is tracked in `docs/stories.md`.

Use these references as source of truth:

- Docs map / read order: `docs/README.md`
- CLI behavior and flags: `docs/cli-contract.md`
- Unsupported non-goals (MVP gates): `docs/unsupported-feature-matrix.md`
- Story status and acceptance criteria: `docs/stories.md`
- Active implementation gaps/TODOs: `docs/todo.md`

## Prerequisites

1. Java 21+
2. Maven 3.8+
3. Node.js 20+ (Node 24 is also validated)
4. TypeScript installed (`npm i -D typescript` recommended)

## Repo Setup

```bash
npm ci
mvn -B -ntp test
```

## Daily Workflow (Required)

TSJ follows comprehensive TDD.

1. `Red`: add/update tests first.
2. `Green`: implement minimal code to pass tests.
3. `Refactor`: clean up without changing behavior.

Done criteria for any change:

1. Relevant tests pass.
2. Lint/checkstyle passes.
3. Behavior/docs/stories are updated when needed.

## Core Commands

Compile:

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile path/to/main.ts --out build"
```

Run:

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run path/to/main.ts --out build"
```

Run fixtures:

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="fixtures tests/fixtures"
```

Run tests:

```bash
mvn -B -ntp test
```

Module-local test loop:

```bash
mvn -B -ntp -pl cli -am test
mvn -B -ntp -pl runtime -am test
mvn -B -ntp -pl compiler/backend-jvm -am test
```

## Java Interop Quick Start

Example TypeScript:

```ts
import { max } from "java:java.lang.Math";
console.log(max(3, 7));
```

Run with external jar(s):

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run app.ts --out build --jar path/to/lib.jar"
```

Run with explicit classpath:

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run app.ts --out build --classpath path/to/a.jar:path/to/b.jar"
```

Important:

- `strict` interop policy is default.
- `broad` interop requires explicit acknowledgement (`--ack-interop-risk`).
- See full rules in `docs/cli-contract.md`.

## TypeScript Support Expectations

Do not assume “full TypeScript” support.

Before adopting syntax in production fixtures:

1. Check story status in `docs/stories.md`.
2. Check unsupported gates in `docs/unsupported-feature-matrix.md`.
3. Check active parser/runtime gaps in `docs/todo.md`.
4. Add a failing test first for any new syntax/semantic expectation.

For known unsupported or non-parity grammar/semantic cases tracked as a progression suite:
`unsupported/README.md` and `unsupported/run_progress.sh`.

## Diagnostics and Debugging

- CLI emits structured diagnostics with codes (for example `TSJ-CLI-*`, `TSJ-RUN-*`, `TSJ-BACKEND-*`).
- Command/flag contract and diagnostic families are documented in `docs/cli-contract.md`.
- For runtime mapping back to TS coordinates, use `--ts-stacktrace`.

## Recommended Read Order for New Contributors

1. `README.md`
2. `docs/README.md`
3. `docs/developer-guide.md` (this file)
4. `docs/cli-contract.md`
5. `docs/unsupported-feature-matrix.md`
6. `docs/todo.md`
7. `docs/stories.md`
