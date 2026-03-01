// UTTA Interop 008: Custom Exception Hierarchies
// Tests catching and inspecting Java exception subclasses from TS

import { riskyOperation, dispatch } from "java:dev.utta.AppException";

// 1. Call that succeeds
const ok = riskyOperation(false);
console.log("success:" + (ok === "success"));

// 2. Call that throws ValidationException
try {
  riskyOperation(true);
  console.log("throw:false");
} catch (e: any) {
  console.log("throw:" + (e !== null));
}

// 3. Dispatch that succeeds
const r0 = dispatch(0);
console.log("dispatch_ok:" + (r0 === "ok"));

// 4. Dispatch ValidationException
try {
  dispatch(1);
  console.log("dispatch_val:false");
} catch (e: any) {
  console.log("dispatch_val:" + (e !== null));
}

// 5. Dispatch NotFoundException
try {
  dispatch(2);
  console.log("dispatch_nf:false");
} catch (e: any) {
  console.log("dispatch_nf:" + (e !== null));
}

// 6. Dispatch generic AppException
try {
  dispatch(3);
  console.log("dispatch_gen:false");
} catch (e: any) {
  console.log("dispatch_gen:" + (e !== null));
}
