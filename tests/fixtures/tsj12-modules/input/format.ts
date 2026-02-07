import { read } from "./counter.ts";
console.log("format-init");
export function display() {
  return "count=" + read();
}
