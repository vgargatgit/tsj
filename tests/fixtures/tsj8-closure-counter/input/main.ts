function createCounter(seed: number) {
  let value = seed;
  function inc() {
    value = value + 1;
    return value;
  }
  return inc;
}

const c = createCounter(2);
console.log("counter=" + c());
console.log("counter=" + c());
