// Source inspiration: microsoft/TypeScript conformance loop/control-flow suites.
let acc = 0;
for (const n of [1, 2, 3, 4]) {
  if (n % 2 === 0) {
    continue;
  }
  acc += n;
}

let i = 0;
while (i < 3) {
  acc += i;
  i++;
}

console.log("acc=" + acc);
