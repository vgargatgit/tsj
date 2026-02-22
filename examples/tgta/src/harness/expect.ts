export function fail(message: string): never {
  throw new Error(message);
}

export function assertCondition(condition: unknown, message: string): asserts condition {
  if (!condition) {
    fail(message);
  }
}

export function assertEqual<T>(actual: T, expected: T, message: string): void {
  if (actual !== expected) {
    fail(`${message}: expected ${String(expected)}, received ${String(actual)}`);
  }
}
