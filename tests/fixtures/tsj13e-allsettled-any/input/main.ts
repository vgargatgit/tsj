Promise.allSettled([Promise.resolve(1), Promise.reject("x")]).then((entries: any) => {
  console.log("settled=" + entries.length);
  return entries;
});

Promise.any([Promise.reject("a"), Promise.resolve("ok")]).then(
  (value: string) => {
    console.log("any=" + value);
    return value;
  },
  (reason: any) => {
    console.log("any-err=" + reason.name);
    return reason;
  }
);
console.log("sync");
