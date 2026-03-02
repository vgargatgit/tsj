// XTTA Grammar Torture: Optional Chaining & Nullish Coalescing
// Tests ?., ??, ??=, ||=, &&=

// 1. Optional chaining on object
const obj: any = { a: { b: { c: 42 } } };
console.log("chain_deep:" + (obj?.a?.b?.c === 42));

// 2. Optional chaining with null
const nil: any = null;
console.log("chain_null:" + (nil?.x?.y === undefined));

// 3. Optional chaining on method
const str: string | null = "hello";
console.log("chain_method:" + (str?.toUpperCase() === "HELLO"));

// 4. Optional chaining method on null
const nstr: string | null = null;
console.log("chain_method_null:" + (nstr?.toUpperCase() === undefined));

// 5. Optional element access
const arr: number[] | null = [1, 2, 3];
console.log("chain_element:" + (arr?.[1] === 2));

// 6. Nullish coalescing
const val1: number | null = null;
const val2: number | undefined = undefined;
console.log("nullish_null:" + ((val1 ?? 10) === 10));
console.log("nullish_undef:" + ((val2 ?? 20) === 20));

// 7. Nullish coalescing with falsy but non-null
const zero = 0;
const empty = "";
const falsy = false;
console.log("nullish_zero:" + ((zero ?? 99) === 0));
console.log("nullish_empty:" + ((empty ?? "default") === ""));
console.log("nullish_false:" + ((falsy ?? true) === false));

// 8. Nullish assignment
let na: number | null = null;
na ??= 42;
console.log("nullish_assign:" + (na === 42));

// 9. Logical OR assignment
let oa: string | null = null;
oa ||= "fallback";
console.log("or_assign:" + (oa === "fallback"));

// 10. Logical AND assignment
let aa: number | null = 5;
aa &&= 10;
console.log("and_assign:" + (aa === 10));
