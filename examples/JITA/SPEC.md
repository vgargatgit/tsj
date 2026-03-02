## JITA — Jar Interop Torture App

### What it is

A repo with **5 scenario fixtures**, each one designed to trip exactly one shortfall. Each scenario has:

* a tiny TypeScript entry file that tries the interop,
* a controlled set of jars/classpath inputs,
* an `expectation.json` saying whether it should **pass** or **fail** and which diagnostic code to expect.

You run a single command that executes the whole matrix and prints a compact report:

* ✅ scenario still blocked (expected fail, saw correct diag)
* ✅ scenario now supported (expected pass)
* ❌ regression (expected pass but failed, or expected fail but passed unexpectedly, or wrong diagnostic)

### Layout

```text
jita/
  README.md
  jita.json                       # runs all scenarios
  scenarios/
    S1_missing_runtime_classpath/
      main.ts
      expect.json
      jars/...
    S2_non_public_member/
      main.ts
      expect.json
      jars/...
    S3_conflicting_versions/
      main.ts
      expect.json
      jars/...
    S4_provided_scope_runtime/
      main.ts
      expect.json
      jars/...
    S5_app_isolated_duplication/
      main.ts
      expect.json
      jars/...
  tools/
    run_matrix.sh
    run_matrix.ps1
```

---

## Scenario specs (each maps 1:1 to your list)

### S1) Missing runtime classpath (compile ok, run fails)

**Goal:** Detect if TSJ still cannot call classes absent at runtime.

**Setup**

* Compile with `--jar libs/api.jar` (contains `dev.jita.Api`)
* Run **without** that jar on runtime classpath (omit `--jar` / `--classpath`)

**main.ts**

* `import { Api } from "java:dev.jita.Api";`
* call a simple static method `Api.ping()` and print result

**Expected today**

* Run fails with a class-not-found style diagnostic (whatever TSJ emits: e.g., `TSJ-RUN-CLASS-NOT-FOUND` or similar)

**Signals “fixed” when**

* Run succeeds (meaning you introduced packaging/embedding or automatic runtime classpath propagation)

**Why it’s a torture test**
It separates compile-time descriptor availability vs runtime classloading reality.

---

### S2) Non-public member access (package-private/private)

**Goal:** Detect if TSJ still blocks reflective access to non-public Java members.

**Setup jar**
`libs/nonpublic.jar` contains:

* `dev.jita.hidden.Hidden` with:

    * `static String packagePrivatePing()` (no modifier)
    * `private static String privatePing()`

**main.ts**

* attempt to call both via `java:` import binding

**Expected today**

* Compile should fail (preferred) with `TSJ55-INTEROP-VISIBILITY` / `TSJ-INTEROP-REFLECTIVE`

    * or run fails with explicit “non-public reflective access” diagnostic

**Signals “fixed” when**

* It succeeds *only if* you intentionally allow:

    * `setAccessible(true)` paths (and policy allows it)
    * or you generate a public bridge in same package (rare unless you control jar)

**Note**
This scenario should run in both policy modes:

* strict (expect fail)
* broad with explicit risk ack (still likely fail unless you added a privileged mechanism)

---

### S3) Conflicting versions of same library in one classpath

**Goal:** Detect if TSJ still rejects classpath that includes `foo-1.0.jar` and `foo-2.0.jar`.

**Setup**

* Provide two jars with same “artifact stem” but different versions:

    * `libs/dupe-lib-1.0.jar`
    * `libs/dupe-lib-2.0.jar`

Both include `dev.jita.dupe.Versioned` but return different values so you can also test “which one wins” if you ever allow it.

**main.ts**

* call `Versioned.version()` and print

**Expected today**

* TSJ fails at CLI normalization with `TSJ-CLASSPATH-CONFLICT` (or your deterministic conflict diag)

**Signals “fixed” when**
You decide to allow conflicts via mediation rules:

* compile/run succeeds and deterministically selects the winner
* AND report includes which jar was chosen (ideally from class-index metadata)

---

### S4) Provided-scope dependencies missing at runtime

**Goal:** Detect if TSJ still enforces “provided not in runtime scope.”

**Setup**

* Use a dependency jar `libs/provided-only.jar` containing `dev.jita.provided.ProvidedApi`
* Mark it as “provided” in the scenario’s classpath metadata (however TSJ currently models it; if TSJ expects Maven mediation metadata, supply a minimal POM/`pom.properties` so TSJ-40b applies scope rules)

**main.ts**

* call `ProvidedApi.ping()`

**Expected today**

* Compile may succeed (if compile includes provided)
* Run fails with `TSJ-CLASSPATH-SCOPE` or equivalent “available only in provided scope” diagnostic

**Signals “fixed” when**

* You either:

    * include provided deps in runtime (policy change), or
    * provide auto-packaging that embeds them for runtime, or
    * allow scenario-level override: “treat provided as runtime”

This test confirms that change.

---

### S5) App-isolated mode rejects class duplication between app output and deps

**Goal:** Detect if app-isolated mode still fails when app outputs same class as a dependency.

**Setup**

* Dependency jar contains `dev.jita.conflict.Clash`
* The TSJ program also emits/produces a class with same binary name (or you include a generated Java helper class compiled into app output) — simplest: include a tiny Java source in scenario that TSJ compile step compiles into output class dir.

**main.ts**

* call `Clash.who()` and print

**Expected today**

* Fail fast in app-isolated mode with `TSJ-RUN-009` (as you mentioned) and origin-aware diagnostics

**Signals “fixed” when**

* Either you relax isolation rules, or implement namespace shading/relocation, or add deterministic precedence that allows duplicates.

---

## How you run it (matrix runner)

A runner script loops scenarios and executes:

1. **compile**

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="compile scenarios/<S>/main.ts --out scenarios/<S>/build <classpath flags>"
```

2. **run**

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run scenarios/<S>/main.ts --out scenarios/<S>/build <runtime classpath flags>"
```

The runner captures:

* exit code
* stdout last JSON line (optional)
* stderr diagnostics (TSJ emits structured diagnostics already)

Then compares against `expect.json` fields:

* `expectCompile`: pass/fail + diag code(s)
* `expectRun`: pass/fail + diag code(s)

---

## Why this torture app is effective

* It doesn’t just “fail”; it fails **in a targeted, signature way** (specific diag IDs).
* It cleanly distinguishes:

    * compile-time descriptor success vs runtime classloading
    * policy failures vs technical failures
    * mediation conflicts vs isolation conflicts
* When you fix something, the test naturally flips to pass (or to a different expected diag if you intentionally change behavior).