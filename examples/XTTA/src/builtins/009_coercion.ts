// XTTA Builtins Torture: Type Coercion and Conversion Edge Cases

// 1. String to number
console.log("str_to_num:" + (Number("42") === 42));
console.log("str_to_num_float:" + (Number("3.14") === 3.14));
console.log("str_to_num_empty:" + (Number("") === 0));
console.log("str_to_num_nan:" + (Number.isNaN(Number("abc"))));

// 2. Number to string
console.log("num_to_str:" + (String(42) === "42"));
console.log("num_to_str_float:" + (String(3.14) === "3.14"));

// 3. Boolean coercion
console.log("bool_0:" + (!0 === true));
console.log("bool_empty:" + (!"" === true));
console.log("bool_null:" + (!null === true));
console.log("bool_undef:" + (!undefined === true));
console.log("bool_nan:" + (!NaN === true));

// 4. String concatenation coercion
console.log("concat_num:" + ("" + 42 === "42"));
console.log("concat_bool:" + ("" + true === "true"));
console.log("concat_null:" + ("" + null === "null"));

// 5. Comparison edge cases
console.log("null_eq_undef:" + (null == undefined));
console.log("null_neq_undef:" + (null !== undefined));
console.log("nan_neq_nan:" + (NaN !== NaN));

// 6. typeof
console.log("typeof_str:" + (typeof "hello" === "string"));
console.log("typeof_num:" + (typeof 42 === "number"));
console.log("typeof_bool:" + (typeof true === "boolean"));
console.log("typeof_undef:" + (typeof undefined === "undefined"));
console.log("typeof_null:" + (typeof null === "object"));
console.log("typeof_fn:" + (typeof (() => {}) === "function"));
console.log("typeof_obj:" + (typeof {} === "object"));
console.log("typeof_arr:" + (typeof [] === "object"));

// 7. Unary plus
console.log("unary_plus:" + (+true === 1));
console.log("unary_plus_str:" + (+"42" === 42));

// 8. Double NOT for boolean conversion
console.log("double_not:" + (!!1 === true && !!0 === false && !!"" === false && !!"a" === true));

// 9. Infinity
console.log("infinity:" + (1 / 0 === Infinity));
console.log("neg_infinity:" + (-1 / 0 === -Infinity));
console.log("infinity_type:" + (typeof Infinity === "number"));
