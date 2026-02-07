async function compute(seed: number) {
  console.log("start=" + seed);
  const next = await Promise.resolve(seed + 1);
  console.log("after=" + next);
  return next + 1;
}

function onDone(value: number) {
  console.log("done=" + value);
  return value;
}

compute(4).then(onDone);
console.log("sync");
