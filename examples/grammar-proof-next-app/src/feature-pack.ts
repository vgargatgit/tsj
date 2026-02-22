import {
  divider,
  defaultTitle
} from "./helpers.ts";
import type {
  Person
} from "./types.ts";
import {
  base
} from "./types.ts";

function renderSpreadLine() {
  function sum4(a, b, c, d) {
    return a + b + c + d;
  }

  const base = [2, 3];
  const combined = [1, ...base, 4];
  const [w, x, y, z] = combined;

  const left = { a: 1 };
  const right = { b: 2, c: 3 };
  const merged = { ...left, ...right, d: 4 };
  const { a, b, c, d } = merged;

  return `${w}|${x}|${y}|${z}|${sum4(...combined)}|${a}|${b}|${c}|${d}`;
}

function renderParamsLine() {
  function summary(a = 10, b = a + 1, ...rest) {
    const [first, second] = rest;
    return `${a}|${b}|${rest.length}|${first ?? "none"}|${second ?? "none"}`;
  }

  const combine = (prefix = "P", ...parts) => {
    const [head] = parts;
    return `${prefix}-${head ?? "none"}-${parts.length}`;
  };

  class Runner {
    run(seed = 1, ...tail) {
      const [first] = tail;
      return seed + (first ?? 0);
    }
  }

  const runner = new Runner();
  return `${summary()}|${summary(3, undefined, 9)}|${combine(undefined, "x", "y")}|${runner.run(undefined, 4)}|${runner.run(2)}`;
}

function renderLoopLine() {
  const values = [1, 2, 3];
  let sum = 0;
  for (const value of values) {
    if (value === 2) {
      continue;
    }
    sum += value;
  }

  const obj = { a: 1, b: 2 };
  let keys = "";
  for (const key in obj) {
    keys = keys + key;
  }

  let last = "";
  for (last in obj) {
  }

  let pairTotal = 0;
  for (const [left, right] of [[1, 2], [3, 4]]) {
    pairTotal += left + right;
  }

  return `${sum}|${keys}|${last}|${pairTotal}`;
}

function renderClassLine() {
  const keyPart = "sum";

  class Counter {
    #seed = 2;
    value = this.#seed;

    [keyPart + "With"](delta) {
      return this.#seed + delta;
    }

    static total = 1;

    static {
      Counter.total = Counter.total + 2;
    }

    static ["bump"](step = 1) {
      Counter.total = Counter.total + step;
      return Counter.total;
    }
  }

  const counter = new Counter();
  return `${counter.value}|${counter.sumWith(3)}|${Counter.total}|${Counter.bump()}`;
}

function renderTypesLine() {
  const checked = { name: "ok" } satisfies { name: string };
  const person: Person = checked;
  const total = (<number>base) + (base as number);
  return `${total}|${person.name}`;
}

export function runFeaturePack() {
  console.log(divider(defaultTitle));
  console.log(`spread:${renderSpreadLine()}`);
  console.log(`params:${renderParamsLine()}`);
  console.log(`loops:${renderLoopLine()}`);
  console.log(`class:${renderClassLine()}`);
  console.log(`ts-only:${renderTypesLine()}`);
}
