const badThenable = {
  then(resolve: any, reject: any) {
    reject("boom");
    resolve(99);
  }
};

Promise.resolve(badThenable).then(
  undefined,
  (reason: string) => {
    console.log("error=" + reason);
    return reason;
  }
);
console.log("sync");
