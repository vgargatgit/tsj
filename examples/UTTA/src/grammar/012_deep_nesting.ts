// UTTA Grammar 012: Deeply Nested Expressions
// Tests compiler handling of complex expression nesting

// 1. Deeply nested ternary
const v = 1;
const r1 = v === 1 ? "one" : v === 2 ? "two" : v === 3 ? "three" : v === 4 ? "four" : "other";
console.log("deep_ternary:" + (r1 === "one"));

// 2. Nested function calls (10 deep)
function add1(n: number): number { return n + 1; }
const r2 = add1(add1(add1(add1(add1(add1(add1(add1(add1(add1(0))))))))));
console.log("nested_call:" + (r2 === 10));

// 3. Complex arithmetic expression
const r3 = ((1 + 2) * (3 + 4)) / ((5 - 6) + (7 * 8)) - 1;
const expected3 = ((1 + 2) * (3 + 4)) / ((5 - 6) + (7 * 8)) - 1;
console.log("complex_arith:" + (Math.abs(r3 - expected3) < 0.001));

// 4. Chained method calls (long chain)
const r4 = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
  .filter(n => n % 2 === 0)
  .map(n => n * 10)
  .filter(n => n > 20)
  .map(n => String(n))
  .join(",");
console.log("long_chain:" + (r4 === "40,60,80,100"));

// 5. Nested array/object literals
const deep = { a: { b: [{ c: [1, [2, [3, [4]]]]}]}};
console.log("deep_literal:" + ((deep.a.b[0] as any).c[1][1][1][0] === 4));

// 6. Complex logical expression
const a = true, b = false, c = true, d = false;
const r6 = (a && !b) || (c && d) || (!a && b) || (a && c && !d);
console.log("complex_logic:" + (r6 === true));

// 7. String concatenation chain (20 parts)
let s = "";
for (let i = 0; i < 20; i++) s += String(i);
console.log("concat_chain:" + (s === "012345678910111213141516171819"));

// 8. Nested spread
const base = [1, 2];
const r8 = [...[...base, 3], ...[4, ...base]];
console.log("nested_spread:" + (r8.length === 7 && r8[0] === 1 && r8[6] === 2));
