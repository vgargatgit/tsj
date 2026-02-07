export let value = 0;
value = await Promise.resolve(5);
console.log("a=" + value);
