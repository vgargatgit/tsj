import type { ExternalType } from "pkg-types";

type PrimitivePack =
  | string
  | number
  | boolean
  | bigint
  | symbol
  | null
  | undefined
  | any
  | unknown
  | never
  | void;

type LiteralPack = "x" | 42 | true;

interface Shape {
  readonly id: string;
  value?: number;
}

type Pair<T> = [head: T, tail?: T];
type ReadA<T> = readonly T[];
type ReadB<T> = ReadonlyArray<T>;
type Indexed<T, K extends keyof T> = T[K];
type Keyed<T> = keyof T;

declare const shapeValue: Shape;
type ValueType = Indexed<Shape, "value">;
type ShapeType = typeof shapeValue;
type UnwrapPromise<T> = T extends Promise<infer U> ? U : T;
type Remap<T> = { [K in keyof T as `mapped_${string & K}`]?: T[K] };
type TemplateLiteral<T extends string> = `prefix_${T}`;

const cfg = { id: "ok", value: 1 } satisfies Shape;

function assertIsString(value: unknown): asserts value is string {
  if (typeof value !== "string") {
    throw new Error("value must be string");
  }
}

export type Alias =
  | ExternalType
  | PrimitivePack
  | LiteralPack
  | Pair<number>
  | ReadA<number>
  | ReadB<number>
  | Keyed<Shape>
  | ValueType
  | ShapeType
  | UnwrapPromise<Promise<number>>
  | Remap<Shape>
  | TemplateLiteral<"id">;

export type { ExternalType };

void cfg;
void assertIsString;
