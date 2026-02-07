const thenable = {
  then(resolve: any, reject: any) {
    resolve(41);
    reject("bad");
    resolve(99);
  }
};

Promise.resolve(thenable)
  .then((value: number) => {
    console.log("value=" + value);
    return value + 1;
  })
  .then((value: number) => {
    console.log("next=" + value);
    return value;
  });
console.log("sync");
