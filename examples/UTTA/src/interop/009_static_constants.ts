// UTTA Interop 009: Static Constants
// Tests accessing Java static final fields (constants)

import { VERSION, MAX_SIZE, PI_APPROX } from "java:dev.utta.Outer";
import { values, valueOf } from "java:dev.utta.Color";
import { $instance$isPrimary as isPrimary } from "java:dev.utta.Color";

// 1. String constant
console.log("str_const:" + (VERSION === "1.0.0"));

// 2. Int constant
console.log("int_const:" + (MAX_SIZE === 1024));

// 3. Double constant
console.log("dbl_const:" + (PI_APPROX > 3.14 && PI_APPROX < 3.15));

// 4. Multiple enum constants in one expression
const red = valueOf("RED");
const green = valueOf("GREEN");
const blue = valueOf("BLUE");
const allPrimary = isPrimary(red) && isPrimary(green) && isPrimary(blue);
console.log("enum_multi:" + (allPrimary === true));

// 5. Non-primary enum
const yellow = valueOf("YELLOW");
const yellowPrimary = isPrimary(yellow);
console.log("enum_false:" + (yellowPrimary === false));
