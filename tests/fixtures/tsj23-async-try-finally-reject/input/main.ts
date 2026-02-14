async function run() {
  try {
    throw await Promise.resolve("boom");
  } finally {
    const marker = await Promise.resolve("fin");
    console.log("finally=" + marker);
  }
}

run().then(
  (value: string) => {
    console.log("done=" + value);
    return value;
  },
  (reason: string) => {
    console.log("error=" + reason);
    return reason;
  }
);
console.log("sync");
