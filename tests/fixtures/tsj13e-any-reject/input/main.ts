Promise.any([Promise.reject("a"), Promise.reject("b")]).then(
  (value: string) => {
    console.log("any=" + value);
    return value;
  },
  (reason: any) => {
    console.log("anyErr=" + reason.name + ":" + reason.errors.length);
    return reason;
  }
);
console.log("sync");
