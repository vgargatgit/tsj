// UTTA Stress 002: Deep Recursion
// Tests JVM stack handling with deep recursive calls

// 1. Simple recursion (100 levels)
function countdown(n: number): number {
  if (n <= 0) return 0;
  return 1 + countdown(n - 1);
}
console.log("recurse_100:" + (countdown(100) === 100));

// 2. Fibonacci (moderate depth)
function fib(n: number): number {
  if (n <= 1) return n;
  return fib(n - 1) + fib(n - 2);
}
console.log("fib_20:" + (fib(20) === 6765));

// 3. Mutual recursion
function isEven(n: number): boolean {
  if (n === 0) return true;
  return isOdd(n - 1);
}
function isOdd(n: number): boolean {
  if (n === 0) return false;
  return isEven(n - 1);
}
console.log("mutual_50:" + (isEven(50) === true && isOdd(51) === true));

// 4. Recursion with accumulator (500 levels)
function sumTo(n: number, acc: number): number {
  if (n <= 0) return acc;
  return sumTo(n - 1, acc + n);
}
console.log("acc_500:" + (sumTo(500, 0) === 125250));

// 5. Recursive tree building
interface TreeNode {
  value: number;
  children: TreeNode[];
}
function buildTree(depth: number, value: number): TreeNode {
  if (depth <= 0) return { value, children: [] };
  return {
    value,
    children: [buildTree(depth - 1, value * 2), buildTree(depth - 1, value * 2 + 1)]
  };
}
const tree = buildTree(5, 1);
console.log("tree:" + (tree.children.length === 2));

// 6. Deep recursion count (1000 levels)
function deepCount(n: number): number {
  if (n <= 0) return 0;
  return 1 + deepCount(n - 1);
}
console.log("deep_1000:" + (deepCount(1000) === 1000));
