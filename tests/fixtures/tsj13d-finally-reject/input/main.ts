Promise.resolve(1)
  .finally(() => Promise.reject("fin"))
  .then(
    (value: number) => {
      console.log("value=" + value);
      return value;
    },
    (reason: string) => {
      console.log("error=" + reason);
      return reason;
    }
  );
console.log("sync");
