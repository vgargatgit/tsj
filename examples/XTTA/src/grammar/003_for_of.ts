// XTTA Grammar Torture: for...of loops
// Tests iteration over arrays, strings, and custom iterables

// 1. for-of over array
const nums = [10, 20, 30];
let sum = 0;
for (const n of nums) {
  sum += n;
}
console.log("arr_forof:" + (sum === 60));

// 2. for-of over string
const str = "abc";
let chars = "";
for (const ch of str) {
  chars += ch;
}
console.log("str_forof:" + (chars === "abc"));

// 3. for-of with break
let found = -1;
for (const n of [1, 2, 3, 4, 5]) {
  if (n === 3) {
    found = n;
    break;
  }
}
console.log("forof_break:" + (found === 3));

// 4. for-of with continue
let evenSum = 0;
for (const n of [1, 2, 3, 4, 5, 6]) {
  if (n % 2 !== 0) continue;
  evenSum += n;
}
console.log("forof_continue:" + (evenSum === 12));

// 5. for-of with index tracking
const items = ["a", "b", "c"];
let idx = 0;
let result = "";
for (const item of items) {
  result += idx + ":" + item + " ";
  idx++;
}
console.log("forof_index:" + (result.trim() === "0:a 1:b 2:c"));

// 6. Nested for-of
const matrix = [[1, 2], [3, 4]];
let flatSum = 0;
for (const row of matrix) {
  for (const val of row) {
    flatSum += val;
  }
}
console.log("nested_forof:" + (flatSum === 10));
