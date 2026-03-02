// XTTA Builtins Torture: Error Types and Handling

// 1. Error
try {
  throw new Error("basic");
} catch (e: any) {
  console.log("error_basic:" + (e.message === "basic" && e instanceof Error));
}

// 2. TypeError
try {
  throw new TypeError("type issue");
} catch (e: any) {
  console.log("type_error:" + (e.message === "type issue" && e instanceof TypeError));
}

// 3. RangeError
try {
  throw new RangeError("out of range");
} catch (e: any) {
  console.log("range_error:" + (e.message === "out of range" && e instanceof RangeError));
}

// 4. Error inheritance
class CustomError extends Error {
  code: string;
  constructor(message: string, code: string) {
    super(message);
    this.code = code;
  }
}
try {
  throw new CustomError("custom", "E001");
} catch (e: any) {
  console.log("custom_error:" + (e.code === "E001" && e instanceof CustomError && e instanceof Error));
}

// 5. Error.name
const err = new Error("test");
console.log("error_name:" + (err.name === "Error"));

// 6. Multi-level error inheritance
class HttpError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
class NotFoundError extends HttpError {
  constructor(resource: string) {
    super(404, resource + " not found");
  }
}
try {
  throw new NotFoundError("user");
} catch (e: any) {
  console.log("deep_error:" + (e.status === 404 && e instanceof NotFoundError && e instanceof HttpError && e instanceof Error));
}

// 7. Error stack exists
const stackErr = new Error("stack test");
console.log("error_stack:" + (typeof stackErr.stack === "string"));
