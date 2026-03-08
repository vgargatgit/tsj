// Source inspiration: microsoft/TypeScript conformance generic/union narrowing suites.
function pick<T>(value: T, fallback: T): T {
  return value ?? fallback;
}

function sizeOf(value: string | number | { length: number }): number {
  if (typeof value === "number") {
    return value;
  }
  return value.length;
}

const out = pick<number | null>(null, 12);
console.log(sizeOf("abc") + sizeOf({ length: 5 }) + (out ?? 0));
