# TSJ Fixture Format (v0.1)

TSJ-3 fixtures are directory-based and driven by `fixture.properties`.

## Directory layout

```text
tests/fixtures/<fixture-name>/
  fixture.properties
  input/
    main.ts
  expected/
    node.stdout
    node.stderr
    tsj.stdout
    tsj.stderr
```

## `fixture.properties`

Required keys:
1. `name`
2. `entry` (path relative to fixture directory)
3. `expected.node.exitCode`
4. `expected.node.stdout`
5. `expected.node.stderr`
6. `expected.tsj.exitCode`
7. `expected.tsj.stdout`
8. `expected.tsj.stderr`

Optional keys:
1. `expected.node.stdoutMode` (`exact` or `contains`, default `exact`)
2. `expected.node.stderrMode` (`exact` or `contains`, default `exact`)
3. `expected.tsj.stdoutMode` (`exact` or `contains`, default `exact`)
4. `expected.tsj.stderrMode` (`exact` or `contains`, default `exact`)
5. `assert.nodeMatchesTsj` (`true` or `false`, default `false`)

## Harness behavior
1. Node runtime is executed with:
   - `node --experimental-strip-types <entry.ts>`
2. TSJ runtime is executed via CLI bootstrap path:
   - `tsj run <entry.ts> --out <fixture>/.tsj-out`
3. Harness compares each runtime output against expected files.
4. If `assert.nodeMatchesTsj=true`, harness also enforces direct output equality between Node and TSJ.
5. For direct comparison, TSJ diagnostic JSON lines are ignored so semantic output can be compared to Node output.

## CLI entrypoint

Run all fixtures in a root directory:

```bash
tsj fixtures tests/fixtures
```

The command emits structured diagnostics:
1. `TSJ-FIXTURE-PASS`
2. `TSJ-FIXTURE-FAIL`
3. `TSJ-FIXTURE-SUMMARY`
