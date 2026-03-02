Below is a concrete **test app spec** that *forces* TSJ to exercise **jar discovery, mediation, MR-JAR selection, module/jrt, generics signatures, nullability, inheritance/override normalization, SAM/property synthesis, and overload resolution**—all from **one external jar** (plus optionally JDK modules via `jrt:/`).

# TSJ Interop Torture App (TITA) — Spec

## 1) Purpose

A small TSJ project that compiles and runs a TSJ program which imports Java types via `java:` from **a dependency jar** and exercises:

* deterministic class discovery + duplicates/shadowing diagnostics (TSJ-45)
* classfile parsing (TSJ-46)
* lazy resolution + persistent cache (TSJ-47/56)
* generics signature parsing (TSJ-48)
* nullability inference (TSJ-49)
* supertypes + member lookup + access checks (TSJ-50/57/57a)
* override/bridge filtering (TSJ-51)
* property synthesis (TSJ-52)
* SAM detection (TSJ-53)
* compile-time overload resolution + certified runtime invoke (TSJ-54)
* frontend `java:` import integration (TSJ-55)

## 2) Repo Layout

```
tita/
  README.md
  tsj.json               // TSJ project config (classpath, mode, targetJdk)
  deps/
    tita-fixtures-1.0.jar
  src/
    main.tsj             // TSJ program exercising interop
    scenarios/
      overloads.tsj
      generics.tsj
      nullability.tsj
      sam_and_props.tsj
      modules_jrt.tsj
      duplicates.tsj
  expected/
    shared/
      class-index.json.expect
    isolated/
      diagnostics.expect
```

## 3) Required Dependency Jar

### Jar name

`deps/tita-fixtures-1.0.jar`

### How it’s produced (informational; app just consumes it)

A separate “fixtures” Java build (Gradle/Maven) that emits a jar containing specially crafted classes and metadata.

### Contents required in the jar

Package prefix: `dev.tita.fixtures`

#### 3.1 Overloads + boxing/widening/varargs/nullability

Class: `dev.tita.fixtures.Overloads`

* Methods (static):

    * `pick(int x)`
    * `pick(long x)`
    * `pick(Integer x)`  *(boxing candidate)*
    * `pick(Number x)`
    * `pick(int... xs)` *(varargs)*
    * `pick(String s)`
    * `pick(Object o)`
* Methods (instance):

    * `join(String a, String b)`
    * `join(Object a, Object b)`
* Nullability annotations on some params/returns using at least **two families**:

    * JetBrains `@NotNull/@Nullable`
    * JSR-305 / `javax.annotation.Nullable` or `@ParametersAreNonnullByDefault` in `package-info.java`

Expected behaviors:

* compile-time selects correct overload deterministically and persists selected identity
* diagnostics for ambiguous calls include candidate summary + reasons

#### 3.2 Generics signatures + intersections + wildcards

Class: `dev.tita.fixtures.Generics`

* `static <T extends CharSequence & Comparable<T>> T echo(T x)`
* `static List<? extends Number> nums()`
* `static Map<String, List<Integer>> map()`
* Inner generic class: `Box<T>`

    * `T get()`
    * `void set(T t)`

Expected behaviors:

* Signature attribute parsing used (not erased) where available
* unsupported signature forms fall back to erased descriptor with explicit marker

#### 3.3 Inheritance + overrides + bridge/synthetic

Interfaces/classes:

* `dev.tita.fixtures.Base<T>`

    * `T id(T x)`
* `dev.tita.fixtures.Derived extends Base<String>`

    * overrides `String id(String x)` (forces bridge)
* `dev.tita.fixtures.CovariantBase`

    * `Number v()`
* `dev.tita.fixtures.CovariantDerived extends CovariantBase`

    * `Integer v()` (covariant return)

Expected behaviors:

* bridge method filtered when non-bridge equivalent exists
* override lineage recorded for covariant return reporting
* member collection includes inherited members with correct visibility metadata

#### 3.4 SAM + default/static/object-method exclusions

Interface: `dev.tita.fixtures.MyFn<T, R>`

* exactly one abstract method: `R apply(T t)`
* includes:

    * `default String name()`
    * `static MyFn ...` factory
    * redeclare `toString()`? (should be excluded as Object member in SAM detection)

Expected behaviors:

* TSJ recognizes SAM and exposes canonical samMethod metadata
* TSJ lambda/adapter generation path is exercised (even if adapter is minimal)

#### 3.5 Property synthesis (get/is/set)

Class: `dev.tita.fixtures.Bean`

