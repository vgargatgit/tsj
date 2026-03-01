// UTTA Grammar 009: Complex Optional Chaining
// Tests deep and mixed optional chaining patterns

// 1. Deep nested optional chaining
const data: any = { a: { b: { c: { d: 42 } } } };
console.log("deep:" + (data?.a?.b?.c?.d === 42));

// 2. Optional chaining on null intermediate
const partial: any = { a: { b: null } };
console.log("null_mid:" + (partial?.a?.b?.c?.d === undefined));

// 3. Optional method call
const obj = {
  greet(name: string) { return "hi " + name; }
};
const maybeObj: any = obj;
console.log("method:" + (maybeObj?.greet("world") === "hi world"));
const noObj: any = null;
console.log("method_null:" + (noObj?.greet === undefined));

// 4. Optional chaining with nullish coalescing
const config: any = { theme: null };
const theme = config?.theme ?? "default";
console.log("nullish:" + (theme === "default"));

// 5. Optional chaining in assignment context
function getVal(k: string): any { return k === "a" ? 1 : undefined; }
const val = getVal("a") ?? 0;
console.log("assign:" + (val === 1));

// 6. Chained optional on function result
function maybe(): any { return { x: 10 }; }
function nothing(): any { return null; }
console.log("fn_chain:" + (maybe()?.x === 10));
console.log("fn_null:" + (nothing()?.x === undefined));

// 7. Optional element access (?.[]) — tests parser support
const arr = [10, 20, 30];
const maybeArr: any = arr;
console.log("elem:" + (maybeArr?.[1] === 20));
const noArr: any = null;
console.log("elem_null:" + (noArr?.[1] === undefined));

// 8. delete with optional chaining
const mutable: any = { a: { b: 1 } };
delete mutable?.a?.b;
console.log("delete:" + (mutable.a.b === undefined));
