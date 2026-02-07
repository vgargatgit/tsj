const undef = undefined;
console.log("coerce=" + (1 == "1") + ":" + (1 === "1"));
console.log("nullish=" + (undef == null) + ":" + (undef === null));
console.log("boolnum=" + (false == 0) + ":" + (false === 0));
