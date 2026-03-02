// XTTA Grammar Torture: Generator Functions
// Tests function*, yield, yield*, iterator protocol

// 1. Basic generator
function* count() {
  yield 1;
  yield 2;
  yield 3;
}
const gen = count();
const r1 = gen.next();
const r2 = gen.next();
const r3 = gen.next();
const r4 = gen.next();
console.log("basic_gen:" + (r1.value === 1 && r2.value === 2 && r3.value === 3 && r4.done === true));

// 2. Generator with return
function* withReturn() {
  yield 1;
  return 42;
}
const gen2 = withReturn();
const a = gen2.next();
const b = gen2.next();
console.log("gen_return:" + (a.value === 1 && a.done === false && b.value === 42 && b.done === true));

// 3. Infinite generator
function* naturals() {
  let n = 1;
  while (true) {
    yield n++;
  }
}
const nat = naturals();
const first5: number[] = [];
for (let i = 0; i < 5; i++) {
  first5.push(nat.next().value as number);
}
console.log("infinite_gen:" + (first5.length === 5 && first5[4] === 5));

// 4. Generator with arguments to next()
function* accumulator() {
  let total = 0;
  while (true) {
    const val: number = yield total;
    total += val;
  }
}
const acc = accumulator();
acc.next();
acc.next(10);
const accResult = acc.next(20);
console.log("gen_next_arg:" + (accResult.value === 30));

// 5. for...of with generator
function* range(start: number, end: number) {
  for (let i = start; i < end; i++) {
    yield i;
  }
}
let rangeSum = 0;
for (const n of range(1, 6)) {
  rangeSum += n;
}
console.log("gen_forof:" + (rangeSum === 15));

// 6. yield* delegation
function* inner() {
  yield "a";
  yield "b";
}
function* outer() {
  yield "start";
  yield* inner();
  yield "end";
}
const all: string[] = [];
for (const v of outer()) {
  all.push(v);
}
console.log("yield_star:" + (all.length === 4 && all[0] === "start" && all[1] === "a" && all[3] === "end"));
