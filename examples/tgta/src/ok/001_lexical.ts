const cafe\u0301 = 1;
const greek_\u03A9 = 2;

const million = 1_000_000;
const mask = 0b1010_1010;
const hex = 0xCAFE_F00D;
const big = 123n;

const single = 'single';
const dbl = "double";
const tpl = `value=${million}`;

function tag(parts: TemplateStringsArray, ...values: unknown[]) {
  return { parts, values };
}

const tagged = tag`a${single}b${dbl}`;

// line comment
/* block comment */
/** jsdoc comment */
const regexVsDivision = /ab+c/i.test("abbbc") ? 10 / 2 : 0;

function asiReturnTrap(): number | undefined {
  return
  42;
}

let counter = 0;
let other = 1;
counter
++other;
counter--;

function* lexicalGenerator(): Generator<number, void, unknown> {
  yield
  1;
}

async function lexicalAsync(): Promise<number> {
  return await
    Promise.resolve(counter + (big as unknown as number));
}

void [cafe\u0301, greek_\u03A9, million, mask, hex, big, tpl, tagged, regexVsDivision, asiReturnTrap, other];
void lexicalGenerator;
void lexicalAsync;
