// UTTA Grammar 001: for-await-of
// Tests async iteration protocol

async function* asyncRange(n: number) {
  for (let i = 0; i < n; i++) {
    yield i;
  }
}

async function main() {
  // 1. Basic for-await-of
  const results: number[] = [];
  for await (const val of asyncRange(3)) {
    results.push(val);
  }
  console.log("async_for_await:" + (results.length === 3 && results[0] === 0 && results[2] === 2));

  // 2. for-await-of with break
  let count = 0;
  for await (const val of asyncRange(10)) {
    count++;
    if (val >= 2) break;
  }
  console.log("async_break:" + (count === 3));

  // 3. for-await-of on array of promises
  const promises = [Promise.resolve(10), Promise.resolve(20), Promise.resolve(30)];
  const gathered: number[] = [];
  for await (const val of promises) {
    gathered.push(val);
  }
  console.log("promise_array:" + (gathered.length === 3 && gathered[1] === 20));
}

main();
