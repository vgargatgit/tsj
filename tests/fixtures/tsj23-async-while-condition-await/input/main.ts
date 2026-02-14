async function run() {
  let i = 0;
  while (await Promise.resolve(i < 1)) {
    i = i + 1;
  }
  return i;
}

function onDone(value: number) {
  console.log("done=" + value);
  return value;
}

run().then(onDone);
console.log("sync");
