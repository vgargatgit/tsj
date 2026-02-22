import { marker as conflictMarker } from "java:dev.tita.fixtures.Conflict";

export function runDuplicatesScenario() {
  const marker = conflictMarker("dup");
  let ok = true;
  if (marker !== "dup:fixture") {
    ok = false;
  }
  return ok;
}
