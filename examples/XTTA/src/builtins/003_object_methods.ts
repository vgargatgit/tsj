// XTTA Builtins Torture: Object Methods

// 1. Object.keys
const obj = { a: 1, b: 2, c: 3 };
const keys = Object.keys(obj);
console.log("obj_keys:" + (keys.length === 3));

// 2. Object.values
const vals = Object.values(obj);
console.log("obj_values:" + (vals.length === 3));

// 3. Object.entries
const entries = Object.entries(obj);
console.log("obj_entries:" + (entries.length === 3 && entries[0][0] === "a" && entries[0][1] === 1));

// 4. Object.assign
const target = { a: 1 };
const source = { b: 2, c: 3 };
const assigned = Object.assign(target, source);
console.log("obj_assign:" + (assigned.b === 2 && assigned.c === 3));

// 5. Object.freeze
const frozen = Object.freeze({ x: 1, y: 2 });
console.log("obj_freeze:" + (frozen.x === 1));

// 6. Object.fromEntries
const fromEntries = Object.fromEntries([["a", 1], ["b", 2]]);
console.log("obj_fromEntries:" + (fromEntries.a === 1 && fromEntries.b === 2));

// 7. Object.create
const proto = { greet() { return "hello"; } };
const child = Object.create(proto);
console.log("obj_create:" + (child.greet() === "hello"));

// 8. hasOwnProperty
const hasOwn = { x: 1 };
console.log("obj_hasOwn:" + (hasOwn.hasOwnProperty("x") === true));

// 9. Property enumeration
const enumObj: Record<string, number> = { a: 1, b: 2, c: 3 };
let keyCount = 0;
for (const _k in enumObj) {
  keyCount++;
}
console.log("obj_forin:" + (keyCount === 3));

// 10. Dynamic property access
const dynObj: Record<string, string> = { foo: "bar" };
const key = "foo";
console.log("obj_dynamic:" + (dynObj[key] === "bar"));
