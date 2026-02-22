declare const a: any;
declare const b: any;
declare const c: any;
declare const d: any;
declare const e: any;
declare const f: any;
declare const g: any;
declare const x: { y: number } | null;

const precedence = (a + b * c ** d) ?? (e || (f && g));
const chain = a?.b?.(c) ?? d;
const nonNull = x!.y;
const asCast = (x as { y: number }).y;
const angleCast = (<{ y: number }>x).y;

function* expressionGenerator(source: Iterable<number>) {
  yield 1;
  yield* source;
}

async function expressionAsync() {
  const awaited = await Promise.resolve(1);
  return awaited;
}

function captureNewTarget() {
  return new.target;
}

const importMetaUrl = import.meta.url;
const dynamicImportExpr = import("pkg-dynamic");
const inAndInstance = ("k" in a) && (a instanceof Object);

let left = 0;
({ left } = { left: 3 });
[left] = [4];

const satExpr = { id: 1 } satisfies { id: number };

using disposable = {
  [Symbol.dispose](): void {
    // noop
  },
};

async function usingAsync(factory: () => Promise<{ [Symbol.asyncDispose](): Promise<void> }>) {
  await using asyncDisposable = await factory();
  return asyncDisposable;
}

void [
  precedence,
  chain,
  nonNull,
  asCast,
  angleCast,
  importMetaUrl,
  dynamicImportExpr,
  inAndInstance,
  left,
  satExpr,
  disposable,
];
void expressionGenerator;
void expressionAsync;
void captureNewTarget;
void usingAsync;
