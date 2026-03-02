// XTTA Grammar Torture: Getter/Setter in Object Literals + Misc

// 1. Getter in object literal
const person = {
  _name: "Alice",
  get name() { return this._name.toUpperCase(); }
};
console.log("obj_getter:" + (person.name === "ALICE"));

// 2. Setter in object literal
const counter = {
  _count: 0,
  get count() { return this._count; },
  set count(v: number) { this._count = v > 0 ? v : 0; }
};
counter.count = 5;
console.log("obj_setter:" + (counter.count === 5));
counter.count = -1;
console.log("obj_setter_guard:" + (counter.count === 0));

// 3. Immediately invoked arrow
const result = ((x: number) => x * x)(7);
console.log("iife_arrow:" + (result === 49));

// 4. Chained method calls
class Builder {
  private parts: string[] = [];
  add(s: string) { this.parts.push(s); return this; }
  build() { return this.parts.join("-"); }
}
const built = new Builder().add("a").add("b").add("c").build();
console.log("method_chain:" + (built === "a-b-c"));

// 5. Recursive function
function fib(n: number): number {
  if (n <= 1) return n;
  return fib(n - 1) + fib(n - 2);
}
console.log("recursion:" + (fib(10) === 55));

// 6. Higher-order function
function apply(fn: (x: number) => number, val: number): number {
  return fn(val);
}
console.log("higher_order:" + (apply(x => x * 2, 21) === 42));

// 7. Nested ternary in template literal
const status = (n: number) => `status:${n > 0 ? "pos" : n < 0 ? "neg" : "zero"}`;
console.log("nested_tern_tmpl:" + (status(1) === "status:pos" && status(-1) === "status:neg" && status(0) === "status:zero"));

// 8. Short-circuit evaluation
let sideEffect = false;
const sc = false && (sideEffect = true);
console.log("short_circuit:" + (sideEffect === false));

// 9. Nullish with function call
function maybe(): string | null { return null; }
const def = maybe() ?? "default";
console.log("nullish_fn:" + (def === "default"));
