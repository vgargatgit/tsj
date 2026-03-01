// UTTA Grammar 003: BigInt
// Tests BigInt literals and operations

// 1. BigInt literal
const big = 9007199254740993n;
console.log("literal:" + (typeof big === "bigint"));

// 2. BigInt arithmetic
const a = 100n;
const b = 200n;
console.log("add:" + (a + b === 300n));
console.log("mul:" + (a * b === 20000n));
console.log("sub:" + (b - a === 100n));
console.log("div:" + (b / a === 2n));
console.log("mod:" + (201n % 100n === 1n));
console.log("exp:" + (2n ** 10n === 1024n));

// 3. BigInt comparison
console.log("lt:" + (a < b));
console.log("eq:" + (100n === 100n));
console.log("neq:" + (100n !== 200n));

// 4. BigInt from constructor
const c = BigInt(42);
console.log("ctor:" + (c === 42n));

// 5. BigInt string conversion
console.log("to_str:" + (String(42n) === "42"));

// 6. Negative BigInt
const neg = -100n;
console.log("neg:" + (neg === -100n));

// 7. Large BigInt arithmetic (beyond Number.MAX_SAFE_INTEGER)
const huge = 2n ** 64n;
console.log("large:" + (huge > 0n));
