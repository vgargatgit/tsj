// XTTA Interop Torture: Exception Handling
import { riskyOperation } from "java:dev.xtta.complex.TortureLib";

// 1. Successful operation
const ok = riskyOperation(false);
console.log("success:" + (ok === "success"));

// 2. Failed operation caught
let caught = false;
try {
  riskyOperation(true);
} catch (e: any) {
  caught = true;
  console.log("exception_msg:" + (e.message !== null && e.message !== undefined));
}
console.log("exception_caught:" + caught);
