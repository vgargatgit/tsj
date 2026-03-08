import { baseValue, twice } from "./_support.ts";

function add(values: number[]) {
  return values[0] + values[1];
}

const result = add([baseValue, twice(1)]);
console.log("result=" + result);
