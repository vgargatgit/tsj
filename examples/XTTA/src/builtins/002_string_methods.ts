// XTTA Builtins Torture: String Methods

// 1. String.split
console.log("str_split:" + ("a,b,c".split(",").length === 3));

// 2. String.replace
console.log("str_replace:" + ("hello world".replace("world", "ts") === "hello ts"));

// 3. String.trim
console.log("str_trim:" + ("  hi  ".trim() === "hi"));

// 4. String.trimStart/trimEnd
console.log("str_trimStart:" + ("  hi".trimStart() === "hi"));
console.log("str_trimEnd:" + ("hi  ".trimEnd() === "hi"));

// 5. String.toUpperCase / toLowerCase
console.log("str_upper:" + ("hello".toUpperCase() === "HELLO"));
console.log("str_lower:" + ("HELLO".toLowerCase() === "hello"));

// 6. String.startsWith / endsWith
console.log("str_startsWith:" + ("hello".startsWith("he") === true));
console.log("str_endsWith:" + ("hello".endsWith("lo") === true));

// 7. String.includes
console.log("str_includes:" + ("hello world".includes("world") === true));
console.log("str_includes_false:" + ("hello".includes("xyz") === false));

// 8. String.repeat
console.log("str_repeat:" + ("ab".repeat(3) === "ababab"));

// 9. String.padStart / padEnd
console.log("str_padStart:" + ("5".padStart(3, "0") === "005"));
console.log("str_padEnd:" + ("5".padEnd(3, "0") === "500"));

// 10. String.charAt
console.log("str_charAt:" + ("abc".charAt(1) === "b"));

// 11. String.charCodeAt
console.log("str_charCodeAt:" + ("A".charCodeAt(0) === 65));

// 12. String.indexOf / lastIndexOf
console.log("str_indexOf:" + ("abcabc".indexOf("bc") === 1));
console.log("str_lastIndexOf:" + ("abcabc".lastIndexOf("bc") === 4));

// 13. String.slice
console.log("str_slice:" + ("hello".slice(1, 3) === "el"));

// 14. String.substring
console.log("str_substring:" + ("hello".substring(1, 3) === "el"));

// 15. String.concat
console.log("str_concat:" + ("hello".concat(" ", "world") === "hello world"));

// 16. String.length
console.log("str_length:" + ("hello".length === 5));

// 17. String.replaceAll
console.log("str_replaceAll:" + ("aaa".replaceAll("a", "b") === "bbb"));

// 18. String.at
console.log("str_at:" + ("hello".at(0) === "h"));
console.log("str_at_neg:" + ("hello".at(-1) === "o"));

// 19. String bracket access
console.log("str_bracket:" + ("abc"[1] === "b"));

// 20. Template with string methods
const name = "world";
const result = `hello ${name.toUpperCase()}!`;
console.log("str_tmpl_method:" + (result === "hello WORLD!"));
