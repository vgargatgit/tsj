// UTTA Grammar 008: Error.cause and AggregateError
// Tests modern error features

// 1. Error with cause
try {
  try {
    throw new Error("root cause");
  } catch (inner) {
    throw new Error("wrapper", { cause: inner });
  }
} catch (e: any) {
  console.log("cause_msg:" + (e.message === "wrapper"));
  console.log("cause_inner:" + (e.cause instanceof Error && e.cause.message === "root cause"));
}

// 2. AggregateError
const errors = [new Error("e1"), new Error("e2"), new Error("e3")];
const agg = new AggregateError(errors, "Multiple failures");
console.log("agg_msg:" + (agg.message === "Multiple failures"));
console.log("agg_count:" + (agg.errors.length === 3));
console.log("agg_first:" + (agg.errors[0].message === "e1"));

// 3. Error subclass with cause chain
class AppError extends Error {
  constructor(message: string, options?: { cause?: Error }) {
    super(message, options);
    this.name = "AppError";
  }
}
try {
  throw new AppError("app failure", { cause: new TypeError("type issue") });
} catch (e: any) {
  console.log("custom_cause:" + (e.cause instanceof TypeError));
  console.log("custom_name:" + (e.name === "AppError"));
}

// 4. Nested cause chain (3 levels)
try {
  try {
    try {
      throw new Error("level3");
    } catch (e3: any) {
      throw new Error("level2", { cause: e3 });
    }
  } catch (e2: any) {
    throw new Error("level1", { cause: e2 });
  }
} catch (e1: any) {
  console.log("chain_depth:" + (e1.cause.cause.message === "level3"));
}
