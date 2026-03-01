// UTTA Grammar 014: Advanced Type Guards & Assertions
// Tests typeof narrowing edge cases, assertion functions, satisfies

// 1. typeof on all primitive types
function checkType(val: any): string {
  if (typeof val === "string") return "string";
  if (typeof val === "number") return "number";
  if (typeof val === "boolean") return "boolean";
  if (typeof val === "undefined") return "undefined";
  if (typeof val === "object" && val === null) return "null";
  if (typeof val === "object") return "object";
  if (typeof val === "function") return "function";
  return "unknown";
}
console.log("typeof_str:" + (checkType("hi") === "string"));
console.log("typeof_num:" + (checkType(42) === "number"));
console.log("typeof_bool:" + (checkType(true) === "boolean"));
console.log("typeof_undef:" + (checkType(undefined) === "undefined"));
console.log("typeof_null:" + (checkType(null) === "null"));
console.log("typeof_obj:" + (checkType({}) === "object"));
console.log("typeof_fn:" + (checkType(() => {}) === "function"));

// 2. instanceof with class hierarchy
class Animal { kind = "animal"; }
class Dog extends Animal { breed = "mixed"; }
class Cat extends Animal { indoor = true; }

function identify(a: Animal): string {
  if (a instanceof Dog) return "dog:" + a.breed;
  if (a instanceof Cat) return "cat:" + a.indoor;
  return "animal";
}
console.log("inst_dog:" + (identify(new Dog()) === "dog:mixed"));
console.log("inst_cat:" + (identify(new Cat()) === "cat:true"));

// 3. User-defined type guard
interface Square { kind: "square"; size: number; }
interface Rect { kind: "rect"; w: number; h: number; }
type ShapeU = Square | Rect;

function isSquare(s: ShapeU): s is Square {
  return s.kind === "square";
}
const sq: ShapeU = { kind: "square", size: 5 };
console.log("guard:" + (isSquare(sq) && sq.size === 5));

// 4. Discriminated union exhaustive check
function area(s: ShapeU): number {
  switch (s.kind) {
    case "square": return s.size * s.size;
    case "rect": return s.w * s.h;
  }
}
console.log("discrim:" + (area({ kind: "rect", w: 3, h: 4 }) === 12));

// 5. Assertion function pattern
function assertDefined<T>(val: T | undefined | null, msg: string): asserts val is T {
  if (val == null) throw new Error(msg);
}
let maybeNum: number | undefined = 42;
assertDefined(maybeNum, "should be defined");
console.log("assert_fn:" + (maybeNum === 42));
