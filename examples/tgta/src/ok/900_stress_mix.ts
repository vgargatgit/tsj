type Key = `k_${number}`;
type Value<T> = T extends { value: infer V extends string } ? V : never;
type Project<T> = { [K in keyof T as `p_${string & K}`]: T[K] } & { kind: "projected" };

function mixDec(): ClassDecorator {
  return () => {
    // noop
  };
}

@mixDec()
class StressBox<T extends { id: string; value: string }> {
  static registry: Record<string, number> = {};
  #token = 0;

  constructor(public item: T) {}

  static {
    this.registry["boot"] = 1;
  }

  get id(): string {
    return this.item.id;
  }

  set id(next: string) {
    this.item = { ...this.item, id: next };
  }

  map<U>(fn: (value: T) => U): U;
  map(fn: (value: T) => unknown): unknown;
  map(fn: (value: T) => unknown): unknown {
    this.#token += 1;
    return fn(this.item);
  }
}

namespace StressSpace {
  export interface Payload {
    id: string;
    value: string;
  }

  export const defaultPayload: Payload = { id: "a", value: "b" };
}

declare module "stress-pkg" {
  export interface Plugged {
    active: boolean;
  }
}

const instance = new StressBox(StressSpace.defaultPayload);
const output = instance.map((value) => value.value satisfies string);
const view: Project<{ id: string; value: string }> = {
  p_id: "x",
  p_value: "y",
  kind: "projected",
};
const key: Key = "k_1";

type Extracted = Value<typeof StressSpace.defaultPayload>;

type _ExtractedUse = Extracted;

void [instance, output, view, key];
void ("x" as _ExtractedUse);
