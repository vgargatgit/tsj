import { marker as mrMarker } from "java:dev.tita.fixtures.mr.MrPick";
import { valueOf as stringValueOf } from "java:java.lang.String";

export function runModulesAndJrtScenario() {
  const mrValue = mrMarker("mr");
  const jrtValue = stringValueOf(123);

  let mrOk = true;
  if (mrValue !== "mr:v11") {
    mrOk = false;
  }
  if (mrOk) {
    console.log("MRJAR_OK");
  }

  let jrtOk = true;
  if (jrtValue !== "123") {
    jrtOk = false;
  }
  if (jrtOk) {
    console.log("JRT_OK");
  }

  if (!mrOk) {
    return false;
  }
  return jrtOk;
}
