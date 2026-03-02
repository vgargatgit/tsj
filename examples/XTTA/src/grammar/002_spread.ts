// XTTA Grammar Torture: Spread Operator
// Tests array spread, object spread, function call spread

// 1. Array spread
const a1 = [1, 2, 3];
const a2 = [0, ...a1, 4];
console.log("arr_spread:" + (a2.length === 5 && a2[0] === 0 && a2[4] === 4));

// 2. Object spread
const o1 = { x: 1, y: 2 };
const o2 = { ...o1, z: 3 };
console.log("obj_spread:" + (o2.x === 1 && o2.z === 3));

// 3. Object spread override
const o3 = { ...o1, x: 99 };
console.log("obj_override:" + (o3.x === 99 && o3.y === 2));

// 4. Function call spread
function add3(a: number, b: number, c: number) { return a + b + c; }
const args: [number, number, number] = [1, 2, 3];
console.log("fn_spread:" + (add3(...args) === 6));

// 5. Spread with concat-like behavior
const merged = [...[1, 2], ...[3, 4]];
console.log("multi_spread:" + (merged.length === 4));

// 6. Spread to clone array
const original = [1, 2, 3];
const clone = [...original];
clone[0] = 99;
console.log("clone_spread:" + (original[0] === 1 && clone[0] === 99));

// 7. Spread to clone object
const origObj = { a: 1, b: 2 };
const cloneObj = { ...origObj };
cloneObj.a = 99;
console.log("clone_obj:" + (origObj.a === 1 && cloneObj.a === 99));
