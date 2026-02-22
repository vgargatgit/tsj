import test from "node:test";
import assert from "node:assert/strict";

import { parseSourceText, serializeAst } from "./parse_harness.ts";

test("parseSourceText succeeds for valid TypeScript", () => {
  const result = parseSourceText("valid.ts", "const value = 1 + 2;\n");

  assert.equal(result.success, true);
  assert.equal(result.diagnostics.length, 0);
  assert.notEqual(result.ast, null);
  assert.ok(serializeAst(result.ast!).includes("SourceFile"));
});

test("parseSourceText fails with stable diagnostic metadata for invalid TypeScript", () => {
  const result = parseSourceText("invalid.ts", "const value = ;\n");

  assert.equal(result.success, false);
  assert.ok(result.diagnostics.length >= 1);
  assert.match(result.diagnostics[0].code, /^TS\d+$/);
  assert.equal(result.diagnostics[0].span.file, "invalid.ts");
});
