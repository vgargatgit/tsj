// UTTA Grammar 010: Logical Assignment Operators
// Tests &&=, ||=, ??= in various contexts

// 1. Nullish assignment (??=) with null
let a: any = null;
a ??= 42;
console.log("nullish_null:" + (a === 42));

// 2. Nullish assignment with existing value
let b: any = 10;
b ??= 42;
console.log("nullish_exists:" + (b === 10));

// 3. Nullish assignment with undefined
let c: any = undefined;
c ??= "hello";
console.log("nullish_undef:" + (c === "hello"));

// 4. Logical OR assignment (||=) with falsy
let d: any = 0;
d ||= 99;
console.log("or_falsy:" + (d === 99));

// 5. Logical OR with truthy
let e: any = 5;
e ||= 99;
console.log("or_truthy:" + (e === 5));

// 6. Logical AND assignment (&&=) with truthy
let f: any = 1;
f &&= 42;
console.log("and_truthy:" + (f === 42));

// 7. Logical AND with falsy
let g: any = 0;
g &&= 42;
console.log("and_falsy:" + (g === 0));

// 8. Logical assignment on object properties
const obj: any = { x: null, y: 10 };
obj.x ??= 5;
obj.y ??= 99;
console.log("obj_nullish:" + (obj.x === 5 && obj.y === 10));

// 9. Chained logical assignments
let h: any = null;
h ??= undefined;
h ??= 0;
h ||= 42;
console.log("chain:" + (h === 42));

// 10. Logical assignment with empty string
let s: any = "";
s ||= "default";
console.log("empty_str:" + (s === "default"));

s = "";
s ??= "default";
console.log("empty_nullish:" + (s === ""));
