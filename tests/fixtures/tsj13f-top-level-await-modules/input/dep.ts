export let status = "init";
status = await Promise.resolve("ready");
console.log("dep=" + status);
