const ops = {
  async compute(seed: number) {
    const value = await Promise.resolve(seed + 2);
    return value * 3;
  }
};

function onDone(result: number) {
  console.log("done=" + result);
  return result;
}

ops.compute(2).then(onDone);
console.log("sync");
