import { pick, $new as newOverloads, $instance$join as join } from "java:dev.tita.fixtures.Overloads";

export function runOverloadsScenario() {
  const instance = newOverloads();
  const intPick = pick(7);
  const stringPick = pick("hello");
  const stringJoin = join(instance, "left", "right");
  const objectJoin = join(instance, 1, 2);

  let ok = true;
  if (intPick === null) {
    ok = false;
  }
  if (intPick === undefined) {
    ok = false;
  }
  if (stringPick === null) {
    ok = false;
  }
  if (stringPick === undefined) {
    ok = false;
  }
  if (stringJoin === null) {
    ok = false;
  }
  if (stringJoin === undefined) {
    ok = false;
  }
  if (objectJoin === null) {
    ok = false;
  }
  if (objectJoin === undefined) {
    ok = false;
  }

  if (ok) {
    console.log("OVERLOAD_OK");
  }
  return ok;
}
