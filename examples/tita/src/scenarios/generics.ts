import { echo } from "java:dev.tita.fixtures.Generics";
import { $new as newBox,
$instance$set as boxSet,
$instance$get as boxGet }
from "java:dev.tita.fixtures.Generics$Box";

export function runGenericsScenario() {
  const echoed = echo("tsj");
  const box = newBox();
  boxSet(box, "boxed");
  const boxed = boxGet(box);

  let ok = true;
  if (echoed !== "tsj") {
    ok = false;
  }
  if (boxed !== "boxed") {
    ok = false;
  }

  if (ok) {
    console.log("GENERICS_OK");
  }
  return ok;
}
