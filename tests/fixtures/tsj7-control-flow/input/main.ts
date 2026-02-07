function add(a: number, b: number) {
  return a + b;
}

let acc = 0;
let i = 1;
while (i <= 3) {
  acc = add(acc, i);
  i = i + 1;
}
if (acc === 6) {
  console.log("sum=" + acc);
} else {
  console.log("bad");
}
