export async function runPromiseLab(base: number) {
  const recovered = await Promise.reject("boom")
    .catch((reason: string) => {
      console.log("promise:catch=" + reason);
      return base;
    })
    .finally(() => {
      console.log("promise:finally");
      return 99;
    });

  const allValues = await Promise.all([
    Promise.resolve(recovered),
    Promise.resolve(recovered + 1),
    recovered + 2
  ]);
  const raceValue = await Promise.race([Promise.resolve("win"), Promise.reject("lose")]);
  const settledValues = await Promise.allSettled([Promise.resolve(1), Promise.reject("x")]);
  const anyValue = await Promise.any([Promise.reject("bad"), Promise.resolve("ok")]);

  return "lab=" + allValues.length + ":" + raceValue + ":" + settledValues.length + ":" + anyValue;
}
