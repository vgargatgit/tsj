// Source inspiration: RxJS pipe/map/filter composition style.
function map<T, R>(items: T[], fn: (v: T) => R): R[] {
  const out: R[] = [];
  for (const v of items) {
    out.push(fn(v));
  }
  return out;
}

function filter<T>(items: T[], fn: (v: T) => boolean): T[] {
  const out: T[] = [];
  for (const v of items) {
    if (fn(v)) {
      out.push(v);
    }
  }
  return out;
}

const values = filter(map([1, 2, 3, 4], (n) => n * 2), (n) => n > 4);
console.log(values.length);
