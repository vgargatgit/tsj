function step(v: number) {
  console.log("step=" + v);
  return v + 1;
}
function done(v: number) {
  console.log("done=" + v);
  return v;
}

Promise.resolve(1).then(step).then(done);
console.log("sync");
