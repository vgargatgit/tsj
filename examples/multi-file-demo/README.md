# TSJ Multi-File Demo

This sample demonstrates a multi-file TypeScript project running through TSJ.

## Project Structure

- `src/main.ts`: entrypoint
- `src/bootstrap.ts`: side-effect module
- `src/math.ts`: loops + closure + numeric helpers
- `src/users.ts`: classes + inheritance + object-oriented flow
- `src/async-work.ts`: top-level `await`, async arrow, async object method, async control flow
- `src/promise-lab.ts`: Promise `catch`/`finally` + combinators (`all`, `race`, `allSettled`, `any`)

## Run with TSJ

```bash
mvn -B -ntp -pl cli -am exec:java \
  -Dexec.mainClass=dev.tsj.cli.TsjCli \
  -Dexec.args="run examples/multi-file-demo/src/main.ts --out examples/multi-file-demo/.tsj-build"
```

Expected program output lines:

```text
bootstrap:init
module:init=ready
sync:ready=ready
sync:total=15
sync:boosted=15
sync:account=6:12
sync:user=ada#30
sync:coerce=true:false
sync:missing=undefined
async:boost=12
async:series=12
promise:catch=boom
promise:finally
async:lab=3:win:2:ok
sync:done
```
