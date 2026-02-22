var [vx, vy]: [number, number] = [1, 2];
let { lx, ly }: { lx: number; ly: number } = { lx: 3, ly: 4 };
const cz: number = 5;

function overloaded(value: string): string;
function overloaded(value: number): number;
function overloaded(value: string | number): string | number {
  return value;
}

function withDefaults(this: { factor: number }, value = 2, ...rest: number[]): number {
  const total = rest.reduce((sum, item) => sum + item, value);
  return total * this.factor;
}

const identity = <T>(x: T) => x;

declare function declaredFn(value: string): number;
declare const declaredConst: { readonly id: string };
declare class DeclaredClass {
  value: number;
}

import legacy = require("legacy-lib");
const declaredShape = { id: "ok" } satisfies { id: string };

export = legacy;

void [
  vx,
  vy,
  lx,
  ly,
  cz,
  overloaded,
  withDefaults,
  identity,
  declaredFn,
  declaredConst,
  DeclaredClass,
  declaredShape,
];
