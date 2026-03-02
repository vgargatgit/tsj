// XTTA Grammar Torture: Exception Handling Edge Cases

// 1. Try-catch-finally
let order = "";
try {
  order += "try;";
  throw new Error("test");
} catch (e) {
  order += "catch;";
} finally {
  order += "finally;";
}
console.log("try_catch_finally:" + (order === "try;catch;finally;"));

// 2. Nested try-catch
let inner_caught = false;
try {
  try {
    throw new Error("inner");
  } catch (e: any) {
    inner_caught = e.message === "inner";
    throw new Error("rethrown");
  }
} catch (e: any) {
  console.log("nested_try:" + (inner_caught && e.message === "rethrown"));
}

// 3. Finally with return
function withFinally(): string {
  try {
    return "try";
  } finally {
    // finally runs but doesn't override return in TS
  }
}
console.log("finally_return:" + (withFinally() === "try"));

// 4. Custom error class
class AppError extends Error {
  code: number;
  constructor(message: string, code: number) {
    super(message);
    this.code = code;
  }
}
try {
  throw new AppError("not found", 404);
} catch (e: any) {
  console.log("custom_error:" + (e.code === 404 && e.message === "not found"));
}

// 5. Error types
try { (null as any).x; } catch (e) {
  console.log("type_error:" + (e instanceof Error));
}

// 6. Try without catch (only finally)
let finallyRan = false;
try {
  // no error
} finally {
  finallyRan = true;
}
console.log("try_finally:" + finallyRan);

// 7. Re-throw conditional
function conditionalRethrow(shouldCatch: boolean): string {
  try {
    throw new Error("test");
  } catch (e) {
    if (shouldCatch) return "caught";
    throw e;
  }
}
console.log("cond_rethrow:" + (conditionalRethrow(true) === "caught"));
let rethrown = false;
try { conditionalRethrow(false); } catch { rethrown = true; }
console.log("rethrow:" + rethrown);
