console.log("before");
const value = await Promise.resolve(1);
console.log("after=" + value);