* `String getTitle()`
* `void setTitle(String t)`
* `boolean isReady()`
* ambiguous cases included:

    * `String getURL()`, `String getUrl()` (conflict)
    * `void setValue(int v)`, `void setValue(String v)` (overloaded setter)
      Expected behaviors:
* title + ready properties synthesized
* URL and value skipped with deterministic “why skipped” diagnostics
* feature flag toggles synthesis on/off

#### 3.6 Module / access tests (optional but recommended)

Jar should include `Automatic-Module-Name: dev.tita.fixtures` in manifest.

Also include a package that is **not exported** in a named-module scenario if you build a modular jar variant later (optional stretch).

#### 3.7 Multi-release jar portion (MR-JAR)

Jar is a **Multi-Release** jar:

* Base entry: `dev/tita/fixtures/mr/MrPick.class` returns `"base"`
* Versioned entry: `META-INF/versions/11/dev/tita/fixtures/mr/MrPick.class` returns `"v11"`
  Expected behaviors:
* TSJ chooses deterministic entry based on configured target JDK
* selected origin metadata persisted (base vs versioned)

## 4) TSJ App Source Requirements

### 4.1 main.tsj behavior

* Runs a set of scenario functions and prints a deterministic report:

    * `OVERLOAD_OK`
    * `GENERICS_OK`
    * `NULLABILITY_OK`
    * `INHERITANCE_OK`
    * `SAM_OK`
    * `PROPS_OK`
    * `MRJAR_OK`
    * `JRT_OK`
* Exit code 0 only if all checks pass.

### 4.2 Required interop imports

Each scenario must import at least one symbol via `java:` from the jar:

* `java:dev.tita.fixtures.Overloads`
* `java:dev.tita.fixtures.Generics`
* `java:dev.tita.fixtures.Derived`
* `java:dev.tita.fixtures.MyFn`
* `java:dev.tita.fixtures.Bean`
* `java:dev.tita.fixtures.mr.MrPick`

### 4.3 jrt:/ usage (must)

At least one scenario imports JDK class via `java:` that resolves through `jrt:/`:

* `java:java.util.Optional`
  or
* `java:java.lang.String`

And the TSJ run must accept a `--classpath jrt:/` (or equivalent) entry.

## 5) Execution Modes & Expected Outcomes

### 5.1 Shared mode (duplicates tolerated, mediated)

Config: `mode = shared`

* Build produces `<out>/class-index.json`
* If duplicates exist, index records winners + shadowed losers with rule metadata
* App still compiles and runs

**Acceptance checks (shared):**

* `class-index.json` exists and is deterministically ordered
* `duplicateCount > 0` if duplicates scenario is enabled
* Diagnostics (if any) include “winner reason” and list all origins

### 5.2 App-isolated mode (fail on app vs dep conflicts)

Config: `mode = app-isolated`

* Scenario `duplicates.tsj` intentionally creates same class in app output as in jar (see below)
* Compile fails fast with TSJ-RUN-009 (or your code) including both origins

**How to force conflict:**

* Put a TSJ-generated class (or compiled output) that emits `dev.tita.fixtures.Conflict` and also have that class in the jar.

**Acceptance checks (isolated):**

* Build fails with deterministic diagnostic ID
* Diagnostic includes actionable guidance (remove from app output or adjust deps)

## 6) Determinism & Artifact Expectations

On every compile/run:

* `<out>/class-index.json` persisted
* artifact metadata includes:

    * `interopClasspath.classIndex.path`
    * `interopClasspath.classIndex.symbolCount`
    * `interopClasspath.classIndex.duplicateCount`
* overload selections (where applicable) persisted as stable identities (owner/name/descriptor/invokeKind)

## 7) Minimal README Requirements

`README.md` must show exact commands:

### Shared mode run

* `tsj compile --config tsj.json`
* `tsj run --config tsj.json`

### App-isolated negative test

* `tsj compile --config tsj.isolated.json` (expected to fail)
* Mention expected diagnostic code and where to find class-index.json (if produced)

## 8) Success Criteria (what “done” means)

This app is “good” if:

* It **requires** `tita-fixtures-1.0.jar` to compile (no jar → compile errors for `java:` imports).
* It deterministically exercises at least **8** subsystems: indexing, parsing, caching, generics, nullability, inheritance/override, SAM/props, overload resolution, plus MR-JAR and jrt (those last two are extra but strongly recommended).
* It produces stable artifacts and stable diagnostics across repeated runs.

---

If you want, I can also give you:

* a **fixture-jar implementation checklist** (exact Java source signatures + manifest entries),
* and a **TSJ-side scenario skeleton** (filenames + which calls should be made in each `.tsj` file to force the resolver paths).
