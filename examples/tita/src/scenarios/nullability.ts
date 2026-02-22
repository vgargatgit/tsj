import { maybeNull, require } from "java:dev.tita.fixtures.Overloads";

export function runNullabilityScenario() {
  const nullableValue = maybeNull(false);
  const requiredValue = require("safe");

  let ok = true;
  if (nullableValue !== null) {
    ok = false;
  }
  if (requiredValue !== "safe") {
    ok = false;
  }

  if (ok) {
    console.log("NULLABILITY_OK");
  }
  return ok;
}
