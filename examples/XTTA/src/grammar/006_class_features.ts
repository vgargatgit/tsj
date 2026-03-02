// XTTA Grammar Torture: Advanced Class Features
// Tests private fields, static blocks, getters/setters, abstract

// 1. Private class fields
class Counter {
  #count = 0;
  increment() { this.#count++; }
  get value() { return this.#count; }
}
const c = new Counter();
c.increment();
c.increment();
console.log("private_field:" + (c.value === 2));

// 2. Static fields and methods
class MathUtils {
  static PI = 3.14159;
  static double(n: number) { return n * 2; }
}
console.log("static_field:" + (MathUtils.PI > 3.14));
console.log("static_method:" + (MathUtils.double(5) === 10));

// 3. Static block
class Config {
  static mode: string;
  static {
    Config.mode = "production";
  }
}
console.log("static_block:" + (Config.mode === "production"));

// 4. Getter/setter
class Temperature {
  private _celsius: number;
  constructor(c: number) { this._celsius = c; }
  get fahrenheit() { return this._celsius * 9 / 5 + 32; }
  set fahrenheit(f: number) { this._celsius = (f - 32) * 5 / 9; }
}
const temp = new Temperature(100);
console.log("getter:" + (temp.fahrenheit === 212));
temp.fahrenheit = 32;
console.log("setter:" + (temp.fahrenheit === 32));

// 5. Abstract class
abstract class Shape {
  abstract area(): number;
  describe() { return "area=" + this.area(); }
}
class Circle extends Shape {
  constructor(private radius: number) { super(); }
  area() { return Math.PI * this.radius * this.radius; }
}
const circle = new Circle(1);
console.log("abstract:" + (circle.area() > 3.14 && circle.area() < 3.15));

// 6. Multiple inheritance via interfaces
interface Printable {
  print(): string;
}
interface Serializable {
  serialize(): string;
}
class Document implements Printable, Serializable {
  constructor(private content: string) {}
  print() { return "DOC:" + this.content; }
  serialize() { return JSON.stringify({ content: this.content });  }
}
const doc = new Document("hello");
console.log("multi_interface:" + (doc.print() === "DOC:hello"));

// 7. Class with computed method
class Dynamic {
  ["computed_method"]() { return "computed"; }
}
const dyn = new Dynamic();
console.log("computed_method:" + (dyn["computed_method"]() === "computed"));

// 8. Readonly modifier
class Immutable {
  readonly name: string;
  constructor(name: string) { this.name = name; }
}
const imm = new Immutable("frozen");
console.log("readonly:" + (imm.name === "frozen"));
