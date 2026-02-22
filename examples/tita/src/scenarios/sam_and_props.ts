import { prefixer, runWithBuiltIn } from "java:dev.tita.fixtures.SamRunner";
import { $new as newBean, $instance$setTitle as setTitle, $instance$getTitle as getTitle, $instance$setReady as setReady, $instance$isReady as isReady, $instance$getURL as getURL, $instance$getUrl as getUrl } from "java:dev.tita.fixtures.Bean";
import { $new as newDerived, $instance$id as derivedId } from "java:dev.tita.fixtures.Derived";
import { $new as newCovariantDerived, $instance$v as covariantValue } from "java:dev.tita.fixtures.CovariantDerived";
import { $instance$apply as applySam, $instance$name as samName } from "java:dev.tita.fixtures.MyFn";

export function runSamAndPropsScenario() {
  const builtInResult = runWithBuiltIn("sam");
  const callback = prefixer();
  const samResult = applySam(callback, "sam");
  const callbackName = samName(callback);
  const bean = newBean();
  setTitle(bean, "pet-store");
  setReady(bean, true);

  const title = getTitle(bean);
  const ready = isReady(bean);
  const upperUrl = getURL(bean);
  const lowerUrl = getUrl(bean);

  const derived = newDerived();
  const inheritedValue = derivedId(derived, "child");

  const covariant = newCovariantDerived();
  const covariantResult = covariantValue(covariant);

  let samOk = true;
  if (builtInResult !== "sam-ok") {
    samOk = false;
  }
  if (samResult !== "sam-ok") {
    samOk = false;
  }
  if (callbackName !== "fn") {
    samOk = false;
  }
  if (samOk) {
    console.log("SAM_OK");
  }

  let propsOk = true;
  if (title !== "pet-store") {
    propsOk = false;
  }
  if (ready !== true) {
    propsOk = false;
  }
  if (upperUrl !== "URL") {
    propsOk = false;
  }
  if (lowerUrl !== "url") {
    propsOk = false;
  }
  if (propsOk) {
    console.log("PROPS_OK");
  }

  let inheritanceOk = true;
  if (inheritedValue !== "child") {
    inheritanceOk = false;
  }
  if (covariantResult !== 7) {
    inheritanceOk = false;
  }
  if (inheritanceOk) {
    console.log("INHERITANCE_OK");
  }

  if (!samOk) {
    return false;
  }
  if (!propsOk) {
    return false;
  }
  return inheritanceOk;
}
