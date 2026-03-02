# TNTA — TSJ Numeric Torture App Spec (Double-Coerce + Int32 Narrowing) + Top-20 Bug Checklist

## 0) Context / Target Runtime Model (current TSJ)

TNTA is designed for TSJ’s current numeric execution model:

* All numeric ops coerce through **double**:

    * `TsjRuntime.toNumber(...)` returns `double`
    * `+`, `-`, `*` compute via `double` then call narrowing
    * `/` and `%` return `Double` directly
* A small integer narrowing step exists:

    * `narrowNumber(...)` returns `Integer` iff value is integral and within 32-bit range; else `Double`
* Numeric literals may be emitted as `Integer`/`Long`/`Double`, but arithmetic still routes through **boxed runtime helpers**.

TNTA must validate **JS semantics** and detect regressions caused by narrowing and boxing.

---

## 1) Goals

1. **Correctness parity with Node** for JS numeric semantics:

    * NaN, ±Infinity, -0 behavior
    * coercions (`+`, `Number(x)`, `"5"-2`, etc.)
    * bitwise ToInt32/ToUint32 behavior
    * `Math.imul` correctness
    * formatting gotchas like `toFixed`
2. Catch the most common bugs introduced by:

    * double compute + int32 narrowing (`narrowNumber`)
    * boxed runtime helper calls
    * literal emission as Integer/Long/Double
3. Provide **perf telemetry** for boxed numeric hot paths (not strict pass/fail except catastrophic).

---

## 2) Repo Layout

```text
tnta/
  README.md
  tnta.json

  src/
    main.ts
    harness/
      runner.ts
      assert.ts
      rng.ts
      report.ts
      baseline_io.ts

    suites/
      010_narrowing_boundary.ts
      020_literals_and_identity.ts
      030_div_mod_edge.ts
      040_negzero_nan_inf.ts
      050_bitwise_toint32.ts
      060_math_imul_fround.ts
      070_coercions.ts
      100_formatting.ts
      080_property_fuzz.ts
      300_microbench_boxed.ts

  expected/
    node-baseline.json
```

---

## 3) Execution

### 3.1 Generate Node baseline (one-time or when updating)

```bash
node tnta/src/main.ts --mode baseline --out tnta/expected/node-baseline.json
```

### 3.2 Run under TSJ and compare to baseline

