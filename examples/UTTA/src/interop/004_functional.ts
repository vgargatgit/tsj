// UTTA Interop 004: Java Functional Interfaces
// Tests Java streams/Optional without passing TS callbacks (not yet supported)

import { streamPipeline, safeDiv } from "java:dev.utta.FuncLib";

// 1. Stream pipeline (no callback — pure Java side)
const pipelineResult = streamPipeline([3, -1, 5, 2, -4, 8]);
console.log("pipeline:" + (pipelineResult === "4,6,10,16"));

// 2. safeDiv with Optional
const result = safeDiv(10, 3);
import { $instance$isPresent as isPresent, $instance$get as optGet, $instance$orElse as orElse } from "java:java.util.Optional";
console.log("optional_present:" + (isPresent(result) === true));
console.log("optional_val:" + (optGet(result) === 3));

// 3. safeDiv returning empty Optional
const empty = safeDiv(10, 0);
console.log("optional_empty:" + (isPresent(empty) === false));

// 4. Optional orElse
const fallback = orElse(empty, -1);
console.log("optional_orelse:" + (fallback === -1));
