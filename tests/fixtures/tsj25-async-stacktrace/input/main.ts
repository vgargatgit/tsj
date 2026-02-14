const notCallable: any = 1;
console.log("sync");

async function failAfterAwait(seed: number) {
  await Promise.resolve(seed);
  return notCallable();
}

await failAfterAwait(1);
