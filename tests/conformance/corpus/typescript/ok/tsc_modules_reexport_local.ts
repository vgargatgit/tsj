// Source inspiration: microsoft/TypeScript module syntax conformance suites.
// Kept standalone/compile-only by avoiding external module resolution requirements.
const local = { name: "tsc" };
const key = "name" as const;
console.log(local[key]);
