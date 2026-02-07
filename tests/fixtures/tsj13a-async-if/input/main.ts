async function pick(flag: number) {
  let value = 0;
  if (flag === 1) {
    value = await Promise.resolve(10);
    console.log("then=" + value);
  } else {
    value = await Promise.resolve(20);
    console.log("else=" + value);
  }
  console.log("after=" + value);
  return value;
}

function onDone(v: number) {
  console.log("done=" + v);
  return v;
}

pick(1).then(onDone);
console.log("sync");
