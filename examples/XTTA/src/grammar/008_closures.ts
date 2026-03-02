// XTTA Grammar Torture: Closures and Scoping
// Tests closure captures, let in loops, IIFE, complex scoping

// 1. Basic closure
function makeCounter() {
  let count = 0;
  return { inc: () => ++count, get: () => count };
}
const ctr = makeCounter();
ctr.inc();
ctr.inc();
console.log("basic_closure:" + (ctr.get() === 2));

// 2. let in for-loop (each iteration captures its own copy)
const fns: (() => number)[] = [];
for (let i = 0; i < 3; i++) {
  fns.push(() => i);
}
console.log("let_loop:" + (fns[0]() === 0 && fns[1]() === 1 && fns[2]() === 2));

// 3. var in for-loop (all share same variable)
const fns2: (() => number)[] = [];
for (var j = 0; j < 3; j++) {
  fns2.push(((captured) => () => captured)(j));
}
console.log("var_loop_iife:" + (fns2[0]() === 0 && fns2[1]() === 1 && fns2[2]() === 2));

// 4. IIFE
const iife = (() => {
  const secret = 42;
  return secret;
})();
console.log("iife:" + (iife === 42));

// 5. Nested closures
function outer2() {
  let x = 10;
  return function middle() {
    let y = 20;
    return function inner() {
      return x + y;
    };
  };
}
console.log("nested_closure:" + (outer2()()() === 30));

// 6. Closure modifying outer variable
function accumulator() {
  let total = 0;
  return (n: number) => { total += n; return total; };
}
const acc = accumulator();
acc(5);
acc(3);
console.log("closure_mutate:" + (acc(2) === 10));

// 7. Multiple closures sharing state
function shared() {
  let val = 0;
  return {
    inc: () => { val++; },
    dec: () => { val--; },
    get: () => val
  };
}
const sh = shared();
sh.inc(); sh.inc(); sh.dec();
console.log("shared_state:" + (sh.get() === 1));

// 8. Arrow function captures this
class Binder {
  value = 100;
  getArrow() {
    return () => this.value;
  }
}
const binder = new Binder();
const fn = binder.getArrow();
console.log("arrow_this:" + (fn() === 100));
