class Box<T = string> {
  constructor(public value: T) {}
}

function pair<T extends string, U = string>(left: T, right: U): [T, U] {
  return [left, right];
}

function constIdentity<const T>(value: T): T {
  return value;
}

function id<T>(value: T): T {
  return value;
}

const instantiate = id<string>;

type ExtractLeft<T> = T extends [infer L, ...infer _R] ? L : never;

type Fn<T> = (input: T) => T;

type ExtractedLeft = ExtractLeft<["a", "b"]>;
const boxed = new Box("v");
const pairValue = pair("a", "b");
const constValue = constIdentity({ n: 1 });

void [instantiate, boxed, pairValue, constValue];
void (0 as unknown as Fn<number>);
void ("x" as ExtractedLeft);
