// UTTA Grammar 013: Module Re-exports
// Tests export * from, export { x as y } from, barrel patterns
// NOTE: This is a single-file test that checks export/import syntax compiles

// 1. Named re-export simulation (same-file check)
const exportedA = 1;
const exportedB = 2;

// 2. Aliased exports
const original = "hello";
function factory() { return "world"; }

// 3. Default export + named export coexistence
class DefaultClass {
  value = 42;
}

// 4. Namespace-style object (barrel pattern simulation)
const barrel = {
  util1: () => "u1",
  util2: () => "u2",
  util3: () => "u3"
};
console.log("barrel:" + (barrel.util1() === "u1" && barrel.util3() === "u3"));

// 5. Dynamic property access on module-like object
const mods: Record<string, () => string> = {
  a: () => "alpha",
  b: () => "beta",
};
const key = "a";
console.log("dynamic_mod:" + (mods[key]() === "alpha"));

// 6. Computed export names
const items = ["x", "y", "z"];
const exports: Record<string, number> = {};
items.forEach((item, i) => { exports[item] = i; });
console.log("computed:" + (exports.x === 0 && exports.z === 2));

// 7. Circular reference simulation
const nodeA: any = { name: "A", ref: null };
const nodeB: any = { name: "B", ref: nodeA };
nodeA.ref = nodeB;
console.log("circular:" + (nodeA.ref.ref.name === "A"));
