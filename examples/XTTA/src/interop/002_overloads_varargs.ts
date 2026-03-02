// XTTA Interop Torture: Complex Overloads and Varargs
import { identify, sumVarargs, joinVarargs } from "java:dev.xtta.complex.TortureLib";

// 1. Int overload
const intResult = identify(42);
console.log("overload_int:" + (intResult !== null));

// 2. String overload
const strResult = identify("hello");
console.log("overload_str:" + (strResult !== null));

// 3. Varargs sum
const vsum = sumVarargs(1, 2, 3, 4, 5);
console.log("varargs_sum:" + (vsum === 15));

// 4. Varargs with separator
const joined = joinVarargs("-", "a", "b", "c");
console.log("varargs_join:" + (joined === "a-b-c"));
