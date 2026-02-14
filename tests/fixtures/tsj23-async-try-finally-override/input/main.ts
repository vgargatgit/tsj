async function run() {
  try {
    return await Promise.resolve("try");
  } finally {
    const marker = await Promise.resolve("fin");
    return "override-" + marker;
  }
}

function onDone(value: string) {
  console.log("done=" + value);
  return value;
}

run().then(onDone);
console.log("sync");
