// XTTA Grammar Torture: Control Flow Edge Cases
// Tests labeled statements, ternary chains, comma operator, void, switch fallthrough

// 1. Labeled break
let result1 = 0;
outer: for (let i = 0; i < 5; i++) {
  for (let j = 0; j < 5; j++) {
    if (j === 2) break outer;
    result1++;
  }
}
console.log("labeled_break:" + (result1 === 2));

// 2. Labeled continue
let result2 = 0;
loop: for (let i = 0; i < 3; i++) {
  for (let j = 0; j < 3; j++) {
    if (j === 1) continue loop;
    result2++;
  }
}
console.log("labeled_continue:" + (result2 === 3));

// 3. Nested ternary
const grade = (score: number) =>
  score >= 90 ? "A" :
  score >= 80 ? "B" :
  score >= 70 ? "C" :
  score >= 60 ? "D" : "F";
console.log("ternary_chain:" + (grade(95) === "A" && grade(75) === "C" && grade(55) === "F"));

// 4. Comma operator
const comma = (1, 2, 3);
console.log("comma_op:" + (comma === 3));

// 5. void operator
const voided = void 0;
console.log("void_op:" + (voided === undefined));

// 6. Switch with fallthrough
function fallthrough(n: number): string {
  let r = "";
  switch (n) {
    case 1: r += "one";
    case 2: r += "two"; break;
    case 3: r += "three"; break;
  }
  return r;
}
console.log("switch_fall:" + (fallthrough(1) === "onetwo" && fallthrough(2) === "two"));

// 7. Switch with default in middle
function midDefault(n: number): string {
  switch (n) {
    case 1: return "one";
    default: return "other";
    case 3: return "three";
  }
}
console.log("switch_middefault:" + (midDefault(1) === "one" && midDefault(3) === "three" && midDefault(99) === "other"));

// 8. do-while
let dw = 0;
do { dw++; } while (dw < 5);
console.log("do_while:" + (dw === 5));

// 9. for-in loop
const forInObj: Record<string, number> = { x: 1, y: 2, z: 3 };
const keys: string[] = [];
for (const k in forInObj) {
  keys.push(k);
}
console.log("for_in:" + (keys.length === 3));
