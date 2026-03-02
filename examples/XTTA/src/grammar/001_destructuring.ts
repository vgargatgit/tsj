// XTTA Grammar Torture: Destructuring
// Tests object destructuring, array destructuring, nested, defaults, rest, rename

// 1. Basic object destructuring
const obj = { a: 1, b: 2, c: 3 };
const { a, b } = obj;
console.log("obj_basic:" + (a === 1 && b === 2));

// 2. Object destructuring with rename
const { a: x, b: y } = obj;
console.log("obj_rename:" + (x === 1 && y === 2));

// 3. Object destructuring with defaults
const { d = 99, a: aa } = obj as any;
console.log("obj_default:" + (d === 99 && aa === 1));

// 4. Basic array destructuring
const arr = [10, 20, 30];
const [first, second] = arr;
console.log("arr_basic:" + (first === 10 && second === 20));

// 5. Array destructuring with skip
const [, , third] = arr;
console.log("arr_skip:" + (third === 30));

// 6. Array destructuring with rest
const [head, ...tail] = arr;
console.log("arr_rest:" + (head === 10 && tail.length === 2));

// 7. Nested destructuring
const nested = { outer: { inner: 42 } };
const { outer: { inner } } = nested;
console.log("nested:" + (inner === 42));

// 8. Destructuring in function parameters
function destruct({ name, age }: { name: string; age: number }) {
  return name + ":" + age;
}
console.log("fn_param:" + (destruct({ name: "test", age: 25 }) === "test:25"));

// 9. Array destructuring in function params
function sum([a2, b2]: number[]) {
  return a2 + b2;
}
console.log("fn_arr_param:" + (sum([3, 4]) === 7));

// 10. Swap via destructuring
let sw1 = 1, sw2 = 2;
[sw1, sw2] = [sw2, sw1];
console.log("swap:" + (sw1 === 2 && sw2 === 1));
