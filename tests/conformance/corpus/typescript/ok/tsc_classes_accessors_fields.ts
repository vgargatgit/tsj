// Source inspiration: microsoft/TypeScript conformance class-field/accessor suites.
class Counter {
  #value = 0;

  constructor(seed = 0) {
    this.#value = seed;
  }

  get value() {
    return this.#value;
  }

  set value(next: number) {
    this.#value = next;
  }

  bump() {
    this.#value++;
  }
}

const c = new Counter(2);
c.bump();
c.value = c.value + 3;
console.log(c.value);
