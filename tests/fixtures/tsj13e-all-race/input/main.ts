Promise.all([Promise.resolve(1), Promise.resolve(2), 3]).then((values: any) => {
  console.log("all=" + values.length);
  return values;
});

Promise.race([Promise.resolve("win"), Promise.reject("lose")]).then(
  (value: string) => {
    console.log("race=" + value);
    return value;
  },
  (reason: string) => {
    console.log("race-err=" + reason);
    return reason;
  }
);
console.log("sync");
