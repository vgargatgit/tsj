// UTTA Stress 003: Large Arrays
// Tests handling of large data structures

// 1. Create large array (10K elements)
const big: number[] = [];
for (let i = 0; i < 10000; i++) big.push(i);
console.log("create_10k:" + (big.length === 10000));

// 2. Sum large array
let sum = 0;
for (const v of big) sum += v;
console.log("sum_10k:" + (sum === 49995000));

// 3. Filter large array
const evens = big.filter(n => n % 2 === 0);
console.log("filter_10k:" + (evens.length === 5000));

// 4. Map large array
const doubled = big.map(n => n * 2);
console.log("map_10k:" + (doubled[9999] === 19998));

// 5. Reduce large array
const total = big.reduce((acc, n) => acc + n, 0);
console.log("reduce_10k:" + (total === 49995000));

// 6. Array of objects
const objs: { id: number; name: string }[] = [];
for (let i = 0; i < 1000; i++) {
  objs.push({ id: i, name: "item" + i });
}
console.log("obj_arr:" + (objs.length === 1000 && objs[999].name === "item999"));

// 7. Nested array creation
const matrix: number[][] = [];
for (let i = 0; i < 100; i++) {
  const row: number[] = [];
  for (let j = 0; j < 100; j++) {
    row.push(i * 100 + j);
  }
  matrix.push(row);
}
console.log("matrix:" + (matrix.length === 100 && matrix[99][99] === 9999));

// 8. String array operations
const strs: string[] = [];
for (let i = 0; i < 1000; i++) strs.push("s" + i);
const joined = strs.slice(0, 5).join(",");
console.log("str_arr:" + (joined === "s0,s1,s2,s3,s4"));
