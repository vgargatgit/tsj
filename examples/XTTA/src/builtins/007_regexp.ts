// XTTA Builtins Torture: RegExp

// 1. Basic regex test
const re = /hello/;
console.log("regex_test:" + (re.test("hello world") === true));

// 2. Regex test negative
console.log("regex_test_neg:" + (re.test("goodbye") === false));

// 3. Case insensitive
const ci = /hello/i;
console.log("regex_case:" + (ci.test("HELLO") === true));

// 4. String.match
const match = "hello world".match(/(\w+)/);
console.log("str_match:" + (match !== null && match[0] === "hello"));

// 5. String.search
console.log("str_search:" + ("abc123".search(/\d/) === 3));

// 6. String.replace with regex
console.log("str_replace_re:" + ("aabaa".replace(/a/g, "x") === "xxbxx"));

// 7. RegExp constructor
const dynamic = new RegExp("test", "i");
console.log("regex_ctor:" + (dynamic.test("TEST") === true));

// 8. Regex groups
const grp = "2024-01-15".match(/(\d{4})-(\d{2})-(\d{2})/);
console.log("regex_groups:" + (grp !== null && grp[1] === "2024" && grp[2] === "01"));

// 9. Global flag match all
const allMatches: string[] = [];
const gre = /\d+/g;
let m;
while ((m = gre.exec("a1b22c333")) !== null) {
  allMatches.push(m[0]);
}
console.log("regex_global:" + (allMatches.length === 3 && allMatches[2] === "333"));

// 10. Regex special chars
const special = /\./;
console.log("regex_special:" + (special.test("a.b") === true && special.test("ab") === false));
