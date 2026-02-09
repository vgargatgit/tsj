const local = 2;
import { value as v } from "./a.ts";
export function readB() {
  return local + v;
}
