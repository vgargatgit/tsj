// XTTA Grammar Torture: Type Narrowing and Guards
// Tests type guards, narrowing, discriminated unions, assertion functions

// 1. typeof narrowing
function typeCheck(x: string | number): string {
  if (typeof x === "string") {
    return "str:" + x.length;
  }
  return "num:" + x;
}
console.log("typeof_narrow:" + (typeCheck("hi") === "str:2" && typeCheck(42) === "num:42"));

// 2. Truthiness narrowing
function truthy(x: string | null): string {
  if (x) return x;
  return "empty";
}
console.log("truthy_narrow:" + (truthy("hi") === "hi" && truthy(null) === "empty"));

// 3. Equality narrowing
function eqCheck(a: string | number, b: string | number): string {
  if (a === b) return "equal";
  return "diff";
}
console.log("eq_narrow:" + (eqCheck(1, 1) === "equal" && eqCheck(1, 2) === "diff"));

// 4. Discriminated unions
interface Circle { kind: "circle"; radius: number; }
interface Square { kind: "square"; side: number; }
type Shape = Circle | Square;

function area(s: Shape): number {
  switch (s.kind) {
    case "circle": return Math.PI * s.radius * s.radius;
    case "square": return s.side * s.side;
  }
}
console.log("discrim_union:" + (area({ kind: "square", side: 5 }) === 25));

// 5. instanceof narrowing
class Dog {
  bark() { return "woof"; }
}
class Cat {
  meow() { return "meow"; }
}
function speak(pet: Dog | Cat): string {
  if (pet instanceof Dog) return pet.bark();
  return pet.meow();
}
console.log("instanceof_narrow:" + (speak(new Dog()) === "woof" && speak(new Cat()) === "meow"));

// 6. Custom type guard
function isString(x: unknown): x is string {
  return typeof x === "string";
}
function processValue(x: unknown): string {
  if (isString(x)) return x.toUpperCase();
  return "not-string";
}
console.log("type_guard:" + (processValue("hi") === "HI" && processValue(42) === "not-string"));

// 7. in operator narrowing
interface Fish { swim: () => void; }
interface Bird { fly: () => void; }
function move(animal: Fish | Bird): string {
  if ("swim" in animal) return "swimming";
  return "flying";
}
console.log("in_narrow:" + (move({ swim: () => {} }) === "swimming"));

// 8. Nested narrowing
function nested(x: string | number | null): string {
  if (x === null) return "null";
  if (typeof x === "string") return "s:" + x;
  return "n:" + x;
}
console.log("nested_narrow:" + (nested(null) === "null" && nested("a") === "s:a" && nested(1) === "n:1"));
