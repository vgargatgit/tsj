import defaultThing, { a as alpha, b } from "pkg-a";
import * as ns from "pkg-b";
import type { TypeOnly } from "pkg-types";
import data from "./x.json" with { type: "json" };

export { alpha as renamed, b };
export * from "pkg-c";
export * as nsExport from "pkg-d";
export { q as y } from "pkg-e";
export type { TypeOnly };

void [defaultThing, ns, data];
