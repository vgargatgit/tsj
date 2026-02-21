export let moduleReady = "boot";
moduleReady = await Promise.resolve("ready");
console.log("module:init=" + moduleReady);

export const plusOne = async (value: number) => {
  const next = await Promise.resolve(value + 1);
  return next;
};

export const asyncOps = {
  async boost(value: number) {
    return await plusOne(value + 1);
  }
};

export async function computeSeries(seed: number) {
  let i = 0;
  let total = seed;
  while (i < 2) {
    if (i == 0) {
      total = total + await Promise.resolve(3);
    } else {
      total = total + await Promise.resolve(4);
    }
    i = i + 1;
  }
  return total;
}
