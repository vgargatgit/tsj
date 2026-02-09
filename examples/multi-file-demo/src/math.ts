export function sumTo(limit: number) {
  let i = 1;
  let total = 0;
  while (i <= limit) {
    total = total + i;
    i = i + 1;
  }
  return total;
}

export function makeAdder(base: number) {
  function add(value: number) {
    return value + base;
  }
  return add;
}

export function double(value: number) {
  return value * 2;
}
