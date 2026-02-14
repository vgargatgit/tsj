Promise.all("ab").then((values: any) => {
  console.log("all=" + values.length);
  return values;
});

Promise.race("ab").then(
  (value: string) => {
    console.log("race=" + value);
    return value;
  },
  (reason: any) => {
    console.log("race-err=" + reason);
    return reason;
  }
);

Promise.allSettled("ab").then((entries: any) => {
  console.log("settled=" + entries.length);
  return entries;
});

Promise.any("ab").then(
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
