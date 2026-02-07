async function failLater() {
  await Promise.resolve(1);
  throw "boom";
}

function onError(reason: string) {
  console.log("error=" + reason);
  return reason;
}

failLater().then(undefined, onError);
console.log("sync");
