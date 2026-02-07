async function accumulate(limit: number) {
  let i = 0;
  let sum = 0;
  while (i < limit) {
    const step = await Promise.resolve(i + 1);
    sum = sum + step;
    i = i + 1;
  }
  console.log("sum=" + sum);
  return sum;
}

function onDone(v: number) {
  console.log("done=" + v);
  return v;
}

accumulate(3).then(onDone);
console.log("sync");
