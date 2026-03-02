// XTTA Grammar Torture: Template Literals
// Tests tagged templates, nested templates, multiline

// 1. Basic template literal
const name = "world";
console.log("basic_template:" + (`hello ${name}` === "hello world"));

// 2. Expression in template
const a = 5, b = 3;
console.log("expr_template:" + (`${a + b}` === "8"));

// 3. Nested template
const inner = `${1 + 1}`;
const outer = `result: ${inner}`;
console.log("nested_template:" + (outer === "result: 2"));

// 4. Template with method call
const msg = `${"hello".toUpperCase()} WORLD`;
console.log("method_template:" + (msg === "HELLO WORLD"));

// 5. Multiline template
const multi = `line1
line2
line3`;
const lines = multi.split("\n");
console.log("multiline:" + (lines.length === 3));

// 6. Tagged template
function tag(strings: TemplateStringsArray, ...values: any[]): string {
  let result = "";
  for (let i = 0; i < strings.length; i++) {
    result += strings[i];
    if (i < values.length) {
      result += String(values[i]).toUpperCase();
    }
  }
  return result;
}
const tagged = tag`hello ${"world"} and ${"test"}`;
console.log("tagged_template:" + (tagged === "hello WORLD and TEST"));

// 7. Template with ternary
const val = true;
const ternTmpl = `status: ${val ? "yes" : "no"}`;
console.log("ternary_template:" + (ternTmpl === "status: yes"));

// 8. Escaped characters in template
const escaped = `tab:\there`;
console.log("escaped_template:" + (escaped.includes("\t")));
