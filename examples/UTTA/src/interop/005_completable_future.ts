// UTTA Interop 005: CompletableFuture to Promise
// Tests async Java interop via CompletableFuture

import { fetchGreeting, computeSquare, failingFuture } from "java:dev.utta.AsyncLib";

async function main() {
  // 1. Basic CompletableFuture → value
  const greeting = await fetchGreeting("TSJ");
  console.log("cf_basic:" + (greeting === "Hello, TSJ!"));

  // 2. CompletableFuture with computation
  const squared = await computeSquare(7);
  console.log("cf_compute:" + (squared === 49));

  // 3. Failed CompletableFuture
  try {
    await failingFuture();
    console.log("cf_fail:false");
  } catch (e: any) {
    console.log("cf_fail:" + (e !== null));
  }
}

main();
