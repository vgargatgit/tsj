// XTTA Grammar Torture: Rest Parameters, Default Parameters, Computed Properties

// 1. Rest parameters
function sum(...nums: number[]): number {
  let total = 0;
  for (let i = 0; i < nums.length; i++) {
    total += nums[i];
  }
  return total;
}
console.log("rest_params:" + (sum(1, 2, 3, 4, 5) === 15));

// 2. Rest with leading params
function first_rest(first: string, ...rest: string[]): string {
  return first + ":" + rest.length;
}
console.log("rest_leading:" + (first_rest("a", "b", "c") === "a:2"));

// 3. Default parameters
function greet(name: string = "world") {
  return "hello " + name;
}
console.log("default_param:" + (greet() === "hello world" && greet("ts") === "hello ts"));

// 4. Default with expression
function createId(prefix: string = "id", num: number = Date.now() > 0 ? 1 : 0) {
  return prefix + "-" + num;
}
console.log("default_expr:" + (createId() === "id-1"));

// 5. Default uses previous param
function range(start: number, end: number, step: number = 1): number[] {
  const result: number[] = [];
  for (let i = start; i < end; i += step) {
    result.push(i);
  }
  return result;
}
console.log("default_prev:" + (range(0, 5).length === 5 && range(0, 10, 2).length === 5));

// 6. Computed property names
const prop = "dynamic";
const computed = { [prop]: 42, ["a" + "b"]: 99 };
console.log("computed_prop:" + (computed.dynamic === 42 && computed.ab === 99));

// 7. Computed property with symbol-like key
const keyName = "key_" + 1;
const obj: Record<string, number> = { [keyName]: 100 };
console.log("computed_dynamic:" + (obj["key_1"] === 100));

// 8. Object shorthand
const xx = 1, yy = 2;
const shorthand = { xx, yy };
console.log("obj_shorthand:" + (shorthand.xx === 1 && shorthand.yy === 2));

// 9. Method shorthand
const methods = {
  add(a: number, b: number) { return a + b; },
  mul(a: number, b: number) { return a * b; }
};
console.log("method_shorthand:" + (methods.add(2, 3) === 5 && methods.mul(2, 3) === 6));
