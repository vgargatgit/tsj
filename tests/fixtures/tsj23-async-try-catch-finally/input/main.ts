async function run(flag: number) {
  try {
    const value = await Promise.resolve(flag);
    if (value === 0) {
      throw "boom";
    }
    console.log("try=" + value);
    return "ok-" + value;
  } catch (err: string) {
    const caught = await Promise.resolve(err + "-handled");
    console.log("catch=" + caught);
    return "catch-" + caught;
  } finally {
    const marker = await Promise.resolve("fin");
    console.log("finally=" + marker);
  }
}

function onDone(value: string) {
  console.log("done=" + value);
  return value;
}

run(0).then(onDone);
console.log("sync");
