// UTTA Grammar 007: WeakMap, WeakSet, WeakRef
// Tests weak reference data structures

// 1. WeakMap basic ops
const wm = new WeakMap<object, string>();
const k1 = { id: 1 };
const k2 = { id: 2 };
wm.set(k1, "one");
wm.set(k2, "two");
console.log("wm_get:" + (wm.get(k1) === "one"));
console.log("wm_has:" + (wm.has(k2) === true));

// 2. WeakMap delete
wm.delete(k1);
console.log("wm_del:" + (wm.has(k1) === false));

// 3. WeakSet basic ops
const ws = new WeakSet<object>();
const obj1 = { x: 1 };
const obj2 = { x: 2 };
ws.add(obj1);
ws.add(obj2);
console.log("ws_has:" + (ws.has(obj1) === true));
ws.delete(obj1);
console.log("ws_del:" + (ws.has(obj1) === false));

// 4. WeakRef basic
const strong = { value: 42 };
const ref = new WeakRef(strong);
const deref = ref.deref();
console.log("wr_deref:" + (deref !== undefined && deref.value === 42));

// 5. WeakMap with class instances as keys
class Entity {
  constructor(public name: string) {}
}
const cache = new WeakMap<Entity, number>();
const e = new Entity("test");
cache.set(e, 100);
console.log("wm_class:" + (cache.get(e) === 100));
