let i = 0;
while (await Promise.resolve(i < 1)) {
  i = i + 1;
}
console.log(i);
