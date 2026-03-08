// EXPECT_CODE: TSJ-STRICT-UNSUPPORTED
// EXPECT_FEATURE_ID: TSJ-STRICT-PROTOTYPE-MUTATION

const value = {};
Object.setPrototypeOf(value, null);
console.log(value);
