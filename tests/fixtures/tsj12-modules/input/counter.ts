console.log("counter-init");
export let count = 0;
export function inc() {
  count = count + 1;
}
export function read() {
  return count;
}
