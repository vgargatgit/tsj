const inc = async (value: number) => (await Promise.resolve(value)) + 1;

function onDone(result: number) {
  console.log("done=" + result);
  return result;
}

inc(5).then(onDone);
console.log("sync");
