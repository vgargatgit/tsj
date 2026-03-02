// XTTA Builtins Torture: Math Methods

// 1. Math.floor
console.log("math_floor:" + (Math.floor(3.7) === 3));

// 2. Math.ceil
console.log("math_ceil:" + (Math.ceil(3.2) === 4));

// 3. Math.round
console.log("math_round:" + (Math.round(3.5) === 4 && Math.round(3.4) === 3));

// 4. Math.abs
console.log("math_abs:" + (Math.abs(-5) === 5 && Math.abs(5) === 5));

// 5. Math.max / Math.min
console.log("math_max:" + (Math.max(1, 5, 3) === 5));
console.log("math_min:" + (Math.min(1, 5, 3) === 1));

// 6. Math.pow
console.log("math_pow:" + (Math.pow(2, 10) === 1024));

// 7. Math.sqrt
console.log("math_sqrt:" + (Math.sqrt(144) === 12));

// 8. Math.sign
console.log("math_sign:" + (Math.sign(-5) === -1 && Math.sign(0) === 0 && Math.sign(5) === 1));

// 9. Math.trunc
console.log("math_trunc:" + (Math.trunc(3.9) === 3 && Math.trunc(-3.9) === -3));

// 10. Math.random
const rnd = Math.random();
console.log("math_random:" + (rnd >= 0 && rnd < 1));

// 11. Math.PI and Math.E
console.log("math_PI:" + (Math.PI > 3.14 && Math.PI < 3.15));
console.log("math_E:" + (Math.E > 2.71 && Math.E < 2.72));

// 12. Math.log / Math.log2 / Math.log10
console.log("math_log:" + (Math.abs(Math.log(Math.E) - 1) < 0.0001));
console.log("math_log2:" + (Math.log2(8) === 3));
console.log("math_log10:" + (Math.log10(1000) === 3));

// 13. Number.isInteger
console.log("num_isInteger:" + (Number.isInteger(5) === true && Number.isInteger(5.5) === false));

// 14. Number.isFinite
console.log("num_isFinite:" + (Number.isFinite(42) === true && Number.isFinite(Infinity) === false));

// 15. Number.isNaN
console.log("num_isNaN:" + (Number.isNaN(NaN) === true && Number.isNaN(42) === false));

// 16. parseInt / parseFloat
console.log("parseInt:" + (parseInt("42") === 42));
console.log("parseFloat:" + (parseFloat("3.14") === 3.14));

// 17. toFixed
console.log("toFixed:" + ((3.14159).toFixed(2) === "3.14"));

// 18. toString with radix
console.log("toString_radix:" + ((255).toString(16) === "ff"));
