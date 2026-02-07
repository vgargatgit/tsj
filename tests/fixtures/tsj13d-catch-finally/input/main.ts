Promise.reject("boom")
  .catch((reason: string) => {
    console.log("catch=" + reason);
    return 7;
  })
  .finally(() => {
    console.log("finally");
    return 999;
  })
  .then((value: number) => {
    console.log("value=" + value);
    return value;
  });
console.log("sync");
