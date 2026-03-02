// XTTA Builtins Torture: JSON

// 1. JSON.stringify simple
console.log("json_str_simple:" + (JSON.stringify(42) === "42"));

// 2. JSON.stringify string
console.log("json_str_string:" + (JSON.stringify("hello") === "\"hello\""));

// 3. JSON.stringify object
const obj = { a: 1, b: "two" };
const json = JSON.stringify(obj);
console.log("json_str_obj:" + (json.includes("\"a\":1") || json.includes("\"a\": 1")));

// 4. JSON.stringify array
console.log("json_str_arr:" + (JSON.stringify([1, 2, 3]) === "[1,2,3]"));

// 5. JSON.stringify null/boolean
console.log("json_str_null:" + (JSON.stringify(null) === "null"));
console.log("json_str_bool:" + (JSON.stringify(true) === "true"));

// 6. JSON.parse number
console.log("json_parse_num:" + (JSON.parse("42") === 42));

// 7. JSON.parse string
console.log("json_parse_str:" + (JSON.parse("\"hello\"") === "hello"));

// 8. JSON.parse object
const parsed = JSON.parse("{\"a\":1,\"b\":2}");
console.log("json_parse_obj:" + (parsed.a === 1 && parsed.b === 2));

// 9. JSON.parse array
const parsedArr = JSON.parse("[1,2,3]");
console.log("json_parse_arr:" + (parsedArr.length === 3 && parsedArr[0] === 1));

// 10. JSON roundtrip
const original = { name: "test", values: [1, 2, 3], nested: { flag: true } };
const roundtrip = JSON.parse(JSON.stringify(original));
console.log("json_roundtrip:" + (roundtrip.name === "test" && roundtrip.values[2] === 3 && roundtrip.nested.flag === true));

// 11. JSON.stringify with nested object
const deep = { a: { b: { c: 1 } } };
console.log("json_deep:" + (JSON.stringify(deep).includes("\"c\":1") || JSON.stringify(deep).includes("\"c\": 1")));

// 12. JSON.stringify with replacer (function)
const withReplacer = JSON.stringify({ a: 1, b: 2, c: 3 }, (key, value) => {
  if (key === "b") return undefined;
  return value;
});
console.log("json_replacer:" + (!withReplacer.includes("\"b\"")));
