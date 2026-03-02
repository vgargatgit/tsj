// XTTA Builtins Torture: Array Methods
// Tests map, filter, reduce, find, some, every, includes, indexOf, forEach, sort, slice, concat

// 1. Array.map
const mapped = [1, 2, 3].map(x => x * 2);
console.log("arr_map:" + (mapped[0] === 2 && mapped[1] === 4 && mapped[2] === 6));

// 2. Array.filter
const filtered = [1, 2, 3, 4, 5].filter(x => x % 2 === 0);
console.log("arr_filter:" + (filtered.length === 2 && filtered[0] === 2 && filtered[1] === 4));

// 3. Array.reduce
const sum = [1, 2, 3, 4].reduce((acc, x) => acc + x, 0);
console.log("arr_reduce:" + (sum === 10));

// 4. Array.find
const found = [1, 2, 3, 4].find(x => x > 2);
console.log("arr_find:" + (found === 3));

// 5. Array.findIndex
const idx = [10, 20, 30].findIndex(x => x === 20);
console.log("arr_findIndex:" + (idx === 1));

// 6. Array.some
console.log("arr_some:" + ([1, 2, 3].some(x => x > 2) === true));
console.log("arr_some_false:" + ([1, 2, 3].some(x => x > 5) === false));

// 7. Array.every
console.log("arr_every:" + ([2, 4, 6].every(x => x % 2 === 0) === true));
console.log("arr_every_false:" + ([2, 3, 6].every(x => x % 2 === 0) === false));

// 8. Array.includes
console.log("arr_includes:" + ([1, 2, 3].includes(2) === true));
console.log("arr_includes_false:" + ([1, 2, 3].includes(5) === false));

// 9. Array.indexOf
console.log("arr_indexOf:" + ([10, 20, 30].indexOf(20) === 1));
console.log("arr_indexOf_miss:" + ([10, 20, 30].indexOf(99) === -1));

// 10. Array.forEach
let forEachSum = 0;
[1, 2, 3].forEach(x => { forEachSum += x; });
console.log("arr_forEach:" + (forEachSum === 6));

// 11. Array.sort
const sorted = [3, 1, 2].sort((a, b) => a - b);
console.log("arr_sort:" + (sorted[0] === 1 && sorted[1] === 2 && sorted[2] === 3));

// 12. Array.slice
const sliced = [1, 2, 3, 4, 5].slice(1, 3);
console.log("arr_slice:" + (sliced.length === 2 && sliced[0] === 2 && sliced[1] === 3));

// 13. Array.concat
const concated = [1, 2].concat([3, 4]);
console.log("arr_concat:" + (concated.length === 4 && concated[3] === 4));

// 14. Array.join
console.log("arr_join:" + ([1, 2, 3].join("-") === "1-2-3"));

// 15. Array.reverse
const reversed = [1, 2, 3].reverse();
console.log("arr_reverse:" + (reversed[0] === 3 && reversed[2] === 1));

// 16. Array.push/pop
const arr: number[] = [1, 2];
arr.push(3);
const popped = arr.pop();
console.log("arr_push_pop:" + (popped === 3 && arr.length === 2));

// 17. Array.shift/unshift
const arr2: number[] = [2, 3];
arr2.unshift(1);
const shifted = arr2.shift();
console.log("arr_shift_unshift:" + (shifted === 1 && arr2.length === 2));

// 18. Array.flat
const nested = [[1, 2], [3, 4]];
const flat = nested.flat();
console.log("arr_flat:" + (flat.length === 4 && flat[2] === 3));

// 19. Array.from
const fromStr = Array.from("abc");
console.log("arr_from:" + (fromStr.length === 3 && fromStr[0] === "a"));

// 20. Array.isArray
console.log("arr_isArray:" + (Array.isArray([1, 2]) === true && Array.isArray("not") === false));

// 21. Array.fill
const filled = new Array(3).fill(0);
console.log("arr_fill:" + (filled[0] === 0 && filled[1] === 0 && filled[2] === 0));
