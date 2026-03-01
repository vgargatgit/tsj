// UTTA Grammar 015: Misc Edge Cases
// Tests void, comma operator, in operator, delete, typeof undeclared

// 1. in operator with objects
const obj = { a: 1, b: 2, c: 3 };
console.log("in_op:" + ("a" in obj && !("d" in obj)));

// 2. in operator with arrays
const arr = [10, 20, 30];
console.log("in_arr:" + (0 in arr && !(5 in arr)));

// 3. delete operator
const mutable: any = { x: 1, y: 2 };
delete mutable.x;
console.log("delete:" + (!("x" in mutable) && mutable.y === 2));

// 4. typeof on undeclared (should not throw)
console.log("typeof_undecl:" + (typeof undeclaredVariable === "undefined"));

// 5. Labeled break in nested loops
let found = "";
outer: for (let i = 0; i < 5; i++) {
  for (let j = 0; j < 5; j++) {
    if (i === 2 && j === 3) {
      found = i + "," + j;
      break outer;
    }
  }
}
console.log("labeled:" + (found === "2,3"));

// 6. Labeled continue
let skipped = 0;
loop: for (let i = 0; i < 3; i++) {
  for (let j = 0; j < 3; j++) {
    if (j === 1) continue loop;
    skipped++;
  }
}
console.log("labeled_cont:" + (skipped === 3));

// 7. Nullish on deeply nested
const deep: any = {};
console.log("deep_nullish:" + ((deep?.a?.b?.c ?? "miss") === "miss"));

// 8. Comma in for loop
let sum = 0;
for (let i = 0, j = 10; i < 5; i++, j--) {
  sum += i + j;
}
console.log("comma_for:" + (sum === 50));

// 9. void operator
const voidResult = void 0;
console.log("void:" + (voidResult === undefined));

// 10. Comma operator in expression
const commaResult = (1, 2, 3, 4, 5);
console.log("comma:" + (commaResult === 5));
