// Source inspiration: lodash-style safe getter patterns (https://github.com/lodash/lodash).
function getPath(obj: any, path: string, fallback: unknown) {
  const parts = path.split(".");
  let cur: any = obj;
  for (const p of parts) {
    cur = cur?.[p];
    if (cur === undefined) {
      return fallback;
    }
  }
  return cur;
}

console.log(getPath({ a: { b: 3 } }, "a.b", 0));
