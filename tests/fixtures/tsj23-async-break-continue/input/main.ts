async function flow(limit: number) {
  let i = 0;
  let sum = 0;
  while (i < limit) {
    i = i + 1;
    if (i === 2) {
      continue;
    }
    if (i === 4) {
      break;
    }
    const step = await Promise.resolve(i);
    sum = sum + step;
  }
  console.log("sum=" + sum);
  return sum;
}

function onDone(v: number) {
  console.log("done=" + v);
  return v;
}

flow(6).then(onDone);
console.log("sync");
