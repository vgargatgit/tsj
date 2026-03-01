// UTTA Interop 001: Java Enums
// Tests accessing Java enum values, methods, and enum operations

import { values, valueOf, fromHex } from "java:dev.utta.Color";

// 1. Access enum values array
const all = values();
console.log("enum_values:" + (all.length === 4));

// 2. Get enum by name
const red = valueOf("RED");
console.log("enum_val:" + (red !== null && red !== undefined));

// 3. Static method
const found = fromHex("00FF00");
console.log("enum_static:" + (found !== null));

// 4. Instance methods on enum value
import { $instance$getHex as getHex, $instance$isPrimary as isPrimary, $instance$name as eName, $instance$ordinal as ordinal } from "java:dev.utta.Color";
const hex = getHex(red);
console.log("enum_hex:" + (hex === "FF0000"));

// 5. Enum instance method
const prim = isPrimary(red);
console.log("enum_primary:" + (prim === true));

// 6. Enum name()
const name = eName(red);
console.log("enum_name:" + (name === "RED"));

// 7. Enum ordinal()
const green = valueOf("GREEN");
const ord = ordinal(green);
console.log("enum_ordinal:" + (ord === 1));
