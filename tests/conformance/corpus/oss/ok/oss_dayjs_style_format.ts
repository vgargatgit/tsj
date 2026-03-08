// Source inspiration: lightweight date formatting style (dayjs-like APIs).
function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`;
}

function fmt(y: number, m: number, d: number): string {
  return `${y}-${pad2(m)}-${pad2(d)}`;
}

console.log(fmt(2026, 3, 6));
