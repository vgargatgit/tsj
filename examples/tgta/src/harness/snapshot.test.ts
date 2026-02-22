import test from "node:test";
import assert from "node:assert/strict";

import { normalizeLineEndings, stableStringify } from "./snapshot.ts";

test("stableStringify sorts object keys recursively", () => {
  const value = {
    z: 1,
    nested: {
      b: true,
      a: "x",
    },
    a: 2,
  };

  const rendered = stableStringify(value);
  assert.equal(
    rendered,
    '{\n  "a": 2,\n  "nested": {\n    "a": "x",\n    "b": true\n  },\n  "z": 1\n}'
  );
});

test("stableStringify preserves array order", () => {
  const rendered = stableStringify([
    { b: 2, a: 1 },
    { d: 4, c: 3 },
  ]);

  assert.equal(
    rendered,
    '[\n  {\n    "a": 1,\n    "b": 2\n  },\n  {\n    "c": 3,\n    "d": 4\n  }\n]'
  );
});

test("normalizeLineEndings converts CRLF to LF", () => {
  assert.equal(normalizeLineEndings("a\r\nb\r\nc\n"), "a\nb\nc\n");
});
