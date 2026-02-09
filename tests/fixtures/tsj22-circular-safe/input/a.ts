import { pingB } from "./b.ts";
export function pingA() {
  return "A";
}
export function callB() {
  return pingB();
}
