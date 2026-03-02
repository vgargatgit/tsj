// XTTA Grammar Torture: Async Edge Cases
// Tests async generators, Promise combinators, async IIFE, error handling

// 1. Async IIFE
const aiife = await (async () => {
  return 42;
})();
console.log("async_iife:" + (aiife === 42));

// 2. Promise.resolve chain
const chain = await Promise.resolve(1).then(n => n + 1).then(n => n * 2);
console.log("promise_chain:" + (chain === 4));

// 3. Async function returning promise
async function asyncDouble(n: number): Promise<number> {
  return n * 2;
}
const doubled = await asyncDouble(21);
console.log("async_return:" + (doubled === 42));

// 4. Async error handling
let caught = false;
try {
  await Promise.reject(new Error("boom"));
} catch (e: any) {
  caught = e.message === "boom";
}
console.log("async_catch:" + caught);

// 5. Multiple awaits in sequence
async function sequential(): Promise<number> {
  const a = await Promise.resolve(1);
  const b = await Promise.resolve(2);
  const c = await Promise.resolve(3);
  return a + b + c;
}
console.log("async_seq:" + (await sequential() === 6));

// 6. Async with conditional
async function conditionalAsync(flag: boolean): Promise<string> {
  if (flag) {
    return await Promise.resolve("yes");
  }
  return "no";
}
console.log("async_cond:" + (await conditionalAsync(true) === "yes" && await conditionalAsync(false) === "no"));

// 7. Promise.all
const results = await Promise.all([
  Promise.resolve(1),
  Promise.resolve(2),
  Promise.resolve(3)
]);
console.log("promise_all:" + (results[0] === 1 && results[1] === 2 && results[2] === 3));

// 8. Nested async calls
async function a_fn(): Promise<number> { return 1; }
async function b_fn(): Promise<number> { return (await a_fn()) + 1; }
async function c_fn(): Promise<number> { return (await b_fn()) + 1; }
console.log("nested_async:" + (await c_fn() === 3));
