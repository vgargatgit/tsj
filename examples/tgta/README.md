# TS Grammar Torture App (TGTA)

TGTA is a standalone TypeScript grammar torture app.
It validates parsing behavior with deterministic golden snapshots.

## Layout

```text
examples/tgta/
  README.md
  package.json
  tsj.json
  src/
    entry.ts
    harness/
      expect.ts
      snapshot.ts
      parse_harness.ts
      *.test.ts
    ok/
    err/
  fixtures/
    expected/
      ok/*.ast.json
      err/*.diag.json
```

## Parse Harness Commands

Run parse verification:

```bash
node --experimental-strip-types examples/tgta/src/harness/parse_harness.ts
```

Regenerate snapshots:

```bash
node --experimental-strip-types examples/tgta/src/harness/parse_harness.ts --update
```

Run harness unit tests:

```bash
node --experimental-strip-types --test examples/tgta/src/harness/*.test.ts
```

## Exit Codes

- `0`: all files matched expected parse status and snapshots.
- `1`: snapshot mismatch.
- `2`: unexpected parse success/failure.
- `3`: harness internal error.

## Snapshot Contract

- Every file in `src/ok` has one AST snapshot in `fixtures/expected/ok`.
- Every file in `src/err` has one diagnostics snapshot in `fixtures/expected/err`.
- Add a new grammar feature by adding one source file and one snapshot.
- Paths are normalized to repo-relative forward-slash form.
- Line endings are normalized to LF before comparison.

## Spec Command Mapping

The spec names `tsj test --suite parse` and `tsj test --suite parse --update`.
In this standalone app, the equivalent commands are the two Node commands above.
