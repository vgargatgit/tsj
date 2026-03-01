// UTTA Stress 004: Deep Prototype Chains
// Tests inheritance depth and prototype traversal

// 1. 10-level class hierarchy
class L0 { level() { return 0; } base() { return "L0"; } }
class L1 extends L0 { level() { return 1; } }
class L2 extends L1 { level() { return 2; } }
class L3 extends L2 { level() { return 3; } }
class L4 extends L3 { level() { return 4; } }
class L5 extends L4 { level() { return 5; } }
class L6 extends L5 { level() { return 6; } }
class L7 extends L6 { level() { return 7; } }
class L8 extends L7 { level() { return 8; } }
class L9 extends L8 { level() { return 9; } }

const deep = new L9();
console.log("deep_level:" + (deep.level() === 9));
console.log("deep_base:" + (deep.base() === "L0"));
console.log("deep_inst:" + (deep instanceof L0 && deep instanceof L5));

// 2. Method override chain
class Counter {
  count = 0;
  increment() { this.count++; return this; }
}
class DoubleCounter extends Counter {
  increment() { this.count += 2; return this; }
}
class TripleCounter extends DoubleCounter {
  increment() { this.count += 3; return this; }
}
const tc = new TripleCounter();
tc.increment().increment().increment();
console.log("override_chain:" + (tc.count === 9));

// 3. Super call chain
class A {
  greet(): string { return "A"; }
}
class B extends A {
  greet(): string { return super.greet() + "B"; }
}
class C extends B {
  greet(): string { return super.greet() + "C"; }
}
class D extends C {
  greet(): string { return super.greet() + "D"; }
}
console.log("super_chain:" + (new D().greet() === "ABCD"));

// 4. Dynamic property lookup through chain
class PropBase {
  get info(): string { return "base"; }
}
class PropChild extends PropBase {
  extra = 42;
}
const pc = new PropChild();
console.log("prop_lookup:" + (pc.info === "base" && pc.extra === 42));

// 5. Mixin-style pattern (advanced generics — tests parser support)
function addTimestamp<T extends new (...args: any[]) => any>(Base: T) {
  return class extends Base {
    timestamp = Date.now();
    getTimestamp() { return this.timestamp; }
  };
}
class BaseEntity { name = "entity"; }
const Enhanced = addTimestamp(BaseEntity);
const inst = new Enhanced();
console.log("mixin:" + (inst.name === "entity" && inst.getTimestamp() > 0));
