import { pingA } from "./a.ts";
export function pingB() {
  return "B";
}
export function unused() {
  return pingA;
}
