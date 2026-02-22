import { runOverloadsScenario } from "./scenarios/overloads.ts";
import { runGenericsScenario } from "./scenarios/generics.ts";
import { runNullabilityScenario } from "./scenarios/nullability.ts";
import { runSamAndPropsScenario } from "./scenarios/sam_and_props.ts";
import { runModulesAndJrtScenario } from "./scenarios/modules_jrt.ts";
import { runDuplicatesScenario } from "./scenarios/duplicates.ts";

let ok = true;

if (!runOverloadsScenario()) {
  ok = false;
}
if (!runGenericsScenario()) {
  ok = false;
}
if (!runNullabilityScenario()) {
  ok = false;
}
if (!runSamAndPropsScenario()) {
  ok = false;
}
if (!runModulesAndJrtScenario()) {
  ok = false;
}
if (!runDuplicatesScenario()) {
  ok = false;
}

if (ok) {
  console.log("TITA_OK");
} else {
  console.log("TITA_FAIL");
}
