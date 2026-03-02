// XTTA Interop Torture: Arrays and Null
import { createIntArray, arraySum, nullSafe, createStringArray } from "java:dev.xtta.complex.TortureLib";

// 1. Create int array
const intArr = createIntArray(5);
console.log("int_array:" + (intArr !== null));

// 2. Array sum
const asum = arraySum(intArr);
console.log("array_sum:" + (asum === 100));

// 3. String array
const strArr = createStringArray("x", "y", "z");
console.log("str_array:" + (strArr !== null));

// 4. Null-safe operation
const nullResult = nullSafe(null as any);
console.log("null_safe:" + (nullResult === "was-null"));

// 5. Non-null operation
const upperResult = nullSafe("hello");
console.log("non_null:" + (upperResult === "HELLO"));
