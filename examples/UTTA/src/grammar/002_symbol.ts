// UTTA Grammar 002: Symbol
// Tests Symbol creation and well-known symbols

// 1. Basic Symbol creation
const s1 = Symbol("test");
const s2 = Symbol("test");
console.log("unique:" + (s1 !== s2));

// 2. Symbol as property key
const key = Symbol("myKey");
const obj: any = {};
obj[key] = 42;
console.log("prop_key:" + (obj[key] === 42));

// 3. Symbol.for (global registry)
const g1 = Symbol.for("shared");
const g2 = Symbol.for("shared");
console.log("global_same:" + (g1 === g2));

// 4. Symbol.keyFor
const desc = Symbol.keyFor(g1);
console.log("key_for:" + (desc === "shared"));

// 5. Symbol.iterator protocol
class Range {
  constructor(private start: number, private end: number) {}
  [Symbol.iterator]() {
    let cur = this.start;
    const end = this.end;
    return {
      next() {
        if (cur <= end) return { value: cur++, done: false };
        return { value: undefined, done: true };
      }
    };
  }
}
const r = new Range(1, 3);
const vals: number[] = [];
for (const v of r) {
  vals.push(v);
}
console.log("iterator:" + (vals.length === 3 && vals[0] === 1 && vals[2] === 3));

// 6. Symbol.toPrimitive
class Money {
  constructor(private amount: number, private currency: string) {}
  [Symbol.toPrimitive](hint: string) {
    if (hint === "number") return this.amount;
    if (hint === "string") return `${this.amount} ${this.currency}`;
    return this.amount;
  }
}
const m = new Money(42, "USD");
console.log("to_prim_num:" + (+m === 42));
console.log("to_prim_str:" + (`${m}` === "42 USD"));

// 7. typeof Symbol
console.log("typeof_sym:" + (typeof s1 === "symbol"));
