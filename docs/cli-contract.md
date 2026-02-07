# TSJ CLI Contract (Bootstrap v0.1)

## Commands

### `tsj compile <input.ts> --out <dir>`
Behavior:
1. Validates input file exists and has `.ts`/`.tsx` extension.
2. Creates output directory if missing.
3. Emits artifact file:
   - `<out>/program.tsj.properties`
4. Emits structured JSON diagnostics to stdout/stderr.

Success diagnostic:
- Code: `TSJ-COMPILE-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-003` missing required `--out`
- `TSJ-COMPILE-001` input file not found
- `TSJ-COMPILE-002` unsupported input extension
- `TSJ-COMPILE-500` artifact write error

### `tsj run <entry.ts> [--out <dir>]`
Behavior:
1. Compiles entry to artifact (default out dir `.tsj-build` when omitted).
2. Reads generated artifact.
3. Executes bootstrap runtime path.
4. Emits structured JSON diagnostics.

Success diagnostic:
- Code: `TSJ-RUN-SUCCESS`

Failure diagnostics:
- `TSJ-CLI-004` missing entry path
- `TSJ-RUN-001` artifact read error
- compile-phase failure codes from `tsj compile`

### `tsj fixtures <fixturesRoot>`
Behavior:
1. Loads all fixture directories under `<fixturesRoot>`.
2. Runs each fixture with Node and TSJ.
3. Compares outputs with fixture expectations.
4. Emits per-fixture diagnostics plus a summary.

Success diagnostics:
- `TSJ-FIXTURE-PASS`
- `TSJ-FIXTURE-SUMMARY`

Failure diagnostics:
- `TSJ-FIXTURE-FAIL`
- `TSJ-FIXTURE-001` fixture load error
- `TSJ-FIXTURE-002` no fixture directories

## Diagnostic Shape
All diagnostics use one-line JSON objects:

```json
{
  "level": "INFO|ERROR",
  "code": "TSJ-*",
  "message": "human-readable message",
  "context": {
    "key": "value"
  }
}
```
