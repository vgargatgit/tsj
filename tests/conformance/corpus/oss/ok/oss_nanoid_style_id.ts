// Source inspiration: nanoid ID composition style (https://github.com/ai/nanoid).
const alphabet = "abcdef012345";

function id(size = 8): string {
  let out = "";
  for (let i = 0; i < size; i++) {
    const idx = (i * 7 + 3) % alphabet.length;
    out += alphabet[idx];
  }
  return out;
}

console.log(id(6));
