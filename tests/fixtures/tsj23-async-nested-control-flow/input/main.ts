async function nested(limit: number) {
  let i = 0;
  let total = 0;
  while (i < limit) {
    if (i === 0) {
      total = total + await Promise.resolve(i + 10);
    } else {
      total = total + await Promise.resolve(i + 20);
    }
    i = i + 1;
  }
  return total;
}

async function failIfLarge(value: number) {
  if (value > 30) {
    throw await Promise.resolve("too-big");
  }
  return "ok";
}

function onStatus(value: string) {
  console.log("status=" + value);
  return value;
}

function onError(error: unknown) {
  console.log("err=" + error);
  return error;
}

nested(2).then(failIfLarge).then(onStatus, onError);
console.log("sync");
