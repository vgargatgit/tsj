class Base {
  value: number;
  constructor(seed: number) {
    this.value = seed;
  }
  read() {
    return this.value;
  }
}

class Derived extends Base {
  constructor(seed: number) {
    super(seed + 2);
  }
  triple() {
    return this.value * 3;
  }
}

const d = new Derived(3);
const obj = { label: "ok", count: d.read() };
obj.count = obj.count + 1;
console.log("tsj9=" + obj.label + ":" + d.triple() + ":" + obj.count);
