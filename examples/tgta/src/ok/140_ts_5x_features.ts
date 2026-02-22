import jsonData from "./config.json" with { type: "json" };

const settings = { mode: "on" } satisfies { mode: string };

function identity<const T>(value: T): T {
  return value;
}

type SegmentParts<T extends string> = T extends `${infer Head extends string}-${infer Tail}`
  ? [Head, Tail]
  : never;

using syncResource = {
  [Symbol.dispose](): void {
    // noop
  },
};

async function consume(factory: () => Promise<{ [Symbol.asyncDispose](): Promise<void> }>) {
  await using asyncResource = await factory();
  return identity(asyncResource);
}

type SegmentResult = SegmentParts<"left-right">;

void [settings, syncResource, consume, jsonData];
void (0 as unknown as SegmentResult);
