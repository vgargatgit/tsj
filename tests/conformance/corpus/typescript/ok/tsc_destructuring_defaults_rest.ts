// Source inspiration: microsoft/TypeScript conformance destructuring suites.
const input = { a: 1, b: 2, c: 3, d: 4 };
const { a, b = 20, c, d } = input;
const [x, y = 9, ...tail] = [10];

function take({ p = 5, q }) {
  return p + q;
}

console.log(a + b + x + y + tail.length + take({ q: 2 }) + c + d);
