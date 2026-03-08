// Source inspiration: microsoft/TypeScript conformance template/computed-key suites.
const prefix = "item";
const id = 7;
const key = `${prefix}_${id}`;

const obj: Record<string, number> = {
  [key]: id * 2,
  [prefix + "_fallback"]: 1
};

console.log(`${key}:${obj[key]}`);