(Assume TSJ forwards program args after `--`. If not supported, hardcode baseline path in TS.)

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run tnta/src/main.ts --out tnta/build -- --mode compare --baseline tnta/expected/node-baseline.json"
```

---

## 4) Output Contract (Stable)

Program prints exactly one final JSON line to stdout:

```json
{"suite":"tnta","mode":"compare","seed":123,"passed":13245,"failed":0,"skipped":2,"durationMs":456,"perf":{"addOpsPerSec":0,"imulOpsPerSec":0},"failures":[]}
```

* `failed > 0` ⇒ exit code `1`, else `0`
* Each failure item must contain:

    * `suite`, `caseId`, `input`, `expected`, `actual`, `comparison`, `note`
    * Also record `typeofExpected` and `typeofActual` when possible.

---

## 5) Comparison Rules (JS-correct)

Use these utilities in `harness/assert.ts`:

* `objectIs(a,b)` — same semantics as JS `Object.is`
* `eq(a,b)` — default equality uses `Object.is` for numbers; `===` for strings/booleans; deep compare for arrays/objects (small)
* `approx(a,b,eps)` — only for trig/hypot/fround; never for core arithmetic
* `expectThrows(fn, expectedName?)` — for BigInt mixing (if included later)
* For `-0`/`NaN` cases, **always** compare using `Object.is`.

---

## 6) Harness Design

### 6.1 Modes

* `--mode baseline`: run all suites, output a baseline JSON file (`--out`).
* `--mode compare`: load baseline (`--baseline`) and compare suite results case-by-case; output final report JSON.

### 6.2 Test Case Model

Each suite exports `runSuite(ctx) => SuiteResult`.

Each case:

* `caseId: string`
* `compute(): any` (value or throws)
* `expected`: computed in baseline mode and stored
* `comparison`: `"Object.is" | "===" | "approx"`
* `note`: short string referencing likely bug category
* `input`: small JSON-serializable payload (numbers/strings)

Baseline stores:

* `caseId`
* `expectedValue` or `{ throws: { name, message? } }`
* `typeofExpected`
* `comparison`
* `tolerance` (if approx)
* `note`

Compare mode recomputes `actual` and compares using stored comparison rule.

---

## 7) Suites (must implement) + Required Case IDs

### 7.1 `010_narrowing_boundary.ts`

Purpose: stress `narrowNumber()` boundary and integral detection.

Cases (Object.is comparison unless noted):

* `NARROW-01`: `2147483647 + 0`
* `NARROW-02`: `2147483648 + 0`
* `NARROW-03`: `-2147483648 + 0`
* `NARROW-04`: `-2147483649 + 0`
* `NARROW-05`: `46341 * 46341` (just above 2^31)
* `NARROW-06`: `65536 * 65536` (2^32)
* `NARROW-07`: `1.1 + 2.2` (non-integral)
* `NARROW-08`: `0.1 + 0.2` (must be `0.30000000000000004`)
* `NEGZERO-01`: `(-0) + 0` (must stay `-0`) — Object.is
* `NEGZERO-02`: `Math.min(0, -0)` (must be `-0`) — Object.is

Notes:

* These are the fastest indicators of narrowing bugs.

---

### 7.2 `020_literals_and_identity.ts`

Purpose: ensure literal emission as Integer/Long/Double doesn’t change JS behavior.

Cases:

* `LIT-01`: `2147483647`
* `LIT-02`: `2147483648`
* `LIT-03`: `9007199254740991` (2^53 - 1)
* `LIT-04`: `9007199254740992` (2^53)
* `LIT-05`: `(2 ** 53) + 1` (precision loss check; compare to Node)
* `LIT-06`: `1e309` (Infinity)
* `LIT-07`: `1e-324` (subnormal edge; compare to Node)
* `LIT-08`: `(2147483648).toString()` — string equality
* `LIT-09`: `(9007199254740992).toString()` — string equality

---

### 7.3 `030_div_mod_edge.ts`

Purpose: `/` and `%` are Double-returning; validate edge cases.

Cases:

* `DIV-01`: `0 / 0` → NaN (Object.is)
* `DIV-02`: `1 / 0` → Infinity
* `DIV-03`: `1 / -0` → -Infinity (Object.is)
* `MOD-01`: `5 % 2` → 1
* `MOD-02`: `-5 % 2` → -1
* `MOD-03`: `5 % -2` → 1
* `MOD-04`: `-5 % -2` → -1
* `MOD-05`: `5 % Infinity` → 5
* `MOD-06`: `Infinity % 5` → NaN (Object.is)

---

### 7.4 `040_negzero_nan_inf.ts`

Purpose: special-value semantics.

Cases:

* `NAN-01`: `Number.isNaN(NaN)` → true
* `NAN-02`: `NaN !== NaN` → true
* `NAN-03`: `Object.is(NaN, NaN)` → true
* `NEGZERO-03`: `Object.is(-0, 0)` → false
* `NEGZERO-04`: `Math.sign(-0)` must be `-0` (Object.is)
* `NEGZERO-05`: `1 / (-0)` is `-Infinity` (Object.is)

---

### 7.5 `050_bitwise_toint32.ts`

Purpose: validate ToInt32/ToUint32 and shift semantics.

Cases:

* `BIT-01`: `(0xFFFFFFFF | 0)` → -1
* `BIT-02`: `(-1 >>> 0)` → 4294967295
* `BIT-03`: `(1 << 31)` → -2147483648
* `BIT-04`: `(1 << 32)` → 1 (shift count masked)
* `BIT-05`: `(~0)` → -1
* `BIT-06`: `(2147483648 | 0)` → -2147483648
* `BIT-07`: `(123456789 >>> 0)` equals Node baseline
* `BIT-08`: `((-2147483648) >>> 0)` → 2147483648

---

### 7.6 `060_math_imul_fround.ts`

Purpose: `Math.imul` correctness; `Math.fround` best-effort.

Cases:

* `IMUL-01`: `Math.imul(0xffffffff, 5)` → -5
* `IMUL-02`: `Math.imul(0x7fffffff, 2)` → -2
* `IMUL-03`: `Math.imul(123456789, 987654321)` baseline compare
* `IMUL-04`: random fixed-seed 100 pairs; each compared to Node baseline results

Optional (may be skipped if not supported; must report `skipped` with reason):

* `FROUND-01`: `Math.fround(1.337)` baseline compare (approx or exact float32 if implemented)
* `FROUND-02`: `Math.fround(1e40)` baseline compare

---

### 7.7 `070_coercions.ts`

Purpose: stress `toNumber` and `+` string concat rules.

Cases:

* `COERCE-PLUS-01`: `"5" + 2` → "52" (string)
* `COERCE-NUM-01`: `"5" - 2` → 3
* `COERCE-NUM-02`: `"5" * "2"` → 10
* `COERCE-NUM-03`: `"5" / 2` → 2.5
* `COERCE-NUM-04`: `+" \n\t 1 "` → 1
* `COERCE-NUM-05`: `Number("")` → 0
* `COERCE-NUM-06`: `Number("   ")` → 0
* `COERCE-NUM-07`: `Number("0x10")` → 16
* `COERCE-NUM-08`: `parseInt("08", 10)` → 8
* `COERCE-NUM-09`: `Number("x")` → NaN (Object.is with NaN)

---

### 7.8 `100_formatting.ts`

Purpose: formatting gotchas differ between JS and Java; catch regressions.

Cases (string comparisons):

* `FMT-01`: `(1.005).toFixed(2)` (must match Node baseline exactly)
* `FMT-02`: `(0).toFixed(0)` → "0"
* `FMT-03`: `(10).toFixed(2)` → "10.00"
* `FMT-04`: `(1e21).toString()` baseline compare (JS switches formatting rules)
* `FMT-05`: `JSON.stringify(NaN)` → "null"
* `FMT-06`: `JSON.stringify(Infinity)` → "null"

---

### 7.9 `080_property_fuzz.ts`

Purpose: deterministic property-based smoke tests in safe numeric ranges (exact in double).

Config from `tnta.json`:

* `seed`
* `trialsInt` (e.g., 10000)
* `trialsFloat` (e.g., 5000)

Properties:

* `PROP-INT-01`: for ints a,b in [-1e6,1e6], `a + b === b + a`
* `PROP-INT-02`: for ints a,b,c, `a*(b+c) === a*b + a*c`
* `PROP-INT32-01`: `((x|0) >>> 0) === (x >>> 0)` for random x in safe range
* `PROP-FLOAT-01`: for floats x != 0, `x / x` approx 1 (eps in config)
* `PROP-FLOAT-02`: `Math.abs(x) >= 0` (exact)

On first failure, record:

* seed, iteration, inputs, expected/actual

---

### 7.10 `300_microbench_boxed.ts`

Purpose: perf telemetry for boxed helper paths; not strict pass/fail.

Benchmarks (counts in config; default large but not absurd):

* `PERF-ADD`: loop `x = x + 1` N times
* `PERF-MUL`: loop `x = x * 1.0000001` N times
* `PERF-BIT`: loop `x = (x|0) + 1` N times
* `PERF-IMUL`: loop `x = Math.imul(x|0, 1664525)` N times

Report:

* ops/sec per benchmark
* include in final report under `perf`

Optional catastrophic threshold (very low, config-driven):

* If ops/sec below threshold, mark as failure with `caseId=PERF-LOW-*` (default disabled).

---

## 8) Top-20 Bug Checklist Mapping (must be represented)

The suite must include cases that detect these bug classes; case IDs are already included above.

1. **-0 lost in narrowing** → `NEGZERO-01`
2. **Math.min(0,-0) wrong** → `NEGZERO-02`
3. **negative % sign wrong** → `MOD-02` / `MOD-04`
4. **Infinity % edge wrong** → `MOD-05` / `MOD-06`
5. **0/0 not NaN** → `DIV-01`
6. **1/-0 not -Infinity** → `DIV-03` / `NEGZERO-05`
7. **NaN comparisons wrong** → `NAN-02` / `NAN-03`
8. **string + uses numeric add** → `COERCE-PLUS-01`
9. **whitespace ToNumber wrong** → `COERCE-NUM-04`
10. **Number("") wrong** → `COERCE-NUM-05`
11. **hex parse wrong** → `COERCE-NUM-07`
12. **ToInt32 wrong near 2^31** → `BIT-06`
13. **shift count masking missing** → `BIT-04`
14. **>>> unsigned wrong** → `BIT-02`
15. **Math.imul wrong** → `IMUL-01` / `IMUL-02`
16. **incorrect narrowing above boundary** → `NARROW-02`
17. **integral detection with FP rounding** → `LIT-05` + baseline compare
18. **exponentiation right-assoc** → include:

    * `POW-01`: `2 ** 3 ** 2` (add to `010_narrowing_boundary.ts` or new tiny suite)
19. **unary minus with ** grammar** → include:

    * `POW-02`: ensure `-2 ** 2` matches Node behavior (likely SyntaxError). If TSJ/TS parser forbids, capture as an expected throw in baseline.
20. **toFixed/toPrecision mismatch** → `FMT-01`

**Add these two cases to the suite set explicitly:**

* `POW-01`: `2 ** 3 ** 2`
* `POW-02`: evaluating `-2 ** 2` must match Node (expected SyntaxError). Implement as `compute()` that uses `eval`? If TSJ forbids eval (TSJ-15), then instead make `POW-02` a harness-only Node baseline check and mark skipped in TSJ with explicit reason. Preferred approach:

    * Place `POW-02` behind a config flag `allowEvalCase=false` default; keep it off for TSJ runs.

(If you don’t want any eval at all, drop `POW-02` and keep `POW-01` only.)

---

## 9) Config: `tnta.json`

Fields:

* `seed`: number
* `trialsInt`: number
* `trialsFloat`: number
* `floatEps`: number (e.g., 1e-12)
* `bench`:

    * `addIters`, `mulIters`, `bitIters`, `imulIters`
    * `failOnPerfBelow`: boolean (default false)
    * `minOpsPerSec`: number (only if failOnPerfBelow=true)
* `features`:

    * `includeFround`: boolean default true (suite may skip if missing)
    * `includePowSyntaxEvalCase`: boolean default false

---

## 10) README (aligned with TSJ Quickstart)

### Generate baseline

```bash
node tnta/src/main.ts --mode baseline --out tnta/expected/node-baseline.json
```

### Run under TSJ and compare

```bash
mvn -B -ntp -f cli/pom.xml exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run tnta/src/main.ts --out tnta/build -- --mode compare --baseline tnta/expected/node-baseline.json"
```

---

## 11) Implementation Notes / Non-Goals

* TNTA must avoid “non-deterministic” checks.
* Prefer baseline compare for any case where JS behavior is subtle (formatting, subnormals).
* No external jars required (this is numeric semantics), but jar usage can be added later.
* Do not depend on TSX/JSX.
* Do not require TSJ to implement full JS; only numeric and coercion semantics should be under test. If TSJ currently lacks a feature required for a test, it must be **skipped with explicit reason** and counted in `skipped`.

---

## 12) Definition of Done

TNTA is complete when:

* Node baseline generation works and produces deterministic `expected/node-baseline.json`.
* TSJ compare mode runs without crash and reports failures precisely with case IDs.
* All Top-20 bug classes are covered by explicit case IDs.
* Perf telemetry is printed in final report JSON.

---
